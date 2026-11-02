import type { Metadata } from "next";
import "./globals.css";
import FeedbackChatMount from "./FeedbackChatMount";

export const metadata: Metadata = {
  title: "myEmail",
  description: "myEmail — a calm, fast Gmail client: the server talks to Gmail, the phone just reads and acts",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en">
      <body>{children}
        <FeedbackChatMount />
      </body>
    </html>
  );
}
