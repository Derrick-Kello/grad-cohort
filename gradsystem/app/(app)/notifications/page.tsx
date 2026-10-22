"use client";

import Link from "next/link";
import { useState } from "react";
import { CheckCheck } from "lucide-react";
import { Pager } from "@/components/data-table";
import { Button, Card, Checkbox, Empty, ErrorBox, Loading, PageHeader, Select, cx } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Notification, Page } from "@/lib/types";

const TYPES: [string, string][] = [
  ["", "All types"],
  ["N-02", "Presentation scheduled"],
  ["N-03", "Rescheduled or cancelled"],
  ["N-04", "Reminders"],
  ["N-05", "Outcomes"],
  ["N-07", "Approvals"],
  ["N-08", "Returned"],
  ["N-10", "Publication reviews"],
  ["N-12", "Thesis screening"],
  ["N-14", "Attendance"],
  ["N-15", "Deadlines"],
  ["N-16", "At risk"],
  ["N-17", "Expulsion"],
  ["ANN", "Announcements"],
];

/** Notification centre (FR-NTF-01): read / unread, filter by type, mark all read, links to the page. */
export default function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [code, setCode] = useState("");
  const [page, setPage] = useState(0);
  const path = `/notifications?page=${page}&size=25&unreadOnly=${unreadOnly}${code ? `&code=${code}` : ""}`;
  const { data, error, mutate } = useApi<Page<Notification>>(path);

  const markAll = async () => {
    await api.post("/notifications/read-all");
    void mutate();
  };
  const markRead = async (n: Notification) => {
    if (!n.readAt) await api.post(`/notifications/${n.id}/read`).catch(() => undefined);
  };

  return (
    <>
      <PageHeader
        title="Notifications"
        description="Every update that needs your attention. Each one is also sent by email."
        actions={
          <Button icon={<CheckCheck size={16} />} onClick={markAll}>
            Mark all read
          </Button>
        }
      />
      <div className="mb-4 flex flex-wrap items-center gap-4">
        <Select aria-label="Filter by type" value={code} onChange={(e) => { setCode(e.target.value); setPage(0); }} className="w-56">
          {TYPES.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </Select>
        <Checkbox label="Unread only" checked={unreadOnly} onChange={(v) => { setUnreadOnly(v); setPage(0); }} />
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card padded={false}>
          {data.content.length === 0 ? (
            <Empty title="You're all caught up">No notifications match these filters.</Empty>
          ) : (
            <ul className="divide-y divide-border">
              {data.content.map((n) => {
                const body = (
                  <div className="flex gap-3">
                    <span aria-hidden className={cx("mt-1.5 h-2 w-2 shrink-0 rounded-full", n.readAt ? "bg-transparent" : "bg-primary")} />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline justify-between gap-x-3">
                        <p className={cx("text-sm", n.readAt ? "font-medium" : "font-semibold")}>
                          {n.title}
                          {!n.readAt && <span className="sr-only"> (unread)</span>}
                        </p>
                        <p className="text-xs text-muted">{formatDateTime(n.createdAt)}</p>
                      </div>
                      <p className="mt-0.5 text-sm text-muted">{n.body}</p>
                      <p className="mt-1 text-xs text-faint">
                        {n.code} · {n.codeLabel}
                      </p>
                    </div>
                  </div>
                );
                return (
                  <li key={n.id}>
                    {n.link ? (
                      <Link href={n.link} onClick={() => markRead(n)} className="block px-5 py-4 hover:bg-surface-2">
                        {body}
                      </Link>
                    ) : (
                      <button type="button" onClick={() => markRead(n).then(() => mutate())} className="block w-full px-5 py-4 text-left hover:bg-surface-2">
                        {body}
                      </button>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </Card>
      )}
      {data && (
        <div className="mt-3">
          <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </div>
      )}
    </>
  );
}
