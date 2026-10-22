"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ConfirmDialog } from "@/components/dialog";
import { StudentTable } from "@/components/student-table";
import { useToast } from "@/components/toast";
import { Button, Card, Empty, ErrorBox, KeyValue, Loading, PageHeader, Select, StatusChip, Tabs } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, formatDateTime } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Cohort, Comment, Extension, Milestone, Programme, StudentSummary } from "@/lib/types";

type Case = { student: StudentSummary; milestones: Milestone[]; extensions: Extension[]; supervisorComments: Comment[] };

/** At-risk report (FR-DLN-11) and the expulsion review queue (FR-DLN-07, FR-DLN-08). */
export default function AtRiskPage() {
  const [tab, setTab] = useState<"queue" | "risk">("queue");
  const queue = useApi<Case[]>("/admin/expulsion-queue");
  return (
    <>
      <PageHeader
        title="At-risk and expulsion"
        description="The system flags students at programme end when milestones are outstanding. It never expels anyone: an admin reviews each case and confirms or clears it with a written reason."
      />
      <Tabs value={tab} onChange={setTab} items={[{ value: "queue", label: `Expulsion review${queue.data ? ` (${queue.data.length})` : ""}` }, { value: "risk", label: "At-risk report" }]} />
      {tab === "queue" ? <ExpulsionQueue cases={queue.data} error={queue.error} reload={() => queue.mutate()} /> : <RiskReport />}
    </>
  );
}

function ExpulsionQueue({ cases, error, reload }: { cases?: Case[]; error: unknown; reload: () => void }) {
  const [decision, setDecision] = useState<{ c: Case; kind: "confirm" | "clear" } | null>(null);
  const toast = useToast();
  const decide = useAction(async (id: string, kind: string, reason: string) => api.post(`/admin/students/${id}/expulsion/${kind}`, { reason }));
  if (error) return <ErrorBox error={error as Error} onRetry={reload} />;
  if (!cases) return <Loading />;
  if (cases.length === 0) return <Card><Empty title="No flagged students">Students flagged at programme end appear here for review.</Empty></Card>;
  return (
    <div className="flex flex-col gap-5">
      {cases.map((c) => {
        const s = c.student;
        return (
          <Card
            key={s.id}
            title={<Link href={`/admin/students/${s.id}`} className="hover:underline">{s.name} ({s.number})</Link>}
            description={`${s.programmeName} · ${s.cohortName} · flagged ${formatDateTime(s.flaggedAt)}`}
            actions={
              <>
                <Button size="sm" onClick={() => setDecision({ c, kind: "clear" })}>Clear flag</Button>
                <Button size="sm" variant="danger" onClick={() => setDecision({ c, kind: "confirm" })}>Confirm expulsion</Button>
              </>
            }
          >
            <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
              <div className="flex flex-col gap-3">
                <KeyValue
                  items={[
                    ["Reason flagged", s.statusReason],
                    ["Approved", `${s.approvedMilestones} of ${s.requiredMilestones}`],
                    ["Programme end", formatDate(s.programmeEndOn)],
                    ["Supervisor", s.supervisorName],
                  ]}
                />
                <div>
                  <p className="mb-1 text-xs font-medium text-muted">Extensions</p>
                  {c.extensions.length === 0 ? <p className="text-sm text-muted">None recorded.</p> : (
                    <ul className="text-sm">{c.extensions.map((e) => <li key={e.id}>{e.months} month(s), {formatDate(e.approvedOn)}: {e.reason}</li>)}</ul>
                  )}
                </div>
                <div>
                  <p className="mb-1 text-xs font-medium text-muted">Supervisor comments</p>
                  {c.supervisorComments.length === 0 ? <p className="text-sm text-muted">None.</p> : (
                    <ul className="flex flex-col gap-1 text-sm">{c.supervisorComments.map((cm) => <li key={cm.id} className="rounded bg-surface-2 px-2 py-1">{cm.body} <span className="text-xs text-muted">({cm.authorName}, {formatDate(cm.createdAt)})</span></li>)}</ul>
                  )}
                </div>
              </div>
              <div>
                <p className="mb-2 text-xs font-medium text-muted">Milestones and attempts</p>
                <ul className="flex flex-col divide-y divide-border rounded-lg border border-border text-sm">
                  {c.milestones.map((m) => (
                    <li key={m.id} className="flex items-center justify-between gap-2 px-3 py-2">
                      <span>
                        {m.label}: {m.title}
                        <span className="block text-xs text-muted">{m.attempts.length} attempt(s){m.failedAttempts ? `, ${m.failedAttempts} not passed` : ""}</span>
                      </span>
                      <StatusChip status={m.status} />
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </Card>
        );
      })}
      <ConfirmDialog
        open={!!decision}
        onClose={() => setDecision(null)}
        title={decision?.kind === "confirm" ? `Confirm the expulsion of ${decision?.c.student.name}?` : `Clear the flag on ${decision?.c.student.name}?`}
        consequence={
          decision?.kind === "confirm"
            ? "The status becomes EXPELLED. Sign-in is deactivated after 30 days and the record is kept. The student and supervisor are notified."
            : "The student returns to ACTIVE, or AT_RISK if the checkpoint was missed. Record an extension on the student page first if new deadlines apply."
        }
        reasonLabel="Written reason"
        confirmLabel={decision?.kind === "confirm" ? "Confirm expulsion" : "Clear flag"}
        tone={decision?.kind === "confirm" ? "danger" : "primary"}
        pending={decide.pending}
        error={decide.error}
        onConfirm={async (reason) => {
          if (decision && (await decide.run(decision.c.student.id, decision.kind, reason))) {
            toast("Decision recorded");
            setDecision(null);
            reload();
          }
        }}
      />
    </div>
  );
}

function RiskReport() {
  const router = useRouter();
  const [programmeId, setProgrammeId] = useState("");
  const [cohortId, setCohortId] = useState("");
  const programmes = useApi<Programme[]>("/reference/programmes");
  const cohorts = useApi<Cohort[]>(programmeId ? `/reference/cohorts?programmeId=${programmeId}` : "/reference/cohorts");
  const q = new URLSearchParams();
  if (programmeId) q.set("programmeId", programmeId);
  if (cohortId) q.set("cohortId", cohortId);
  const { data, error } = useApi<StudentSummary[]>(`/admin/at-risk?${q}`);
  return (
    <>
      <div className="mb-4 grid grid-cols-1 gap-2 sm:max-w-lg sm:grid-cols-2">
        <Select aria-label="Programme" value={programmeId} onChange={(e) => { setProgrammeId(e.target.value); setCohortId(""); }}>
          <option value="">All programmes</option>
          {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
        </Select>
        <Select aria-label="Cohort" value={cohortId} onChange={(e) => setCohortId(e.target.value)}>
          <option value="">All cohorts</option>
          {cohorts.data?.map((c) => <option key={c.id} value={c.id}>{c.programmeCode} {c.name}</option>)}
        </Select>
      </div>
      <ErrorBox error={error} />
      {!data && !error && <Loading />}
      {data && <StudentTable rows={data} onOpen={(s) => router.push(`/admin/students/${s.id}`)} exportName="at-risk-students" />}
    </>
  );
}
