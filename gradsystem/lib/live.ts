"use client";

import { useEffect, useState } from "react";
import { mutate } from "swr";
import { API_URL, api, session } from "./api";
import type { Notification } from "./types";

/**
 * Unread notification count, kept live over server-sent events (FR-NTF-03). EventSource cannot send the bearer
 * header, so this reads the stream with fetch and parses the events by hand. It reconnects with backoff.
 */
export function useUnreadCount(enabled: boolean, onNotification?: (n: Notification) => void) {
  const [unread, setUnread] = useState<number | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let stopped = false;
    let controller: AbortController | null = null;
    let delay = 2000;

    const connect = async () => {
      while (!stopped) {
        controller = new AbortController();
        try {
          const res = await fetch(`${API_URL}/notifications/stream`, {
            headers: { Authorization: `Bearer ${session.access() ?? ""}`, Accept: "text/event-stream" },
            signal: controller.signal,
          });
          if (res.status === 401) {
            // Triggers the refresh-token rotation, then reconnects with the new access token.
            await api.get("/me").catch(() => undefined);
            if (!session.access()) return;
            continue;
          }
          if (!res.ok || !res.body) throw new Error("stream failed");
          delay = 2000;
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = "";
          for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            let index;
            while ((index = buffer.indexOf("\n\n")) >= 0) {
              const raw = buffer.slice(0, index);
              buffer = buffer.slice(index + 2);
              let event = "message";
              let data = "";
              for (const line of raw.split("\n")) {
                if (line.startsWith("event:")) event = line.slice(6).trim();
                else if (line.startsWith("data:")) data += line.slice(5).trim();
              }
              if (!data) continue;
              try {
                const parsed = JSON.parse(data);
                if (event === "unread") setUnread(parsed.unread);
                if (event === "notification") {
                  onNotification?.(parsed as Notification);
                  void mutate((key) => typeof key === "string" && key.startsWith("/notifications"));
                }
              } catch {
                // Ignore malformed events.
              }
            }
          }
        } catch {
          if (stopped) return;
        }
        if (stopped) return;
        await new Promise((r) => setTimeout(r, delay));
        delay = Math.min(delay * 2, 30000);
      }
    };

    api
      .get<{ unread: number }>("/notifications/unread-count")
      .then((r) => !stopped && setUnread(r.unread))
      .catch(() => undefined);
    void connect();
    return () => {
      stopped = true;
      controller?.abort();
    };
    // onNotification is read through the closure on purpose; reconnecting on every render would drop events.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled]);

  return [unread, setUnread] as const;
}
