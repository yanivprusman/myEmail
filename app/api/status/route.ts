import { guarded } from "@/lib/respond";
import { myAddress, unreadCounts } from "@/lib/gmail";

/** Which mailbox is connected, and how much is waiting in each view. */
export async function GET(request: Request) {
  return guarded(request, async () => {
    const [email, unread] = await Promise.all([myAddress(), unreadCounts()]);
    return { email, unread };
  });
}
