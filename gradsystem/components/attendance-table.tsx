"use client";

import type { StudentAttendance } from "@/lib/types";
import { DataTable } from "./data-table";
import { ProgressBar, StatusChip } from "./ui";

/** Attendance and eligibility per course (FR-ATT-07). */
export function AttendanceTable({ rows }: { rows: StudentAttendance[] }) {
  return (
    <DataTable
      rows={rows}
      rowKey={(r) => r.courseId}
      emptyTitle="No courses"
      emptyText="You are not enrolled in any course this semester."
      columns={[
        { key: "course", header: "Course", value: (r) => r.code, render: (r) => <div><p className="font-medium">{r.code}</p><p className="text-xs text-muted">{r.title}</p></div> },
        { key: "semester", header: "Semester", value: (r) => r.semesterLabel, hideOnMobile: true },
        { key: "held", header: "Sessions", value: (r) => r.sessionsCounted, className: "tabular" },
        { key: "attended", header: "Attended", value: (r) => r.sessionsAttended, className: "tabular" },
        {
          key: "pct",
          header: "Attendance",
          value: (r) => r.attendancePct ?? null,
          render: (r) =>
            r.attendancePct === null ? (
              <span className="text-muted">No sessions yet</span>
            ) : (
              <div className="flex min-w-[120px] flex-col gap-1">
                <span className="tabular font-medium">{r.attendancePct}%</span>
                <ProgressBar value={Number(r.attendancePct)} max={100} tone={Number(r.attendancePct) >= r.threshold ? (Number(r.attendancePct) < r.threshold + 5 ? "warn" : "ok") : "bad"} label={`${r.code} attendance`} />
              </div>
            ),
        },
        { key: "status", header: "Exam", value: (r) => r.status, render: (r) => <StatusChip status={r.status} /> },
        {
          key: "miss",
          header: "Can still miss",
          value: (r) => r.canMiss,
          render: (r) => (r.status === "NOT_ELIGIBLE" ? <span className="text-bad">Below {r.threshold}%</span> : <span className="tabular">{r.canMiss} session{r.canMiss === 1 ? "" : "s"}</span>),
        },
      ]}
    />
  );
}
