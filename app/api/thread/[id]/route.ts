import { guarded } from "@/lib/respond";
import { act, ACTIONS, getThread, GmailError, isAction } from "@/lib/gmail";

type Ctx = { params: Promise<{ id: string }> };

/** The whole conversation, as text. Opening it does NOT mark it read — the
 *  phone says so explicitly, so a preview fetch can never eat an unread mark. */
export async function GET(request: Request, { params }: Ctx) {
  const { id } = await params;
  return guarded(request, async () => ({ thread: await getThread(id) }));
}

/** POST {action: archive|inbox|read|unread|trash|untrash} */
export async function POST(request: Request, { params }: Ctx) {
  const { id } = await params;
  return guarded(request, async () => {
    const { action } = (await request.json().catch(() => ({}))) as { action?: string };
    if (!action || !isAction(action))
      throw new GmailError(`action must be one of ${ACTIONS.join(", ")}`, 400);
    await act(id, action);
    return { action };
  });
}
