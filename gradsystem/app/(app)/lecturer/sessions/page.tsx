"use client";

import Link from "next/link";
import { Suspense, useState } from "react";
import { Plus } from "lucide-react";
import { CoursePicker, useCourseParam } from "@/components/course-picker";
import { DataTable } from "@/components/data-table";
import { Dialog } from "@/components/dialog";
import { useToast } from "@/components/toast";
import { Button, Card, Empty, ErrorBox, Loading, PageHeader, StatusChip, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate, isoDay } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Course, Session } from "@/lib/types";

export default function SessionsPage() {
  return (
    <Suspense fallback={<Loading />}>
      <Sessions />
    </Suspense>
  );
}

function Sessions() {
  const courses = useApi<Course[]>("/lecturer/courses");
  const [course, setCourse] = useCourseParam(courses.data);
  const { data, error, mutate } = useApi<Session[]>(course ? `/lecturer/courses/${course.id}/sessions` : null);
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader
        title="Sessions"
        description="Create a class session, then mark attendance. Future sessions cannot be marked; you can edit a session for 7 days."
        actions={
          course && (
            <Button variant="primary" icon={<Plus size={16} />} onClick={() => setCreating(true)}>
              New session
            </Button>
          )
        }
      />
      <ErrorBox error={courses.error ?? error} />
      {courses.data?.length === 0 && (
        <Card>
          <Empty title="No courses assigned" />
        </Card>
      )}
      {courses.data && course && (
        <div className="mb-4">
          <CoursePicker courses={courses.data} value={course} onChange={setCourse} />
        </div>
      )}
      {course && !data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(s) => s.id}
          emptyTitle="No sessions yet"
          emptyText="Create the first session for this course."
          columns={[
            { key: "date", header: "Date", value: (s) => s.heldOn, sortable: true, render: (s) => <span className="font-medium">{formatDate(s.heldOn)}</span> },
            { key: "time", header: "Time", value: (s) => `${s.startsAt.slice(0, 5)} to ${s.endsAt.slice(0, 5)}` },
            { key: "topic", header: "Topic", value: (s) => s.topic ?? "" },
            {
              key: "attendance",
              header: "Attendance",
              value: (s) => s.present + s.late,
              render: (s) =>
                s.future ? (
                  <StatusChip status="SCHEDULED" label="Upcoming" />
                ) : s.attendanceTaken ? (
                  <span className="tabular text-sm">
                    {s.present} present · {s.late} late · {s.absent} absent{s.excused ? ` · ${s.excused} excused` : ""}
                  </span>
                ) : (
                  <StatusChip status="AT_RISK" label="Not taken" />
                ),
            },
            {
              key: "action",
              header: "",
              render: (s) =>
                !s.future && (
                  <Link className="text-sm font-medium text-primary hover:underline" href={`/lecturer/attendance/${s.id}`}>
                    {s.attendanceTaken ? (s.lecturerCanEdit ? "Edit attendance" : "View") : "Mark attendance"}
                  </Link>
                ),
            },
          ]}
        />
      )}
      {creating && course && (
        <NewSession
          course={course}
          onClose={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            void mutate();
          }}
        />
      )}
    </>
  );
}

function NewSession({ course, onClose, onDone }: { course: Course; onClose: () => void; onDone: () => void }) {
  const [heldOn, setHeldOn] = useState(isoDay());
  const [startsAt, setStartsAt] = useState("09:00");
  const [endsAt, setEndsAt] = useState("11:00");
  const [topic, setTopic] = useState("");
  const toast = useToast();
  const { run, pending, error } = useAction(async () => api.post<Session>(`/lecturer/courses/${course.id}/sessions`, { heldOn, startsAt, endsAt, topic }));
  return (
    <Dialog
      open
      onClose={onClose}
      title={`New session for ${course.code}`}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            onClick={async () => {
              if (await run()) {
                toast("Session created");
                onDone();
              }
            }}
          >
            Create session
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div className="sm:col-span-3">
          <TextField label="Date" type="date" value={heldOn} onChange={(e) => setHeldOn(e.target.value)} required />
        </div>
        <TextField label="Starts" type="time" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} required />
        <TextField label="Ends" type="time" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} required />
        <div className="sm:col-span-3">
          <TextField label="Topic" value={topic} onChange={(e) => setTopic(e.target.value)} />
        </div>
        <div className="sm:col-span-3">
          <ErrorBox error={error} />
        </div>
      </div>
    </Dialog>
  );
}
