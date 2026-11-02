import { guarded } from "@/lib/respond";
import { GmailError, reply } from "@/lib/gmail";

/** POST {text} — reply to the thread's latest message, from the owner's own address. */
export async function POST(request: Request, { params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return guarded(request, async () => {
    const { text } = (await request.json().catch(() => ({}))) as { text?: string };
    if (!text?.trim()) throw new GmailError("reply text is empty", 400);
    return reply(id, text.trim());
  });
}
