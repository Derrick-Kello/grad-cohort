"use client";

import { useState } from "react";
import { Download } from "lucide-react";
import { api, download } from "@/lib/api";
import { isoDay } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { EligibilityRow } from "@/lib/types";
import { DataTable } from "./data-table";
import { ConfirmDialog } from "./dialog";
import { useToast } from "./toast";
import { Button, ErrorBox, Input, Loading, StatusChip } from "./ui";

/** Exam eligibility list as of a chosen date with CSV / PDF export (FR-ATT-09) and admin exceptions (FR-ATT-10). */
export function EligibilityPanel({ courseId, courseCode, canGrantException }: { courseId: string; courseCode: string; canGrantException?: boolean }) {
  const [asOf, setAsOf] = useState(isoDay());
  const { data, error, mutate } = useApi<EligibilityRow[]>(`/lecturer/courses/${courseId}/eligibility?asOf=${asOf}`);
  const [target, setTarget] = useState<EligibilityRow | null>(null);
  const [revoking, setRevoking] = useState<EligibilityRow | null>(null);
  const toast = useToast();
  const grant = useAction(async (row: EligibilityRow, reason: string) => api.post(`/admin/eligibility/${row.eligibilityId}/exception`, { reason }));
  const revoke = useAction(async (row: EligibilityRow) => api.del(`/admin/eligibility/${row.eligibilityId}/exception`).then(() => true));

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm font-medium">
          Attendance as of
          <Input type="date" value={asOf} max={isoDay()} onChange={(e) => setAsOf(e.target.value)} className="w-44" />
        </label>
        <Button icon={<Download size={15} />} onClick={() => download(`/lecturer/courses/${courseId}/eligibility?asOf=${asOf}&format=csv`, `eligibility-${courseCode}.csv`)}>
          CSV
        </Button>
        <Button icon={<Download size={15} />} onClick={() => download(`/lecturer/courses/${courseId}/eligibility?asOf=${asOf}&format=pdf`, `eligibility-${courseCode}.pdf`)}>
          PDF
        </Button>
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(r) => r.studentId}
          searchText={(r) => `${r.name} ${r.number}`}
          searchPlaceholder="Search students"
          emptyTitle="No students enrolled"
          rowClassName={(r) => (r.status === "NOT_ELIGIBLE" ? "bg-bad-bg/40" : undefined)}
          columns={[
            { key: "name", header: "Student", sortable: true, value: (r) => r.name, render: (r) => <div><p className="font-medium">{r.name}</p><p className="text-xs text-muted">{r.number} · {r.programmeCode}</p></div> },
            { key: "counted", header: "Sessions", sortable: true, value: (r) => r.sessionsCounted, className: "tabular" },
            { key: "attended", header: "Attended", sortable: true, value: (r) => r.sessionsAttended, className: "tabular" },
            { key: "pct", header: "Attendance", sortable: true, value: (r) => r.attendancePct ?? null, render: (r) => <span className="tabular font-medium">{r.attendancePct === null ? "No sessions" : `${r.attendancePct}%`}</span> },
            {
              key: "status",
              header: "Status",
              sortable: true,
              value: (r) => r.status,
              render: (r) => (
                <div className="flex flex-col gap-1">
                  <StatusChip status={r.status} />
                  {r.exceptionReason && <span className="text-xs text-muted">{r.exceptionReason}</span>}
                </div>
              ),
            },
            ...(canGrantException
              ? [
                  {
                    key: "actions",
                    header: "",
                    render: (r: EligibilityRow) =>
                      r.eligibilityId && r.status === "NOT_ELIGIBLE" ? (
                        <Button size="sm" onClick={() => setTarget(r)}>
                          Grant exception
                        </Button>
                      ) : r.eligibilityId && r.status.includes("EXCEPTION") ? (
                        <Button size="sm" variant="ghost" onClick={() => setRevoking(r)}>
                          Remove exception
                        </Button>
                      ) : null,
                  },
                ]
              : []),
          ]}
        />
      )}
      <ConfirmDialog
        open={!!target}
        onClose={() => setTarget(null)}
        title={`Grant an eligibility exception to ${target?.name ?? ""}?`}
        consequence={`${target?.name} will appear as ELIGIBLE (EXCEPTION) on the ${courseCode} exam list despite ${target?.attendancePct}% attendance. The decision is audited.`}
        reasonLabel="Reason (for example, documented medical leave)"
        confirmLabel="Grant exception"
        pending={grant.pending}
        error={grant.error}
        onConfirm={async (reason) => {
          if (target && (await grant.run(target, reason))) {
            toast("Exception granted");
            setTarget(null);
            void mutate();
          }
        }}
      />
      <ConfirmDialog
        open={!!revoking}
        onClose={() => setRevoking(null)}
        title="Remove the exception?"
        consequence="The student's eligibility goes back to the attendance rule."
        confirmLabel="Remove exception"
        tone="danger"
        pending={revoke.pending}
        error={revoke.error}
        onConfirm={async () => {
          if (revoking && (await revoke.run(revoking))) {
            setRevoking(null);
            void mutate();
          }
        }}
      />
    </div>
  );
}
