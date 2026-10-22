"use client";

import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { formatDate, formatDateTime } from "@/lib/format";
import type { Milestone, PresentationRow, StudentAttendance, StudentSummary } from "@/lib/types";
import { MilestoneTimeline } from "./milestones";
import { Banner, Card, ProgressBar, Stat, StatusChip, statusTone } from "./ui";

export type StudentDashboard = {
  student: StudentSummary;
  statusLabel: string;
  nextAction: { text: string; link: string };
  milestones: Milestone[];
  nextPresentation: PresentationRow | null;
  approvedPublications: number;
  thesisStatus: string | null;
  thesisUnlocked: boolean;
  attendanceWarnings: StudentAttendance[];
};

/** Progress at a glance (FR-DSH-01), shared by the student dashboard and supervisor student view. */
export function ProgressSummary({ data, audience }: { data: StudentDashboard; audience: "student" | "staff" }) {
  const s = data.student;
  const tone = statusTone(s.status);
  const checkpointDone = s.approvedMilestones >= 4;
  return (
    <div className="flex flex-col gap-6">
      {s.status !== "COMPLETED" && (
        <Banner
          tone={tone === "ok" ? "info" : tone}
          title={audience === "student" ? "Your next step" : "Next step"}
          action={
            audience === "student" && data.nextAction.link ? (
              <Link href={data.nextAction.link} className="inline-flex items-center gap-1 text-sm font-semibold underline-offset-2 hover:underline">
                Open <ArrowRight size={14} aria-hidden />
              </Link>
            ) : null
          }
        >
          {data.nextAction.text}
        </Banner>
      )}
      {s.statusReason && s.status !== "ACTIVE" && (
        <Banner tone={tone} title={`Status: ${data.statusLabel}`}>
          {s.statusReason}
        </Banner>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat label="Semester" value={`${s.currentSemester} of 4`} hint={`${s.programmeName}, ${s.cohortName}`} />
        <div className="rounded-[var(--radius)] border border-border bg-surface p-4 shadow-[var(--shadow)]">
          <p className="text-xs font-medium tracking-wide text-muted uppercase">Presentations approved</p>
          <p className="tabular mt-1.5 text-2xl font-semibold">
            {s.approvedMilestones} <span className="text-base font-normal text-muted">of {s.requiredMilestones}</span>
          </p>
          <div className="mt-2">
            <ProgressBar value={s.approvedMilestones} max={s.requiredMilestones} tone={tone === "bad" ? "bad" : tone === "warn" ? "warn" : "ok"} label="Presentations approved" />
          </div>
        </div>
        <Stat
          label="18-month checkpoint"
          value={checkpointDone ? "Met" : s.daysToCheckpoint >= 0 ? `${s.daysToCheckpoint} days` : "Passed"}
          tone={checkpointDone ? "ok" : s.daysToCheckpoint < 0 ? "bad" : s.daysToCheckpoint < 90 ? "warn" : undefined}
          hint={`${formatDate(s.checkpointDueOn)}: 4 presentations approved`}
        />
        <Stat
          label="Programme end"
          value={s.daysToProgrammeEnd >= 0 ? `${s.daysToProgrammeEnd} days` : "Passed"}
          tone={s.daysToProgrammeEnd < 0 ? "bad" : s.daysToProgrammeEnd < 120 ? "warn" : undefined}
          hint={`${formatDate(s.programmeEndOn)}: all ${s.requiredMilestones} approved`}
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card title="Milestones" className="lg:col-span-2" actions={<StatusChip status={s.status} label={data.statusLabel} />}>
          <MilestoneTimeline milestones={data.milestones} compact />
        </Card>
        <div className="flex flex-col gap-6">
          <Card title="Next presentation">
            {data.nextPresentation ? (
              <div className="text-sm">
                <p className="font-medium">
                  Presentation {data.nextPresentation.sequenceNo}: {data.nextPresentation.milestoneTitle}
                </p>
                <p className="mt-1 text-muted">
                  {formatDateTime(data.nextPresentation.scheduledStart)}
                </p>
                <p className="text-muted">{data.nextPresentation.venueName ?? "Online"}</p>
              </div>
            ) : (
              <p className="text-sm text-muted">Nothing scheduled.</p>
            )}
          </Card>
          <Card title="Research record">
            <ul className="flex flex-col gap-2 text-sm">
              <li className="flex justify-between gap-2">
                <span className="text-muted">Approved publications</span>
                <span className="tabular font-medium">{data.approvedPublications}</span>
              </li>
              <li className="flex justify-between gap-2">
                <span className="text-muted">Thesis</span>
                <span>{data.thesisStatus ? <StatusChip status={data.thesisStatus} /> : data.thesisUnlocked ? "Open for submission" : <StatusChip status="LOCKED" />}</span>
              </li>
              <li className="flex justify-between gap-2">
                <span className="text-muted">Supervisor</span>
                <span className="text-right font-medium">{s.supervisorName ?? "Not assigned"}</span>
              </li>
              {s.coSupervisorName && (
                <li className="flex justify-between gap-2">
                  <span className="text-muted">Co-supervisor</span>
                  <span className="text-right">{s.coSupervisorName}</span>
                </li>
              )}
            </ul>
          </Card>
          {data.attendanceWarnings.length > 0 && (
            <Card title="Attendance warnings">
              <ul className="flex flex-col gap-2 text-sm">
                {data.attendanceWarnings.map((a) => (
                  <li key={a.courseId} className="flex items-center justify-between gap-2">
                    <span>
                      {a.code} <span className="text-muted">{a.attendancePct}%</span>
                    </span>
                    <StatusChip status={a.status} />
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
