"use client";

import { useState } from "react";
import { Pager } from "@/components/data-table";
import { StudentDetail } from "@/components/student-detail";
import { StudentTable } from "@/components/student-table";
import { Button, ErrorBox, Input, Loading, PageHeader } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { Page, StudentSummary } from "@/lib/types";

/** Progress lookup for the secretary: search, then open a student read-only. */
export default function StudentProgressPage() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<string | null>(null);
  const { data, error, mutate } = useApi<Page<StudentSummary>>(`/admin/students?q=${encodeURIComponent(q)}&page=${page}&size=25`);

  if (open) {
    return (
      <>
        <Button size="sm" className="mb-4" onClick={() => setOpen(null)}>
          Back to search
        </Button>
        <StudentDetail studentId={open} canComment={false} />
      </>
    );
  }
  return (
    <>
      <PageHeader title="Student progress" description="Look up any student's presentations, evidence and status." />
      <div className="mb-4 max-w-sm">
        <Input aria-label="Search students" placeholder="Search name, email or number" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-3">
          <StudentTable searchable={false} rows={data.content} onOpen={(s) => setOpen(s.id)} />
          <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </div>
      )}
    </>
  );
}
