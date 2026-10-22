"use client";

import { formatDate, formatTime, weekday } from "@/lib/format";
import type { PresentationRow } from "@/lib/types";
import { Empty, StatusChip } from "./ui";

/** Presentations grouped by day, used by the secretary calendar and dashboards. */
export function PresentationAgenda({ rows, action, empty = "No presentations in this period." }: { rows: PresentationRow[]; action?: (r: PresentationRow) => React.ReactNode; empty?: string }) {
  if (rows.length === 0) return <Empty title={empty} />;
  const days = new Map<string, PresentationRow[]>();
  for (const r of rows) {
    const key = formatDate(r.scheduledStart);
    days.set(key, [...(days.get(key) ?? []), r]);
  }
  return (
    <div className="flex flex-col gap-5">
      {[...days.entries()].map(([day, list]) => (
        <section key={day}>
          <h3 className="mb-2 text-sm font-semibold text-muted">
            {weekday(list[0].scheduledStart)} {day}
          </h3>
          <ul className="flex flex-col divide-y divide-border rounded-lg border border-border bg-surface">
            {list.map((r) => (
              <li key={r.attemptId} className="flex flex-col gap-2 px-4 py-3 sm:flex-row sm:items-center">
                <p className="tabular w-28 shrink-0 text-sm font-medium">
                  {formatTime(r.scheduledStart)} to {formatTime(r.scheduledEnd)}
                </p>
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-medium">
                    {r.studentName} <span className="font-normal text-muted">({r.studentNumber}, {r.programmeCode})</span>
                  </p>
                  <p className="text-muted">
                    Presentation {r.sequenceNo}: {r.milestoneTitle}
                    {r.attemptNo > 1 ? ` · attempt ${r.attemptNo}` : ""} · {r.venueName ?? "Online"}
                  </p>
                  {r.panel.length > 0 && <p className="text-xs text-muted">Panel: {r.panel.map((p) => p.name ?? p.externalName).join(", ")}</p>}
                </div>
                <div className="flex items-center gap-2">
                  <StatusChip status={r.state} />
                  {action?.(r)}
                </div>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
