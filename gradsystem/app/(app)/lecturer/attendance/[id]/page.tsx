"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import { CheckCheck, ChevronLeft } from "lucide-react";
import { useToast } from "@/components/toast";
import { Banner, Button, Card, ErrorBox, Loading, PageHeader, TextareaField, cx } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDate } from "@/lib/format";
import { useAction, useApi, useEditable } from "@/lib/hooks";
import type { Roster } from "@/lib/types";

const STATUSES = ["PRESENT", "LATE", "ABSENT", "EXCUSED"] as const;
const LABEL: Record<string, string> = { PRESENT: "Present", LATE: "Late", ABSENT: "Absent", EXCUSED: "Excused" };
const ACTIVE: Record<string, string> = {
  PRESENT: "bg-ok text-white dark:text-[#0b1a12] border-ok",
  LATE: "bg-warn-bg text-warn border-warn",
  ABSENT: "bg-bad-bg text-bad border-bad",
  EXCUSED: "bg-info-bg text-info border-info",
};

/** Roster: everyone defaults to ABSENT, "Mark all present", one save for the whole session (FR-ATT-03). */
export default function RosterPage() {
  const { id } = useParams<{ id: string }>();
  const { view } = useAuth();
  const { data, error, mutate } = useApi<Roster>(`/lecturer/sessions/${id}/attendance`);
  const [marks, setMarks] = useEditable<Roster, Record<string, string>>(data, (d) => Object.fromEntries(d.entries.map((e) => [e.studentId, e.status])), {});
  const [reason, setReason] = useState("");
  const toast = useToast();

  const save = useAction(async () =>
    api.put<Roster>(`/lecturer/sessions/${id}/attendance`, {
      entries: Object.entries(marks).map(([studentId, status]) => ({ studentId, status })),
      reason: reason || undefined,
    }),
  );

  if (error) return <ErrorBox error={error} onRetry={() => mutate()} />;
  if (!data) return <Loading />;
  const s = data.session;
  const needsReason = data.editable && !s.lecturerCanEdit;
  const counts = STATUSES.map((st) => [st, Object.values(marks).filter((m) => m === st).length] as const);
  const back = view === "admin" ? "/admin/courses" : `/lecturer/sessions?course=${s.courseId}`;

  return (
    <>
      <Link href={back} className="mb-4 inline-flex items-center gap-1 text-sm text-primary hover:underline">
        <ChevronLeft size={15} /> Back
      </Link>
      <PageHeader
        title={`${data.courseCode}: ${formatDate(s.heldOn)}`}
        description={`${data.courseTitle} · ${s.startsAt.slice(0, 5)} to ${s.endsAt.slice(0, 5)}${s.topic ? ` · ${s.topic}` : ""}`}
        actions={
          data.editable && (
            <Button icon={<CheckCheck size={16} />} onClick={() => setMarks(Object.fromEntries(data.entries.map((e) => [e.studentId, "PRESENT"])))}>
              Mark all present
            </Button>
          )
        }
      />
      {data.editNote && (
        <div className="mb-4">
          <Banner tone={data.editable ? "warn" : "info"}>{data.editNote}</Banner>
        </div>
      )}
      <Card padded={false}>
        <ul className="divide-y divide-border">
          {data.entries.map((e) => (
            <li key={e.studentId} className="flex flex-col gap-2 px-5 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p className="text-sm font-medium">{e.name}</p>
                <p className="text-xs text-muted">
                  {e.number} · {e.programmeCode}
                </p>
              </div>
              <div role="radiogroup" aria-label={`Attendance for ${e.name}`} className="flex flex-wrap gap-1.5">
                {STATUSES.map((st) => (
                  <button
                    key={st}
                    type="button"
                    role="radio"
                    aria-checked={marks[e.studentId] === st}
                    disabled={!data.editable}
                    onClick={() => setMarks((m) => ({ ...m, [e.studentId]: st }))}
                    className={cx(
                      "h-8 rounded-full border px-3 text-xs font-medium disabled:cursor-not-allowed",
                      marks[e.studentId] === st ? ACTIVE[st] : "border-border-strong text-muted hover:bg-surface-2",
                    )}
                  >
                    {LABEL[st]}
                  </button>
                ))}
              </div>
            </li>
          ))}
        </ul>
      </Card>
      {data.editable && (
        <div className="sticky bottom-20 mt-4 flex flex-col gap-3 rounded-[var(--radius)] border border-border bg-surface p-4 shadow-[var(--shadow-lg)] sm:bottom-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="tabular text-sm text-muted">{counts.map(([st, n]) => `${n} ${LABEL[st].toLowerCase()}`).join(" · ")}</p>
          {needsReason && (
            <div className="sm:w-80">
              <TextareaField label="Reason for the change" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} required />
            </div>
          )}
          <div className="flex flex-col gap-2">
            <ErrorBox error={save.error} />
            <Button
              variant="primary"
              loading={save.pending}
              disabled={needsReason && !reason.trim()}
              onClick={async () => {
                const saved = await save.run();
                if (saved) {
                  toast("Attendance saved");
                  void mutate(saved, false);
                }
              }}
            >
              Save attendance
            </Button>
          </div>
        </div>
      )}
    </>
  );
}
