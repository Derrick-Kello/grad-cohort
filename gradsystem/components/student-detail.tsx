"use client";

import { useState } from "react";
import { mutate } from "swr";
import { MessageSquare } from "lucide-react";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Achievement, Milestone, Publication, StudentAttendance, ThesisOverview } from "@/lib/types";
import { AchievementTimeline } from "./achievements";
import { AttendanceTable } from "./attendance-table";
import { Dialog } from "./dialog";
import { CommentThread, EvidenceFiles } from "./evidence-list";
import { AttemptList } from "./milestones";
import { ProgressSummary, type StudentDashboard } from "./progress-summary";
import { ThesisVersion } from "./thesis";
import { useToast } from "./toast";
import { Button, Card, Empty, ErrorBox, Loading, StatusChip, Tabs, TextareaField } from "./ui";

type Detail = {
  progress: StudentDashboard;
  publications: Publication[];
  thesis: ThesisOverview;
  attendance: StudentAttendance[];
  achievements: Achievement[];
};

/** Supervisor (and staff) view of one student: milestones, evidence, publications, thesis, attendance (section 18). */
export function StudentDetail({ studentId, canComment }: { studentId: string; canComment: boolean }) {
  const { data, error, mutate: reload } = useApi<Detail>(`/supervisor/students/${studentId}`);
  const [tab, setTab] = useState<"overview" | "milestones" | "publications" | "thesis" | "attendance" | "achievements">("overview");
  const [commenting, setCommenting] = useState<{ type: "MILESTONE" | "EVIDENCE" | "THESIS"; id: string; label: string } | null>(null);

  if (error) return <ErrorBox error={error} onRetry={() => reload()} />;
  if (!data) return <Loading />;
  const s = data.progress.student;
  return (
    <>
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-semibold">{s.name}</h1>
        <StatusChip status={s.status} />
        <p className="w-full text-sm text-muted">
          {s.number} · {s.programmeName} · {s.cohortName} · enrolled {formatDate(s.enrolledOn)} · {s.email}
        </p>
      </div>
      <Tabs
        value={tab}
        onChange={setTab}
        items={[
          { value: "overview", label: "Overview" },
          { value: "milestones", label: "Milestones and evidence" },
          { value: "publications", label: `Publications (${data.publications.length})` },
          { value: "thesis", label: "Thesis" },
          { value: "attendance", label: "Attendance" },
          { value: "achievements", label: "Achievements" },
        ]}
      />
      {tab === "overview" && <ProgressSummary data={data.progress} audience="staff" />}
      {tab === "milestones" && (
        <div className="flex flex-col gap-4">
          {data.progress.milestones.map((m: Milestone) => (
            <Card
              key={m.id}
              title={`${m.label}: ${m.title}`}
              actions={
                <>
                  <StatusChip status={m.status} />
                  {canComment && m.status !== "LOCKED" && (
                    <Button size="sm" icon={<MessageSquare size={14} />} onClick={() => setCommenting({ type: "MILESTONE", id: m.id, label: m.label })}>
                      Comment
                    </Button>
                  )}
                </>
              }
            >
              {m.status === "LOCKED" ? (
                <p className="text-sm text-muted">{m.lockedReason}</p>
              ) : (
                <div className="flex flex-col gap-3">
                  {m.attempts.length > 0 && <AttemptList attempts={m.attempts} />}
                  <EvidenceFiles milestoneId={m.id} readOnly />
                  <CommentThread targetType="MILESTONE" targetId={m.id} />
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
      {tab === "publications" && (
        <Card padded={false}>
          {data.publications.length === 0 ? (
            <Empty title="No submitted publications" />
          ) : (
            <ul className="divide-y divide-border">
              {data.publications.map((p) => (
                <li key={p.id} className="flex flex-wrap items-start justify-between gap-2 px-5 py-3 text-sm">
                  <div className="min-w-0">
                    <p className="font-medium">{p.title}</p>
                    <p className="text-muted">
                      {p.venueName} {p.year ? `(${p.year})` : ""} {p.doi ? `· ${p.doi}` : ""}
                    </p>
                  </div>
                  <StatusChip status={p.reviewStatus} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      )}
      {tab === "thesis" && (
        <div className="flex flex-col gap-4">
          {data.thesis.locked && <Card><p className="text-sm text-muted">{data.thesis.lockedReason}</p></Card>}
          {!data.thesis.locked && data.thesis.versions.length === 0 && <Card><Empty title="No thesis submitted yet" /></Card>}
          {data.thesis.versions.map((t, i) => (
            <ThesisVersion
              key={t.id}
              t={t}
              defaultOpen={i === 0}
              actions={
                canComment ? (
                  <div>
                    <Button size="sm" icon={<MessageSquare size={14} />} onClick={() => setCommenting({ type: "THESIS", id: t.id, label: `thesis version ${t.versionNo}` })}>
                      Comment on this version
                    </Button>
                  </div>
                ) : null
              }
            />
          ))}
        </div>
      )}
      {tab === "attendance" && <AttendanceTable rows={data.attendance} />}
      {tab === "achievements" && (
        <Card>
          <AchievementTimeline items={data.achievements} />
        </Card>
      )}
      {commenting && (
        <CommentDialog
          target={commenting}
          onClose={() => setCommenting(null)}
          onDone={() => {
            void mutate(`/comments?targetType=${commenting.type}&targetId=${commenting.id}`);
            setCommenting(null);
          }}
        />
      )}
    </>
  );
}

function CommentDialog({ target, onClose, onDone }: { target: { type: string; id: string; label: string }; onClose: () => void; onDone: () => void }) {
  const [body, setBody] = useState("");
  const toast = useToast();
  const { run, pending, error } = useAction(async () => api.post("/comments", { targetType: target.type, targetId: target.id, body }));
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Comment on ${target.label}`}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            disabled={!body.trim()}
            onClick={async () => {
              if (await run()) {
                toast("Comment sent to the student");
                onDone();
              }
            }}
          >
            Post comment
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <TextareaField label="Comment" value={body} onChange={(e) => setBody(e.target.value)} rows={5} hint="The student and the graduate school can read it." />
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
