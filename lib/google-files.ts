import "server-only";

/**
 * Where the Google credentials live. Outside the repo, mode 600, shared by dev
 * and prod on the same peer — the mailbox is one mailbox whichever server reads it.
 *
 *  - CLIENT: an OAuth "Desktop app" client JSON from Google Cloud ({installed:{...}}).
 *    Its project must have the Gmail API enabled.
 *  - TOKEN: written by `node scripts/connect-google.mjs` after the owner signs in.
 */
export const CLIENT_FILE =
  process.env.MYEMAIL_GOOGLE_CLIENT_FILE ?? "/etc/automatelinux/myemail-google-client.json";
export const TOKEN_FILE =
  process.env.MYEMAIL_GOOGLE_TOKEN_FILE ?? "/etc/automatelinux/myemail-google-token.json";
