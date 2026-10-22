"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { CalendarPlus, ChevronLeft } from "lucide-react";
import { ConfirmDialog, Dialog } from "@/components/dialog";
import { StaffSelect } from "@/components/people-picker";
import { ScheduleDialog, type ScheduleTarget } from "@/components/schedule-dialog";
import { StudentDetail } from "@/components/student-detail";
import { useToast } from "@/components/toast";
import { Banner, Button, Card, ErrorBox, Field, KeyValue, Select, StatusChip, TextField, TextareaField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, isoDay } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Milestone, StudentDetail as Detail } from "@/lib/types";

export default function AdminStudentPage() {
  const { id } = useParams<{ id: string }>();
  const { data, error, mutate } = useApi<Detail>(`/admin/students/${id}`);
  const milestones = useApi<Milestone[]>(`/students/${id}/milestones`);
  const [dialog, setDialog] = useState<"supervisor" | "extension" | "expel" | "clear" | "withdraw" | null>(null);
  const [schedule, setSchedule] = useState<ScheduleTarget | null>(null);
  const [cancelling, setCancelling] = useState<Milestone | null>(null);
  const [detailKey, setDetailKey] = useState(0);
  const toast = useToast();
  const refresh = () => {
    void mutate();
    void milestones.mutate();
    setDetailKey((k) => k + 1);
  };
  const decide = useAction(async (kind: string, reason: string) =>
    api.post<Detail>(`/admin/students/${id}/${kind === "expel" ? "expulsion/confirm" : kind === "clear" ? "expulsion/clear" : "withdraw"}`, { reason }),
  );
  const cancel = useAction(async (attemptId: string, reason: string) => api.post(`/admin/attempts/${attemptId}/cancel`, { reason }));
  const endCo = useAction(async (supervisionId: string) => api.del(`/admin/students/${id}/supervisions/${supervisionId}`).then(() => true));

  const s = data?.summary;
  const closed = s && ["EXPELLED", "COMPLETED", "WITHDRAWN"].includes(s.status);
  const next = milestones.data?.find((m) => m.status === "AVAILABLE" || m.status === "SCHEDULED");

  return (
    <>
      <Link href="/admin/students" className="mb-4 inline-flex items-center gap-1 text-sm text-primary hover:underline">
        <ChevronLeft size={15} /> Students
      </Link>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {s && (
        <div className="mb-6 grid grid-cols-1 gap-5 xl:grid-cols-3">
          <Card title="Programme and deadlines" actions={<StatusChip status={s.status} />}>
            <KeyValue
              items={[
                ["Programme", `${s.programmeName}, ${s.cohortName}`],
                ["Enrolled", formatDate(s.enrolledOn)],
                ["Checkpoint", `${formatDate(s.checkpointDueOn)} (${s.daysToCheckpoint} days)`],
                ["Programme end", `${formatDate(s.programmeEndOn)} (${s.daysToProgrammeEnd} days)`],
                ["Approved", `${s.approvedMilestones} of ${s.requiredMilestones}`],
                ["Sign-in", s.userActive ? "Active" : "Deactivated"],
              ]}
            />
            {s.statusReason && <p className="mt-3 text-sm text-muted">{s.statusReason}</p>}
            {!closed && (
              <div className="mt-4 flex flex-wrap gap-2">
                <Button size="sm" onClick={() => setDialog("extension")}>
                  Record extension
                </Button>
                {s.status === "FLAGGED_FOR_EXPULSION" ? (
                  <>
                    <Button size="sm" variant="danger" onClick={() => setDialog("expel")}>
                      Confirm expulsion
                    </Button>
                    <Button size="sm" onClick={() => setDialog("clear")}>
                      Clear flag
                    </Button>
                  </>
                ) : (
                  <Button size="sm" variant="ghost" onClick={() => setDialog("withdraw")}>
                    Record withdrawal
                  </Button>
                )}
              </div>
            )}
          </Card>
          <Card title="Supervision" actions={!closed && <Button size="sm" onClick={() => setDialog("supervisor")}>Change or add</Button>}>
            <ul className="flex flex-col gap-2 text-sm">
              {data.supervisions.map((sv) => (
                <li key={sv.id} className="flex items-center justify-between gap-2">
                  <span className={sv.endsOn ? "text-muted" : ""}>
                    <span className="font-medium">{sv.supervisorName}</span> · {sv.type === "PRIMARY" ? "primary" : "co-supervisor"}
                    <span className="block text-xs text-muted">
                      {formatDate(sv.startsOn)}
                      {sv.endsOn ? ` to ${formatDate(sv.endsOn)}` : " to now"}
                    </span>
                  </span>
                  {!sv.endsOn && sv.type === "CO" && (
                    <Button size="sm" variant="ghost" loading={endCo.pending} onClick={async () => (await endCo.run(sv.id)) && refresh()}>
                      End
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          </Card>
          <Card title="Extensions">
            {data.extensions.length === 0 ? (
              <p className="text-sm text-muted">None recorded.</p>
            ) : (
              <ul className="flex flex-col gap-2 text-sm">
                {data.extensions.map((e) => (
                  <li key={e.id}>
                    <span className="font-medium">{e.months} month(s)</span> · approved by {e.approvedBy}, {formatDate(e.approvedOn)}
                    <span className="block text-muted">{e.reason}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      )}
      {next && !closed && (
        <div className="mb-6">
          <Banner
            tone="info"
            title={`${next.label}: ${next.title}`}
            action={
              next.status === "AVAILABLE" ? (
                <Button size="sm" variant="primary" icon={<CalendarPlus size={14} />} onClick={() => setSchedule({ milestoneIds: [next.id], title: `Schedule ${next.label} for ${s?.name}` })}>
                  Schedule
                </Button>
              ) : next.currentAttempt ? (
                <div className="flex gap-2">
                  <Button size="sm" onClick={() => setSchedule({ milestoneIds: [next.id], title: `Reschedule ${next.label}`, reschedule: next.currentAttempt! })}>
                    Reschedule
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setCancelling(next)}>
                    Cancel
                  </Button>
                </div>
              ) : null
            }
          >
            {next.status === "AVAILABLE" ? "Open for scheduling." : `Scheduled for ${formatDate(next.currentAttempt?.scheduledStart)}.`}
          </Banner>
        </div>
      )}
      <StudentDetail key={detailKey} studentId={id} canComment />

      {schedule && <ScheduleDialog target={schedule} onClose={() => setSchedule(null)} onDone={() => { setSchedule(null); refresh(); }} />}
      {dialog === "supervisor" && <SupervisorDialog studentId={id} onClose={() => setDialog(null)} onDone={() => { setDialog(null); refresh(); }} />}
      {dialog === "extension" && <ExtensionDialog studentId={id} onClose={() => setDialog(null)} onDone={() => { setDialog(null); refresh(); }} />}
      {(["expel", "clear", "withdraw"] as const).map((kind) => (
        <ConfirmDialog
          key={kind}
          open={dialog === kind}
          onClose={() => setDialog(null)}
          title={kind === "expel" ? `Confirm the expulsion of ${s?.name}?` : kind === "clear" ? "Clear the expulsion flag?" : `Record that ${s?.name} has withdrawn?`}
          consequence={
            kind === "expel"
              ? "The status becomes EXPELLED. Sign-in is deactivated after 30 days; the record is kept. This decision is final and audited."
              : kind === "clear"
                ? "The student returns to ACTIVE, or AT_RISK if the checkpoint was missed. Record an extension first if new deadlines apply."
                : "The record closes. No further presentations can be scheduled."
          }
          reasonLabel="Written reason"
          confirmLabel={kind === "expel" ? "Confirm expulsion" : kind === "clear" ? "Clear flag" : "Record withdrawal"}
          tone={kind === "clear" ? "primary" : "danger"}
          pending={decide.pending}
          error={decide.error}
          onConfirm={async (reason) => {
            if (await decide.run(kind, reason)) {
              toast("Decision recorded");
              setDialog(null);
              refresh();
            }
          }}
        />
      ))}
      <ConfirmDialog
        open={!!cancelling}
        onClose={() => setCancelling(null)}
        title={`Cancel ${cancelling?.label}?`}
        consequence="The milestone reopens for scheduling. The student, supervisor and secretary are notified."
        reasonLabel="Reason"
        confirmLabel="Cancel presentation"
        tone="danger"
        pending={cancel.pending}
        error={cancel.error}
        onConfirm={async (reason) => {
          if (cancelling?.currentAttempt && (await cancel.run(cancelling.currentAttempt.id, reason))) {
            setCancelling(null);
            refresh();
          }
        }}
      />
    </>
  );
}

function SupervisorDialog({ studentId, onClose, onDone }: { studentId: string; onClose: () => void; onDone: () => void }) {
  const [supervisorUserId, setSupervisor] = useState("");
  const [type, setType] = useState("PRIMARY");
  const { run, pending, error } = useAction(async () => api.put(`/admin/students/${studentId}/supervisor`, { supervisorUserId, type }));
  return (
    <Dialog
      open
      onClose={onClose}
      title="Change or add a supervisor"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} disabled={!supervisorUserId} onClick={async () => (await run()) && onDone()}>
            Save
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Field label="Supervisor" hint="Must hold the Lecturer and Supervisor roles.">
          <StaffSelect role="SUPERVISOR" label="Supervisor" value={supervisorUserId} onChange={setSupervisor} />
        </Field>
        <Field label="Type">
          <Select aria-label="Type" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="PRIMARY">Primary supervisor (replaces the current one)</option>
            <option value="CO">Co-supervisor (added alongside)</option>
          </Select>
        </Field>
        <p className="text-xs text-muted">The change history is kept. The student and the old and new supervisors are notified.</p>
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}

function ExtensionDialog({ studentId, onClose, onDone }: { studentId: string; onClose: () => void; onDone: () => void }) {
  const [months, setMonths] = useState("3");
  const [reason, setReason] = useState("");
  const [approvedBy, setApprovedBy] = useState("");
  const [approvedOn, setApprovedOn] = useState(isoDay());
  const { run, pending, error } = useAction(async () =>
    api.post(`/admin/students/${studentId}/extensions`, { months: Number(months), reason, approvedBy, approvedOn }),
  );
  return (
    <Dialog
      open
      onClose={onClose}
      title="Record an approved suspension or extension"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} disabled={!reason.trim() || !approvedBy.trim()} onClick={async () => (await run()) && onDone()}>
            Record extension
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <TextField label="Months" type="number" min={1} max={24} value={months} onChange={(e) => setMonths(e.target.value)} required hint="Both the checkpoint and the programme end move by this many months." />
        <TextareaField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} required />
        <TextField label="Approved by" value={approvedBy} onChange={(e) => setApprovedBy(e.target.value)} required hint="For example, Board of Graduate Studies, meeting 12/2026." />
        <TextField label="Approved on" type="date" value={approvedOn} onChange={(e) => setApprovedOn(e.target.value)} required />
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
