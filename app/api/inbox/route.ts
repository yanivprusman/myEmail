import { guarded } from "@/lib/respond";
import { GmailError, isView, listInbox } from "@/lib/gmail";

/** GET /api/inbox?view=people|other[&pageToken=…] */
export async function GET(request: Request) {
  return guarded(request, async () => {
    const qs = new URL(request.url).searchParams;
    const view = qs.get("view") ?? "people";
    if (!isView(view)) throw new GmailError(`unknown view '${view}' (people|other)`, 400);
    return listInbox(view, qs.get("pageToken") ?? undefined);
  });
}
