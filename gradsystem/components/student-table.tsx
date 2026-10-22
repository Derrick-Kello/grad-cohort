"use client";

import { formatDate } from "@/lib/format";
import type { StudentSummary } from "@/lib/types";
import { DataTable } from "./data-table";
import { ProgressBar, StatusChip } from "./ui";

/** Supervisee and progress lists with status chips. */
export function StudentTable({ rows, onOpen, exportName, searchable = true }: { rows: StudentSummary[]; onOpen: (s: StudentSummary) => void; exportName?: string; searchable?: boolean }) {
  return (
    <DataTable
      rows={rows}
      rowKey={(s) => s.id}
      onRowClick={onOpen}
      exportName={exportName}
      searchText={searchable ? (s) => `${s.name} ${s.number} ${s.programmeCode} ${s.cohortName} ${s.supervisorName ?? ""}` : undefined}
      searchPlaceholder="Search name, number, programme"
      emptyTitle="No students"
      initialSort={{ key: "name", dir: "asc" }}
      columns={[
        { key: "name", header: "Student", sortable: true, value: (s) => s.name, render: (s) => <div><p className="font-medium">{s.name}</p><p className="text-xs text-muted">{s.number}</p></div> },
        { key: "programme", header: "Programme", sortable: true, value: (s) => `${s.programmeCode} ${s.cohortName}`, render: (s) => <div><p>{s.programmeCode}</p><p className="text-xs text-muted">{s.cohortName}</p></div> },
        { key: "semester", header: "Sem.", sortable: true, value: (s) => s.currentSemester, className: "tabular", hideOnMobile: true },
        {
          key: "progress",
          header: "Approved",
          sortable: true,
          value: (s) => s.approvedMilestones,
          render: (s) => (
            <div className="flex min-w-[110px] flex-col gap-1">
              <span className="tabular text-sm">
                {s.approvedMilestones} of {s.requiredMilestones}
              </span>
              <ProgressBar value={s.approvedMilestones} max={s.requiredMilestones} tone="ok" label={`${s.name} progress`} />
            </div>
          ),
        },
        { key: "end", header: "Programme end", sortable: true, value: (s) => s.programmeEndOn, render: (s) => <div><p>{formatDate(s.programmeEndOn)}</p><p className="text-xs text-muted">{s.daysToProgrammeEnd >= 0 ? `${s.daysToProgrammeEnd} days left` : "passed"}</p></div>, hideOnMobile: true },
        { key: "supervisor", header: "Supervisor", sortable: true, value: (s) => s.supervisorName ?? "", hideOnMobile: true },
        { key: "status", header: "Status", sortable: true, value: (s) => s.status, render: (s) => <StatusChip status={s.status} /> },
      ]}
    />
  );
}
