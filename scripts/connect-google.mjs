#!/usr/bin/env node
// Connect myEmail to the owner's Gmail — run once, on the peer that serves the app.
//
//   node scripts/connect-google.mjs
//
// Desktop-app OAuth with a loopback redirect + PKCE: this script listens on
// 127.0.0.1 for the one redirect Google sends back, so the sign-in never passes
// through the app's HTTP server (which is reachable from the LAN and must not
// be able to swap whose mailbox it reads).
//
// Writes the refresh token to MYEMAIL_GOOGLE_TOKEN_FILE (default
// /etc/automatelinux/myemail-google-token.json), mode 600.

import { createServer } from "node:http";
import { readFileSync, writeFileSync, chmodSync } from "node:fs";
import { randomBytes, createHash } from "node:crypto";
import { spawn } from "node:child_process";

const CLIENT_FILE = process.env.MYEMAIL_GOOGLE_CLIENT_FILE ?? "/etc/automatelinux/myemail-google-client.json";
const TOKEN_FILE = process.env.MYEMAIL_GOOGLE_TOKEN_FILE ?? "/etc/automatelinux/myemail-google-token.json";
// gmail.modify = read, label, archive, trash (to Trash, never permanent), send.
const SCOPE = "https://www.googleapis.com/auth/gmail.modify";

const raw = JSON.parse(readFileSync(CLIENT_FILE, "utf8"));
const client = raw.installed;
if (!client?.client_id) {
  console.error(`${CLIENT_FILE}: expected a Desktop-app client ({"installed": {...}})`);
  process.exit(1);
}

const verifier = randomBytes(32).toString("base64url");
const challenge = createHash("sha256").update(verifier).digest("base64url");
const state = randomBytes(16).toString("hex");

const server = createServer(async (req, res) => {
  const url = new URL(req.url, "http://127.0.0.1");
  if (url.pathname !== "/") return res.writeHead(404).end();
  const fail = (msg) => {
    res.writeHead(400, { "content-type": "text/plain; charset=utf-8" }).end(msg);
    console.error(msg);
    server.close();
    process.exitCode = 1;
  };
  if (url.searchParams.get("state") !== state) return fail("state mismatch — start again");
  if (url.searchParams.get("error")) return fail(`Google said: ${url.searchParams.get("error")}`);

  const tokenRes = await fetch(client.token_uri ?? "https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      code: url.searchParams.get("code") ?? "",
      client_id: client.client_id,
      client_secret: client.client_secret,
      redirect_uri: redirectUri,
      grant_type: "authorization_code",
      code_verifier: verifier,
    }),
  });
  const tok = await tokenRes.json();
  if (!tok.refresh_token) return fail(`no refresh token in Google's answer: ${JSON.stringify(tok)}`);

  const profile = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/profile", {
    headers: { authorization: `Bearer ${tok.access_token}` },
  }).then((r) => r.json());
  if (!profile.emailAddress) return fail(`token works but Gmail refused: ${JSON.stringify(profile)}`);

  writeFileSync(
    TOKEN_FILE,
    JSON.stringify({ refresh_token: tok.refresh_token, scope: tok.scope, email: profile.emailAddress, at: new Date().toISOString() }, null, 2),
    { mode: 0o600 },
  );
  chmodSync(TOKEN_FILE, 0o600);
  res.writeHead(200, { "content-type": "text/plain; charset=utf-8" }).end(`myEmail is connected to ${profile.emailAddress}. You can close this tab.`);
  console.log(`connected: ${profile.emailAddress} → ${TOKEN_FILE}`);
  server.close();
});

let redirectUri = "";
server.listen(0, "127.0.0.1", () => {
  redirectUri = `http://127.0.0.1:${server.address().port}/`;
  const auth = new URL(client.auth_uri ?? "https://accounts.google.com/o/oauth2/auth");
  auth.search = new URLSearchParams({
    client_id: client.client_id,
    redirect_uri: redirectUri,
    response_type: "code",
    scope: SCOPE,
    access_type: "offline",
    prompt: "consent",
    state,
    code_challenge: challenge,
    code_challenge_method: "S256",
  }).toString();
  console.log(`Sign in here (opening it in the desktop browser):\n\n${auth}\n`);
  spawn("/opt/automateLinux/utilities/runInGuiSession.sh", ["xdg-open", auth.toString()], {
    stdio: "ignore",
    detached: true,
  }).unref();
});
