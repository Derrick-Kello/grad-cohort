"use client";

import Link from "next/link";
import { Card, Empty, ErrorBox, Loading, PageHeader, Stat } from "@/components/ui";
import { formatDate } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Publication } from "@/lib/types";

type LibrarianDashboard = { awaitingReview: number; oldestDays: number; approvedTotal: number; oldest: Publication[] };

export default function LibrarianDashboardPage() {
  const { data, error, mutate } = useApi<LibrarianDashboard>("/librarian/dashboard");
  return (
    <>
      <PageHeader title="Publication reviews" description="Publications count on a student's record only after you verify them." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Stat label="Awaiting review" value={data.awaitingReview} tone={data.awaitingReview > 0 ? "warn" : undefined} />
            <Stat label="Oldest waiting" value={`${data.oldestDays} days`} />
            <Stat label="Approved in total" value={data.approvedTotal} tone="ok" />
          </div>
          <Card title="Oldest in the queue" actions={<Link href="/librarian/queue" className="text-sm font-medium text-primary hover:underline">Open the queue</Link>}>
            {data.oldest.length === 0 ? (
              <Empty title="The queue is empty" />
            ) : (
              <ul className="divide-y divide-border">
                {data.oldest.map((p) => (
                  <li key={p.id} className="py-2.5 text-sm">
                    <Link href={`/librarian/queue?id=${p.id}`} className="font-medium hover:underline">
                      {p.title}
                    </Link>
                    <p className="text-muted">
                      {p.studentName} · submitted {formatDate(p.submittedAt)} · {p.daysWaiting} days waiting
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}
    </>
  );
}
