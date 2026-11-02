import "server-only";
import { readFileSync } from "node:fs";
import { CLIENT_FILE, TOKEN_FILE } from "./google-files";

/**
 * The server's whole conversation with Gmail, over plain REST + fetch.
 *
 * The phone never holds a Google credential: it holds one bearer token for THIS
 * server, and this server holds the refresh token. Losing the phone therefore
 * costs a token rotation here, not a Google password change.
 */

const API = "https://gmail.googleapis.com/gmail/v1/users/me";

export class GmailError extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

type Client = { client_id: string; client_secret: string; token_uri: string };

function readJson(path: string, what: string): Record<string, unknown> {
  try {
    return JSON.parse(readFileSync(path, "utf8"));
  } catch (e) {
    throw new GmailError(
      `${what} not readable at ${path} (${e instanceof Error ? e.message : e}) — ` +
        "see README: 'Connecting Gmail'",
      503,
    );
  }
}

function client(): Client {
  const raw = readJson(CLIENT_FILE, "Google OAuth client");
  const c = (raw.installed ?? raw.web) as Client | undefined;
  if (!c?.client_id || !c.client_secret)
    throw new GmailError(`${CLIENT_FILE} has no installed/web client`, 503);
  return { ...c, token_uri: c.token_uri ?? "https://oauth2.googleapis.com/token" };
}

let cached: { token: string; expires: number } | null = null;

async function accessToken(): Promise<string> {
  if (cached && cached.expires > Date.now() + 60_000) return cached.token;
  const { refresh_token } = readJson(TOKEN_FILE, "Gmail sign-in") as { refresh_token?: string };
  if (!refresh_token) throw new GmailError(`${TOKEN_FILE} has no refresh_token`, 503);
  const c = client();
  const res = await fetch(c.token_uri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      client_id: c.client_id,
      client_secret: c.client_secret,
      refresh_token,
      grant_type: "refresh_token",
    }),
  });
  const body = (await res.json()) as { access_token?: string; expires_in?: number; error?: string; error_description?: string };
  if (!res.ok || !body.access_token)
    throw new GmailError(
      `Google refused the refresh token: ${body.error ?? res.status} ${body.error_description ?? ""}`.trim() +
        " — run scripts/connect-google.mjs again",
      503,
    );
  cached = { token: body.access_token, expires: Date.now() + (body.expires_in ?? 3600) * 1000 };
  return cached.token;
}

async function gmail<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    ...init,
    headers: {
      authorization: `Bearer ${await accessToken()}`,
      ...(init?.body ? { "content-type": "application/json" } : {}),
      ...init?.headers,
    },
  });
  const text = await res.text();
  if (!res.ok) {
    let msg = text;
    try {
      msg = JSON.parse(text).error?.message ?? text;
    } catch {}
    throw new GmailError(`Gmail ${res.status}: ${msg}`, res.status === 404 ? 404 : 502);
  }
  return (text ? JSON.parse(text) : {}) as T;
}

// ---------------------------------------------------------------- Gmail shapes

type Header = { name: string; value: string };
type Part = {
  mimeType: string;
  filename?: string;
  headers?: Header[];
  body?: { data?: string; size?: number; attachmentId?: string };
  parts?: Part[];
};
type GMessage = {
  id: string;
  threadId: string;
  labelIds?: string[];
  snippet?: string;
  internalDate?: string;
  payload?: Part;
};
type GThread = { id: string; messages?: GMessage[] };

function header(m: GMessage, name: string): string {
  const n = name.toLowerCase();
  return m.payload?.headers?.find((h) => h.name.toLowerCase() === n)?.value ?? "";
}

/** `"Dana Levi" <dana@x.com>` → { name: "Dana Levi", email: "dana@x.com" } */
export function parseAddress(raw: string): { name: string; email: string } {
  const m = raw.match(/^\s*"?([^"<]*?)"?\s*<([^>]+)>\s*$/);
  if (m) return { name: m[1].trim() || m[2], email: m[2].trim() };
  return { name: raw.trim(), email: raw.trim() };
}

let me: string | null = null;
export async function myAddress(): Promise<string> {
  if (!me) me = (await gmail<{ emailAddress: string }>("/profile")).emailAddress;
  return me;
}

// ---------------------------------------------------------------- inbox

/**
 * Two views, split by Gmail's own categories rather than a guess of ours:
 *  - people: Primary — mail a person wrote to you
 *  - other:  Promotions / Social / Updates / Forums — everything sent in bulk
 * Doing it with a query (not a filter over a page) keeps paging honest: a page
 * of "people" is 25 people-threads, not 25 threads of which three survive.
 */
const QUERIES = {
  people: "in:inbox -category:promotions -category:social -category:updates -category:forums",
  other: "in:inbox (category:promotions OR category:social OR category:updates OR category:forums)",
} as const;
export type View = keyof typeof QUERIES;
export const isView = (v: string): v is View => v in QUERIES;

export type ThreadSummary = {
  id: string;
  from: string;
  fromEmail: string;
  subject: string;
  snippet: string;
  date: number;
  unread: boolean;
  count: number;
};

export async function listInbox(view: View, pageToken?: string) {
  const qs = new URLSearchParams({ q: QUERIES[view], maxResults: "25" });
  if (pageToken) qs.set("pageToken", pageToken);
  const list = await gmail<{ threads?: { id: string }[]; nextPageToken?: string; resultSizeEstimate?: number }>(
    `/threads?${qs}`,
  );
  const meta = "format=metadata&metadataHeaders=From&metadataHeaders=Subject";
  const threads = await Promise.all(
    (list.threads ?? []).map((t) => gmail<GThread>(`/threads/${t.id}?${meta}`)),
  );
  const mine = await myAddress();
  const summaries: ThreadSummary[] = threads.map((t) => {
    const msgs = t.messages ?? [];
    const first = msgs[0];
    const last = msgs[msgs.length - 1];
    // Show who you are waiting on: the latest message NOT from you, if any.
    const lastOther = [...msgs].reverse().find((m) => parseAddress(header(m, "From")).email !== mine) ?? last;
    const from = parseAddress(header(lastOther, "From"));
    return {
      id: t.id,
      from: from.name,
      fromEmail: from.email,
      subject: header(first, "Subject") || "(no subject)",
      snippet: decodeEntities(last.snippet ?? ""),
      date: Number(last.internalDate ?? 0),
      unread: msgs.some((m) => m.labelIds?.includes("UNREAD")),
      count: msgs.length,
    };
  });
  return { threads: summaries, nextPageToken: list.nextPageToken ?? null };
}

/** Unread thread counts per view — what the tabs show. */
export async function unreadCounts() {
  const count = async (view: View) =>
    (
      await gmail<{ threads?: unknown[]; resultSizeEstimate?: number }>(
        `/threads?${new URLSearchParams({ q: `${QUERIES[view]} is:unread`, maxResults: "100" })}`,
      )
    ).threads?.length ?? 0;
  const [people, other] = await Promise.all([count("people"), count("other")]);
  return { people, other };
}

// ---------------------------------------------------------------- one thread

export type MessageView = {
  id: string;
  from: string;
  fromEmail: string;
  to: string;
  date: number;
  unread: boolean;
  body: string;
  /** The quoted history under "On … wrote:", folded away like Gmail does. */
  quoted: string;
  attachments: string[];
};

function b64url(data: string): string {
  return Buffer.from(data.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8");
}

function walk(p: Part | undefined, out: { plain?: string; html?: string; files: string[] }) {
  if (!p) return;
  if (p.filename && p.body?.attachmentId) out.files.push(p.filename);
  else if (p.mimeType === "text/plain" && p.body?.data && out.plain === undefined) out.plain = b64url(p.body.data);
  else if (p.mimeType === "text/html" && p.body?.data && out.html === undefined) out.html = b64url(p.body.data);
  p.parts?.forEach((c) => walk(c, out));
}

function decodeEntities(s: string): string {
  return s
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;|&#x27;/g, "'")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)));
}

/** Good-enough HTML → readable text. The phone shows text, never remote HTML:
 *  no tracking pixels load, nothing reflows, nothing phones home on open. */
export function htmlToText(html: string): string {
  return decodeEntities(
    html
      .replace(/<(style|script|head)[\s\S]*?<\/\1>/gi, "")
      .replace(/<br\s*\/?>/gi, "\n")
      .replace(/<\/(p|div|tr|li|h[1-6]|blockquote)>/gi, "\n")
      .replace(/<li[^>]*>/gi, "• ")
      .replace(/<a [^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi, (_, href, label) => {
        const l = label.replace(/<[^>]+>/g, "").trim();
        return l && l !== href ? `${l} (${href})` : href;
      })
      .replace(/<[^>]+>/g, ""),
  )
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

/** Split a reply from the history it quotes (English and Hebrew clients). */
export function splitQuoted(text: string): { body: string; quoted: string } {
  const lines = text.split("\n");
  const markers = [
    /^On .+wrote:\s*$/,
    /^בתאריך .+כתב(\/ה)?:?\s*$/,
    /^‫?בתאריך .+/,
    /^-{2,}\s*Original Message\s*-{2,}/i,
    /^-{5,}\s*Forwarded message/i,
    /^From: .+/,
  ];
  let cut = lines.findIndex((l) => markers.some((r) => r.test(l.trim())));
  if (cut === -1) {
    // Trailing run of "> " lines with nothing after them.
    let i = lines.length;
    while (i > 0 && (lines[i - 1].startsWith(">") || lines[i - 1].trim() === "")) i--;
    if (i < lines.length && lines.slice(i).some((l) => l.startsWith(">"))) cut = i;
  }
  if (cut <= 0) return { body: text.trim(), quoted: "" };
  return { body: lines.slice(0, cut).join("\n").trim(), quoted: lines.slice(cut).join("\n").trim() };
}

export async function getThread(id: string) {
  const t = await gmail<GThread>(`/threads/${encodeURIComponent(id)}?format=full`);
  const msgs = t.messages ?? [];
  const messages: MessageView[] = msgs.map((m) => {
    const parts: { plain?: string; html?: string; files: string[] } = { files: [] };
    walk(m.payload, parts);
    const text = parts.plain ?? (parts.html ? htmlToText(parts.html) : decodeEntities(m.snippet ?? ""));
    const { body, quoted } = splitQuoted(text.replace(/\r\n/g, "\n"));
    const from = parseAddress(header(m, "From"));
    return {
      id: m.id,
      from: from.name,
      fromEmail: from.email,
      to: header(m, "To"),
      date: Number(m.internalDate ?? 0),
      unread: !!m.labelIds?.includes("UNREAD"),
      body,
      quoted,
      attachments: parts.files,
    };
  });
  return {
    id: t.id,
    subject: (msgs[0] && header(msgs[0], "Subject")) || "(no subject)",
    inInbox: msgs.some((m) => m.labelIds?.includes("INBOX")),
    messages,
  };
}

// ---------------------------------------------------------------- actions

export const ACTIONS = ["archive", "inbox", "read", "unread", "trash", "untrash"] as const;
export type Action = (typeof ACTIONS)[number];
export const isAction = (a: string): a is Action => (ACTIONS as readonly string[]).includes(a);

export async function act(id: string, action: Action) {
  const tid = encodeURIComponent(id);
  if (action === "trash" || action === "untrash") return gmail(`/threads/${tid}/${action}`, { method: "POST" });
  const change: Record<Exclude<Action, "trash" | "untrash">, { addLabelIds?: string[]; removeLabelIds?: string[] }> = {
    archive: { removeLabelIds: ["INBOX"] },
    inbox: { addLabelIds: ["INBOX"] },
    read: { removeLabelIds: ["UNREAD"] },
    unread: { addLabelIds: ["UNREAD"] },
  };
  return gmail(`/threads/${tid}/modify`, { method: "POST", body: JSON.stringify(change[action]) });
}

/** RFC 2047 — a Hebrew subject must be encoded or it arrives as mojibake. */
function encodeHeader(v: string): string {
  // eslint-disable-next-line no-control-regex
  return /^[\x00-\x7f]*$/.test(v) ? v : `=?UTF-8?B?${Buffer.from(v, "utf8").toString("base64")}?=`;
}

/**
 * Reply to the thread's latest message, threaded the way every client expects:
 * same Gmail threadId, In-Reply-To + References, "Re:" subject. Addressed to
 * the bare address only — a display name copied from a decoded header is the
 * classic way to send a broken To: line.
 */
export async function reply(id: string, text: string) {
  const t = await gmail<GThread>(
    `/threads/${encodeURIComponent(id)}?format=metadata` +
      ["From", "To", "Reply-To", "Subject", "Message-ID", "References"].map((h) => `&metadataHeaders=${h}`).join(""),
  );
  const msgs = t.messages ?? [];
  if (!msgs.length) throw new GmailError("thread has no messages", 404);
  const mine = await myAddress();
  const last = msgs[msgs.length - 1];
  const lastFrom = parseAddress(header(last, "From")).email;
  // Replying under your own last message continues to whoever you wrote to.
  const to =
    lastFrom === mine
      ? parseAddress(header(last, "To").split(",")[0] ?? "").email
      : parseAddress(header(last, "Reply-To") || header(last, "From")).email;
  if (!to) throw new GmailError("could not work out who to reply to", 422);

  const subject0 = header(msgs[0], "Subject");
  const subject = /^re:/i.test(subject0) ? subject0 : `Re: ${subject0}`;
  const msgId = header(last, "Message-ID");
  const refs = [header(last, "References"), msgId].filter(Boolean).join(" ");

  const mime = [
    `From: ${mine}`,
    `To: ${to}`,
    `Subject: ${encodeHeader(subject)}`,
    ...(msgId ? [`In-Reply-To: ${msgId}`, `References: ${refs}`] : []),
    "MIME-Version: 1.0",
    "Content-Type: text/plain; charset=UTF-8",
    "Content-Transfer-Encoding: base64",
    "",
    Buffer.from(text, "utf8").toString("base64").replace(/.{76}/g, "$&\r\n"),
  ].join("\r\n");

  const raw = Buffer.from(mime, "utf8").toString("base64url");
  const sent = await gmail<{ id: string }>("/messages/send", {
    method: "POST",
    body: JSON.stringify({ raw, threadId: t.id }),
  });
  return { id: sent.id, to };
}
