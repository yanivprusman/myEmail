# myEmail

A calm Gmail client for the phone. The **server** (Next.js, dev port 3187, runs on the
desktop) talks to Gmail; the **phone app** (`mobile/`, KMP/Compose, launcher name
*MyEmail*) only reads and acts, over WireGuard at `10.7.0.2:3187`.

What it does differently from the Gmail app:

- **Text, not remote HTML.** Mail is shown as text: no tracking pixels load on open,
  nothing reflows, and each Hebrew/English paragraph takes its own direction.
- **Quoted history folded away** ("On … wrote:", "בתאריך … כתב:").
- **Swipe = archive, with Undo** instead of a confirmation.
- The phone never holds a Google credential — only a bearer token for this server.

## API (all require `Authorization: Bearer $MYEMAIL_API_TOKEN`)

| Route | |
|---|---|
| `GET /api/status` | connected address + unread conversation count |
| `GET /api/inbox[?pageToken=]` | the inbox, 25 threads per page |
| `GET /api/thread/:id` | the conversation as text (does not mark read) |
| `POST /api/thread/:id` `{action}` | `archive` `inbox` `read` `unread` `trash` `untrash` |
| `POST /api/thread/:id/reply` `{text}` | reply to the latest message, in-thread |

## Secrets (none in git)

- `.env.local` — `MYEMAIL_API_TOKEN` (server).
- `mobile/.env` — `API_BASE_URL`, `API_TOKEN` (same value; baked into the APK).
- `/etc/automatelinux/myemail-google-client.json` — Google OAuth **Desktop** client
  (currently the `panelshed-ads-api` project's client).
- `/etc/automatelinux/myemail-google-token.json` — the refresh token (mode 600).

## Connecting Gmail

1. In Google Cloud, project `panelshed-ads-api`: **enable the Gmail API**
   (APIs & Services → Library → Gmail API → Enable). Keep the OAuth consent
   screen in **In production** — in *Testing*, Google expires the sign-in every 7 days.
2. On the desktop: `node scripts/connect-google.mjs` — signs in through the browser
   and writes the token file. Google shows an "unverified app" warning once:
   Advanced → continue.
