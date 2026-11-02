import "server-only";
import { NextResponse } from "next/server";
import { authorize } from "./auth";
import { GmailError } from "./gmail";

/**
 * Every route is: authorize, do one Gmail thing, answer JSON. Failures are
 * `{ok:false, error}` with a real status — the phone renders `error` as a
 * sentence, so it must be one a person can act on.
 */
export async function guarded(request: Request, work: () => Promise<unknown>) {
  const denied = authorize(request);
  if (denied) return NextResponse.json({ ok: false, error: denied }, { status: 401 });
  try {
    const result = await work();
    return NextResponse.json({ ok: true, ...(result as object) });
  } catch (e) {
    const status = e instanceof GmailError ? e.status : 500;
    return NextResponse.json({ ok: false, error: e instanceof Error ? e.message : String(e) }, { status });
  }
}
