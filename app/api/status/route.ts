import { guarded } from "@/lib/respond";
import { myAddress, unreadCount } from "@/lib/gmail";

/** Which mailbox is connected, and how many conversations are unread. */
export async function GET(request: Request) {
  return guarded(request, async () => {
    const [email, unread] = await Promise.all([myAddress(), unreadCount()]);
    return { email, unread };
  });
}
