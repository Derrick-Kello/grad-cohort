"use client";

import Link from "next/link";
import { useState } from "react";
import { Pager } from "@/components/data-table";
import { Card, Empty, ErrorBox, Loading, PageHeader } from "@/components/ui";
import { formatDateTime, pretty } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Comment, Page } from "@/lib/types";

export default function SupervisorCommentsPage() {
  const [page, setPage] = useState(0);
  const { data, error, mutate } = useApi<Page<Comment>>(`/comments/mine?page=${page}&size=25`);
  return (
    <>
      <PageHeader title="Comments" description="Comments you have left on supervisees' evidence, milestones and thesis versions. To add one, open the student." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card padded={false}>
          {data.content.length === 0 ? (
            <Empty title="No comments yet">Open a student from My Students to comment on their work.</Empty>
          ) : (
            <ul className="divide-y divide-border">
              {data.content.map((c) => (
                <li key={c.id} className="px-5 py-3.5 text-sm">
                  <p className="text-xs text-muted">
                    {c.studentId ? (
                      <Link href={`/supervisor/students/${c.studentId}`} className="font-medium text-primary hover:underline">
                        {c.studentName}
                      </Link>
                    ) : null}{" "}
                    · {pretty(c.targetType)}: {c.targetLabel} · {formatDateTime(c.createdAt)}
                  </p>
                  <p className="mt-1 whitespace-pre-wrap">{c.body}</p>
                </li>
              ))}
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
