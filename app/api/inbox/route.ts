import { guarded } from "@/lib/respond";
import { listInbox } from "@/lib/gmail";

/** GET /api/inbox[?pageToken=…] — the inbox, 25 conversations per page. */
export async function GET(request: Request) {
  return guarded(request, async () => listInbox(new URL(request.url).searchParams.get("pageToken") ?? undefined));
}
