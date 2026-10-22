"use client";

import Link from "next/link";
import { useState } from "react";
import { CalendarPlus } from "lucide-react";
import { DataTable } from "@/components/data-table";
import { ConfirmDialog } from "@/components/dialog";
import { OutcomeDialog } from "@/components/outcome-dialog";
import { PresentationAgenda } from "@/components/presentation-list";
import { ScheduleDialog, type ScheduleTarget } from "@/components/schedule-dialog";
import { Banner, Button, Card, ErrorBox, Loading, PageHeader, Select, StatusChip, Tabs } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, isoDay } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Attempt, Milestone, PresentationRow, Programme, Schedulable } from "@/lib/types";

function addDays(n: number) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + n);
  return isoDay(d);
}

/** Scheduling board: milestones open for scheduling (previous one approved), bulk sessions, and upcoming presentations. */
export default function SchedulingPage() {
  const [tab, setTab] = useState<"ready" | "upcoming" | "outcomes">("ready");
  const [programmeId, setProgrammeId] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [target, setTarget] = useState<ScheduleTarget | null>(null);
  const [outcome, setOutcome] = useState<PresentationRow | null>(null);
  const [cancelling, setCancelling] = useState<PresentationRow | null>(null);
  const programmes = useApi<Programme[]>("/reference/programmes");
  const ready = useApi<Schedulable[]>(`/admin/schedule/available${programmeId ? `?programmeId=${programmeId}` : ""}`);
  const upcoming = useApi<PresentationRow[]>(`/secretary/presentations?from=${isoDay()}&to=${addDays(60)}&state=SCHEDULED${programmeId ? `&programmeId=${programmeId}` : ""}`);
  const awaiting = useApi<PresentationRow[]>("/secretary/awaiting-outcome");
  const cancel = useAction(async (attemptId: string, reason: string) => api.post(`/admin/attempts/${attemptId}/cancel`, { reason }));

  const refresh = () => {
    void ready.mutate();
    void upcoming.mutate();
    void awaiting.mutate();
    setSelected([]);
  };

  const reschedule = async (row: PresentationRow) => {
    const ms = await api.get<Milestone[]>(`/students/${row.studentId}/milestones`);
    const attempt: Attempt | undefined = ms.find((m) => m.id === row.milestoneId)?.attempts.find((a) => a.id === row.attemptId);
    if (attempt) setTarget({ milestoneIds: [row.milestoneId], title: `Reschedule ${row.studentName}, Presentation ${row.sequenceNo}`, reschedule: attempt });
  };

  return (
    <>
      <PageHeader
        title="Scheduling"
        description="Only presentations whose previous milestone is approved can be scheduled. Student and venue clashes are refused; panel clashes show a warning."
        actions={
          <Select aria-label="Programme" value={programmeId} onChange={(e) => setProgrammeId(e.target.value)} className="w-48">
            <option value="">All programmes</option>
            {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
          </Select>
        }
      />
      <Tabs
        value={tab}
        onChange={setTab}
        items={[
          { value: "ready", label: `Ready to schedule${ready.data ? ` (${ready.data.length})` : ""}` },
          { value: "upcoming", label: `Upcoming${upcoming.data ? ` (${upcoming.data.length})` : ""}` },
          { value: "outcomes", label: `Outcome not recorded${awaiting.data ? ` (${awaiting.data.length})` : ""}` },
        ]}
      />
      {tab === "ready" && (
        <>
          <ErrorBox error={ready.error} />
          {!ready.data && <Loading />}
          {ready.data && (
            <div className="flex flex-col gap-3">
              {selected.length > 0 && (
                <Banner
                  tone="info"
                  title={`${selected.length} selected`}
                  action={
                    <Button variant="primary" size="sm" icon={<CalendarPlus size={14} />} onClick={() => setTarget({ milestoneIds: selected, title: `Schedule a session for ${selected.length} students` })}>
                      Schedule as one session
                    </Button>
                  }
                >
                  Slots are assigned in the order shown. Each student still gets an individual record.
                </Banner>
              )}
              <DataTable
                rows={ready.data}
                rowKey={(r) => r.milestoneId}
                searchText={(r) => `${r.studentName} ${r.studentNumber} ${r.title}`}
                exportName="ready-to-schedule"
                emptyTitle="Nothing waiting to be scheduled"
                columns={[
                  {
                    key: "select",
                    header: "",
                    render: (r) => (
                      <input
                        type="checkbox"
                        aria-label={`Select ${r.studentName}`}
                        className="h-4 w-4 accent-[var(--primary)]"
                        checked={selected.includes(r.milestoneId)}
                        onChange={(e) => setSelected(e.target.checked ? [...selected, r.milestoneId] : selected.filter((x) => x !== r.milestoneId))}
                      />
                    ),
                  },
                  { key: "student", header: "Student", sortable: true, value: (r) => r.studentName, render: (r) => <Link href={`/admin/students/${r.studentId}`} className="hover:underline"><p className="font-medium">{r.studentName}</p><p className="text-xs text-muted">{r.studentNumber} · {r.programmeCode} {r.cohortName}</p></Link> },
                  { key: "milestone", header: "Presentation", sortable: true, value: (r) => r.sequenceNo, render: (r) => <div><p>Presentation {r.sequenceNo}: {r.title}</p>{r.nextAttemptNo > 1 && <p className="text-xs text-warn">Attempt {r.nextAttemptNo}</p>}</div> },
                  { key: "waiting", header: "Open for", sortable: true, value: (r) => r.daysAvailable, render: (r) => <span className="tabular">{r.daysAvailable} days</span> },
                  { key: "status", header: "Student status", value: (r) => r.studentStatus, render: (r) => <StatusChip status={r.studentStatus} /> },
                  { key: "action", header: "", render: (r) => <Button size="sm" variant="primary" onClick={() => setTarget({ milestoneIds: [r.milestoneId], title: `Schedule Presentation ${r.sequenceNo} for ${r.studentName}` })}>Schedule</Button> },
                ]}
              />
            </div>
          )}
        </>
      )}
      {tab === "upcoming" && (
        <Card>
          {!upcoming.data ? (
            <Loading />
          ) : (
            <PresentationAgenda
              rows={upcoming.data}
              empty="Nothing scheduled in the next 60 days."
              action={(r) => (
                <>
                  <Button size="sm" onClick={() => reschedule(r)}>Reschedule</Button>
                  <Button size="sm" variant="ghost" onClick={() => setCancelling(r)}>Cancel</Button>
                </>
              )}
            />
          )}
        </Card>
      )}
      {tab === "outcomes" && (
        <Card description="The secretary normally records outcomes. Admins can record or correct them at any time.">
          {!awaiting.data ? <Loading /> : <PresentationAgenda rows={awaiting.data} empty="All outcomes recorded." action={(r) => <Button size="sm" variant="primary" onClick={() => setOutcome(r)}>Record</Button>} />}
        </Card>
      )}
      {target && <ScheduleDialog target={target} onClose={() => setTarget(null)} onDone={() => { setTarget(null); refresh(); }} />}
      {outcome && <OutcomeDialog row={outcome} onClose={() => setOutcome(null)} onDone={() => { setOutcome(null); refresh(); }} />}
      <ConfirmDialog
        open={!!cancelling}
        onClose={() => setCancelling(null)}
        title={`Cancel ${cancelling?.studentName}'s presentation on ${formatDate(cancelling?.scheduledStart)}?`}
        consequence="The milestone reopens for scheduling. The student, supervisor and secretary are notified."
        reasonLabel="Reason"
        confirmLabel="Cancel presentation"
        tone="danger"
        pending={cancel.pending}
        error={cancel.error}
        onConfirm={async (reason) => {
          if (cancelling && (await cancel.run(cancelling.attemptId, reason))) {
            setCancelling(null);
            refresh();
          }
        }}
      />
    </>
  );
}
