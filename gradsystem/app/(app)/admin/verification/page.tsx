"use client";

import Link from "next/link";
import { useState } from "react";
import { ConfirmDialog, Dialog } from "@/components/dialog";
import { CommentThread, EvidenceFiles } from "@/components/evidence-list";
import { DataTable } from "@/components/data-table";
import { useToast } from "@/components/toast";
import { Banner, Button, ErrorBox, KeyValue, Loading, PageHeader, StatusChip } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { VerificationItem } from "@/lib/types";

/** Verification queue (FR-VER-01..03): oldest first; approve unlocks the next milestone, return needs a comment. */
export default function VerificationPage() {
  const { data, error, mutate } = useApi<VerificationItem[]>("/admin/verification-queue");
  const [open, setOpen] = useState<VerificationItem | null>(null);
  return (
    <>
      <PageHeader title="Verification queue" description="Passed presentations count only after you check the evidence and approve them. Approval opens the student's next presentation." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(r) => r.milestoneId}
          onRowClick={setOpen}
          exportName="verification-queue"
          searchText={(r) => `${r.studentName} ${r.studentNumber}`}
          emptyTitle="The queue is empty"
          emptyText="Nothing is waiting for verification."
          columns={[
            { key: "student", header: "Student", sortable: true, value: (r) => r.studentName, render: (r) => <div><p className="font-medium">{r.studentName}</p><p className="text-xs text-muted">{r.studentNumber}</p></div> },
            { key: "programme", header: "Programme", sortable: true, value: (r) => r.programmeCode },
            { key: "milestone", header: "Presentation", value: (r) => `Presentation ${r.sequenceNo}: ${r.title}` },
            { key: "outcome", header: "Outcome recorded", sortable: true, value: (r) => r.outcomeAt ?? "", render: (r) => formatDateTime(r.outcomeAt) },
            {
              key: "evidence",
              header: "Evidence",
              sortable: true,
              value: (r) => r.evidenceCount,
              render: (r) => (r.evidenceCount === 0 && r.evidenceRequired ? <StatusChip status="AT_RISK" label="Missing" /> : <span className="tabular">{r.evidenceCount} file{r.evidenceCount === 1 ? "" : "s"}</span>),
            },
            { key: "waiting", header: "Days waiting", sortable: true, value: (r) => r.daysWaiting, render: (r) => <span className={`tabular ${r.daysWaiting > 5 ? "font-semibold text-warn" : ""}`}>{r.daysWaiting}</span> },
          ]}
        />
      )}
      {open && <ReviewDialog item={open} onClose={() => setOpen(null)} onDone={() => { setOpen(null); void mutate(); }} />}
    </>
  );
}

function ReviewDialog({ item, onClose, onDone }: { item: VerificationItem; onClose: () => void; onDone: () => void }) {
  const [confirm, setConfirm] = useState<"approve" | "return" | null>(null);
  const toast = useToast();
  const approve = useAction(async () => api.post(`/admin/milestones/${item.milestoneId}/approve`));
  const ret = useAction(async (comment: string) => api.post(`/admin/milestones/${item.milestoneId}/return`, { comment }));
  const missing = item.evidenceRequired && item.evidenceCount === 0;
  return (
    <>
      <Dialog
        open={!confirm}
        wide
        onClose={onClose}
        title={`${item.studentName}: Presentation ${item.sequenceNo}`}
        footer={
          <>
            <Button onClick={() => setConfirm("return")}>Return with comments</Button>
            <Button variant="primary" disabled={missing} onClick={() => setConfirm("approve")}>
              Approve
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <KeyValue
            items={[
              ["Presentation", item.title],
              ["Programme", item.programmeCode],
              ["Outcome recorded", formatDateTime(item.outcomeAt)],
              ["Score", item.score],
              ["Panel remarks", item.panelRemarks],
              ["Evidence", item.evidenceRequired ? "Required" : "Optional for this programme"],
            ]}
          />
          {missing && <Banner tone="warn" title="No evidence yet">Approval stays disabled until the student uploads at least one file.</Banner>}
          <section>
            <h3 className="mb-2 text-sm font-semibold">Evidence files</h3>
            <EvidenceFiles milestoneId={item.milestoneId} readOnly />
          </section>
          <section>
            <h3 className="mb-2 text-sm font-semibold">Comments</h3>
            <CommentThread targetType="MILESTONE" targetId={item.milestoneId} />
          </section>
          <Link href={`/admin/students/${item.studentId}`} className="text-sm font-medium text-primary hover:underline">
            Open the student record
          </Link>
        </div>
      </Dialog>
      <ConfirmDialog
        open={confirm === "approve"}
        onClose={() => setConfirm(null)}
        title={`Approve Presentation ${item.sequenceNo}?`}
        consequence={`It counts as approved for ${item.studentName} and the next presentation opens for scheduling. The student and supervisor are notified. Approved evidence becomes read-only.`}
        confirmLabel="Approve"
        pending={approve.pending}
        error={approve.error}
        onConfirm={async () => {
          if (await approve.run()) {
            toast("Approved. The next presentation is open.");
            onDone();
          }
        }}
      />
      <ConfirmDialog
        open={confirm === "return"}
        onClose={() => setConfirm(null)}
        title="Return to the student?"
        consequence="The student is notified and can upload corrected evidence, which puts it back in this queue."
        reasonLabel="What needs to change"
        confirmLabel="Return"
        pending={ret.pending}
        error={ret.error}
        onConfirm={async (comment) => {
          if (await ret.run(comment)) {
            toast("Returned to the student");
            onDone();
          }
        }}
      />
    </>
  );
}
