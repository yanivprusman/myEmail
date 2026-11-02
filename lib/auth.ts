import "server-only";
import { timingSafeEqual } from "node:crypto";

/**
 * The one guard on this app.
 *
 * Behind these routes is the owner's whole mailbox — reading it, archiving it,
 * and sending mail in his name. The server listens on 0.0.0.0 (WireGuard AND
 * the home LAN), so "only my devices can reach it" is not enough to rely on.
 *
 * One secret, `MYEMAIL_API_TOKEN`, sent by the phone as a bearer token (baked
 * into the APK from the gitignored mobile/.env). A missing token in the
 * environment REFUSES every request: an API that guards nothing because its
 * secret was never set looks like it is working.
 */
function secret(): string {
  return process.env.MYEMAIL_API_TOKEN ?? "";
}

function same(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

/** Null when the request may proceed; otherwise the reason it may not. */
export function authorize(request: Request): string | null {
  if (!secret()) return "MYEMAIL_API_TOKEN is not set on the server";
  const header = request.headers.get("authorization") ?? "";
  if (!header.startsWith("Bearer ")) return "no bearer token";
  return same(header.slice(7), secret()) ? null : "bad token";
}
