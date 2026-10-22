"use client";

import { useState } from "react";
import { CalendarPlus, Check, ChevronDown, Lock } from "lucide-react";
import { download } from "@/lib/api";
import { formatDate, formatDateTime, formatTime } from "@/lib/format";
import type { Attempt, Milestone } from "@/lib/types";
import { Button, StatusChip, cx } from "./ui";

function where(a: Attempt) {
  if (a.venueName && a.onlineLink) return `${a.venueName} (also online)`;
  return a.venueName ?? "Online";
}

/** Milestone timeline used on the student dashboard, presentations page and supervisor views. */
export function MilestoneTimeline({ milestones, compact, showCalendar }: { milestones: Milestone[]; compact?: boolean; showCalendar?: boolean }) {
  return (
    <ol className="flex flex-col">
      {milestones.map((m, i) => (
        <MilestoneItem key={m.id} m={m} last={i === milestones.length - 1} compact={compact} showCalendar={showCalendar} />
      ))}
    </ol>
  );
}

function MilestoneItem({ m, last, compact, showCalendar }: { m: Milestone; last: boolean; compact?: boolean; showCalendar?: boolean }) {
  const [open, setOpen] = useState(false);
  const a = m.currentAttempt;
  const done = m.status === "APPROVED";
  const locked = m.status === "LOCKED";
  return (
    <li className="relative flex gap-4 pb-5 last:pb-0">
      {!last && <span aria-hidden className="absolute top-9 bottom-0 left-[15px] w-px bg-border" />}
      <span
        aria-hidden
        className={cx(
          "relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border text-sm font-semibold",
          done && "border-ok bg-ok text-white dark:text-[#0b1a12]",
          locked && "border-border bg-surface-2 text-faint",
          !done && !locked && "border-primary bg-primary-soft text-primary",
        )}
      >
        {done ? <Check size={16} /> : locked ? <Lock size={13} /> : m.sequenceNo}
      </span>
      <div className={cx("min-w-0 flex-1", locked && "opacity-75")}>
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-medium">
            {m.label}: {m.title}
          </p>
          <StatusChip status={m.status} />
          {m.finalPreDefense && <span className="text-xs text-muted">Final pre-defense</span>}
        </div>
        <div className="mt-1 text-sm text-muted">
          {locked && <p>{m.lockedReason}</p>}
          {m.status === "AVAILABLE" && <p>Open for scheduling{m.failedAttempts > 0 ? `. ${m.failedAttempts} of ${m.maxAttempts} attempts used.` : "."}</p>}
          {m.status === "SCHEDULED" && a && (
            <p>
              {formatDateTime(a.scheduledStart)} to {formatTime(a.scheduledEnd)} at {where(a)}
              {a.attemptNo > 1 ? ` (attempt ${a.attemptNo})` : ""}
            </p>
          )}
          {m.status === "AWAITING_VERIFICATION" && (
            <p>
              Passed{a?.recordedAt ? ` on ${formatDate(a.recordedAt)}` : ""}. {m.evidenceRequired && m.evidenceCount === 0 ? "Evidence needed before approval." : "Awaiting admin verification."}
            </p>
          )}
          {m.status === "RETURNED" && <p>Returned by the graduate school. Upload corrected evidence.</p>}
          {done && (
            <p>
              Approved {formatDate(m.approvedAt)}
              {m.approvedByName ? ` by ${m.approvedByName}` : ""}
            </p>
          )}
        </div>
        {!compact && (
          <div className="mt-2 flex flex-wrap gap-2">
            {showCalendar && m.status === "SCHEDULED" && a && (
              <Button size="sm" icon={<CalendarPlus size={14} />} onClick={() => download(`/attempts/${a.id}/calendar.ics`, "presentation.ics")}>
                Add to calendar
              </Button>
            )}
            {m.attempts.length > 0 && (
              <Button size="sm" variant="ghost" onClick={() => setOpen(!open)} aria-expanded={open}>
                {m.attempts.length} attempt{m.attempts.length === 1 ? "" : "s"} <ChevronDown size={14} className={cx("transition-transform", open && "rotate-180")} />
              </Button>
            )}
          </div>
        )}
        {open && <AttemptList attempts={m.attempts} />}
      </div>
    </li>
  );
}

export function AttemptList({ attempts }: { attempts: Attempt[] }) {
  return (
    <ul className="mt-3 flex flex-col gap-2">
      {[...attempts].reverse().map((a) => (
        <li key={a.id} className="rounded-lg border border-border bg-surface-2 p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium">Attempt {a.attemptNo}</span>
            <StatusChip status={a.state} />
            <span className="text-muted">
              {formatDateTime(a.scheduledStart)} · {where(a)}
            </span>
          </div>
          {a.panel.length > 0 && (
            <p className="mt-1 text-muted">
              Panel: {a.panel.map((p) => `${p.name ?? p.externalName}${p.role === "CHAIR" ? " (chair)" : ""}`).join(", ")}
            </p>
          )}
          {a.score !== null && <p className="mt-1">Score: {a.score}</p>}
          {a.panelRemarks && <p className="mt-1">Panel remarks: {a.panelRemarks}</p>}
          {a.cancelledReason && <p className="mt-1 text-muted">Cancelled: {a.cancelledReason}</p>}
          {a.recordedByName && (
            <p className="mt-1 text-xs text-muted">
              Recorded by {a.recordedByName} on {formatDateTime(a.recordedAt)}
            </p>
          )}
        </li>
      ))}
    </ul>
  );
}
