"use client";

import Link from "next/link";
import { useState } from "react";
import { ChevronLeft, Plus } from "lucide-react";
import { DataTable } from "@/components/data-table";
import { Dialog } from "@/components/dialog";
import { EligibilityPanel } from "@/components/eligibility-panel";
import { FileInput } from "@/components/file-input";
import { useToast } from "@/components/toast";
import { Button, Checkbox, ErrorBox, Field, Input, Loading, PageHeader, Select, SelectField, StatusChip, Tabs, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Course, Page, Person, Semester, Session, StudentSummary, User } from "@/lib/types";

export default function AdminCoursesPage() {
  const semesters = useApi<Semester[]>("/reference/semesters");
  const [semesterId, setSemesterId] = useState("");
  const { data, error, mutate } = useApi<Course[]>(`/admin/courses${semesterId ? `?semesterId=${semesterId}` : ""}`);
  const [editing, setEditing] = useState<Course | "new" | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  const open = data?.find((c) => c.id === openId);

  if (open) return <CourseView course={open} onBack={() => setOpenId(null)} onEdit={() => setEditing(open)} onChanged={() => mutate()} editing={editing} setEditing={setEditing} />;
  return (
    <>
      <PageHeader
        title="Courses"
        description="Courses per semester, their lecturers and enrolled students. Attendance below the threshold makes a student ineligible for that course's exam."
        actions={<Button variant="primary" icon={<Plus size={16} />} onClick={() => setEditing("new")}>Add course</Button>}
      />
      <div className="mb-4">
        <Select aria-label="Semester" value={semesterId} onChange={(e) => setSemesterId(e.target.value)} className="w-64">
          <option value="">All semesters</option>
          {semesters.data?.map((s) => <option key={s.id} value={s.id}>{s.label}{s.current ? " (current)" : ""}</option>)}
        </Select>
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(c) => c.id}
          onRowClick={(c) => setOpenId(c.id)}
          searchText={(c) => `${c.code} ${c.title} ${c.lecturers.map((l) => l.name).join(" ")}`}
          exportName="courses"
          emptyTitle="No courses"
          columns={[
            { key: "code", header: "Code", sortable: true, value: (c) => c.code, className: "font-medium" },
            { key: "title", header: "Title", sortable: true, value: (c) => c.title },
            { key: "semester", header: "Semester", sortable: true, value: (c) => c.semesterLabel },
            { key: "lecturers", header: "Lecturers", value: (c) => c.lecturers.map((l) => l.name).join(", ") },
            { key: "students", header: "Students", sortable: true, value: (c) => c.studentCount, className: "tabular" },
            { key: "held", header: "Sessions", sortable: true, value: (c) => c.sessionsHeld, className: "tabular" },
          ]}
        />
      )}
      {editing && <CourseDialog course={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} onDone={() => { setEditing(null); void mutate(); }} />}
    </>
  );
}

function CourseView({ course, onBack, onEdit, onChanged, editing, setEditing }: { course: Course; onBack: () => void; onEdit: () => void; onChanged: () => void; editing: Course | "new" | null; setEditing: (c: Course | "new" | null) => void }) {
  const [tab, setTab] = useState<"students" | "sessions" | "eligibility">("students");
  return (
    <>
      <Button size="sm" className="mb-4" icon={<ChevronLeft size={14} />} onClick={onBack}>
        All courses
      </Button>
      <PageHeader title={`${course.code} ${course.title}`} description={`${course.semesterLabel} · ${course.creditHours} credit hours · ${course.lecturers.map((l) => l.name).join(", ") || "No lecturer"}`} actions={<Button onClick={onEdit}>Edit course</Button>} />
      <Tabs value={tab} onChange={setTab} items={[{ value: "students", label: `Students (${course.studentCount})` }, { value: "sessions", label: "Sessions" }, { value: "eligibility", label: "Eligibility" }]} />
      {tab === "students" && <Enrolments course={course} onChanged={onChanged} />}
      {tab === "sessions" && <CourseSessions course={course} />}
      {tab === "eligibility" && <EligibilityPanel courseId={course.id} courseCode={course.code} canGrantException />}
      {editing && editing !== "new" && <CourseDialog course={editing} onClose={() => setEditing(null)} onDone={() => { setEditing(null); onChanged(); }} />}
    </>
  );
}

function Enrolments({ course, onChanged }: { course: Course; onChanged: () => void }) {
  const { data, mutate } = useApi<Person[]>(`/admin/courses/${course.id}/enrolments`);
  const [q, setQ] = useState("");
  const search = useApi<Page<StudentSummary>>(q.length >= 2 ? `/admin/students?q=${encodeURIComponent(q)}&size=10` : null);
  const [importing, setImporting] = useState(false);
  const toast = useToast();
  const refresh = () => {
    void mutate();
    onChanged();
  };
  const add = async (s: StudentSummary) => {
    await api.post(`/admin/courses/${course.id}/enrolments`, { studentIds: [s.id] });
    toast(`${s.name} enrolled`);
    setQ("");
    refresh();
  };
  const remove = async (p: Person) => {
    await api.del(`/admin/courses/${course.id}/enrolments/${p.id}`);
    refresh();
  };
  const enrolled = new Set(data?.map((p) => p.id));
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-start">
        <div className="relative w-full sm:max-w-sm">
          <Input aria-label="Add a student" placeholder="Add a student: type a name or number" value={q} onChange={(e) => setQ(e.target.value)} />
          {search.data && q.length >= 2 && (
            <ul className="absolute z-10 mt-1 w-full overflow-hidden rounded-lg border border-border bg-surface shadow-[var(--shadow-lg)]">
              {search.data.content.filter((s) => !enrolled.has(s.id)).map((s) => (
                <li key={s.id}>
                  <button type="button" onClick={() => add(s)} className="w-full px-3 py-2 text-left text-sm hover:bg-surface-2">
                    {s.name} <span className="text-muted">({s.number}, {s.programmeCode})</span>
                  </button>
                </li>
              ))}
              {search.data.content.filter((s) => !enrolled.has(s.id)).length === 0 && <li className="px-3 py-2 text-sm text-muted">No matching students to add.</li>}
            </ul>
          )}
        </div>
        <Button onClick={() => setImporting(true)}>Import student numbers (CSV)</Button>
      </div>
      {!data ? (
        <Loading />
      ) : (
        <DataTable
          rows={data}
          rowKey={(p) => p.id}
          exportName={`${course.code}-students`}
          emptyTitle="No students enrolled"
          columns={[
            { key: "name", header: "Student", sortable: true, value: (p) => p.name, className: "font-medium" },
            { key: "number", header: "Number", sortable: true, value: (p) => p.number },
            { key: "remove", header: "", render: (p) => <Button size="sm" variant="ghost" onClick={() => remove(p)}>Remove</Button> },
          ]}
        />
      )}
      {importing && <EnrolmentImport course={course} onClose={() => setImporting(false)} onDone={() => { setImporting(false); refresh(); }} />}
    </div>
  );
}

function EnrolmentImport({ course, onClose, onDone }: { course: Course; onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const { run, pending, error } = useAction(async () => {
    const form = new FormData();
    form.append("file", file!);
    return api.upload(`/admin/courses/${course.id}/enrolments/import`, form);
  });
  return (
    <Dialog
      open
      onClose={onClose}
      title={`Import students into ${course.code}`}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} disabled={!file} onClick={async () => (await run()) && onDone()}>
            Import
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3 text-sm">
        <p className="text-muted">One student number per line, or a column headed student_number. Nothing is saved if any number is unknown.</p>
        <FileInput label="CSV file" accept=".csv" maxMb={5} file={file} onChange={setFile} />
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}

function CourseSessions({ course }: { course: Course }) {
  const { data } = useApi<Session[]>(`/lecturer/courses/${course.id}/sessions`);
  if (!data) return <Loading />;
  return (
    <DataTable
      rows={data}
      rowKey={(s) => s.id}
      emptyTitle="No sessions yet"
      emptyText="Lecturers create sessions and mark attendance."
      columns={[
        { key: "date", header: "Date", value: (s) => s.heldOn, render: (s) => formatDate(s.heldOn) },
        { key: "time", header: "Time", value: (s) => `${s.startsAt.slice(0, 5)} to ${s.endsAt.slice(0, 5)}` },
        { key: "topic", header: "Topic", value: (s) => s.topic ?? "" },
        { key: "att", header: "Attendance", value: (s) => s.present, render: (s) => (s.attendanceTaken ? `${s.present} present · ${s.late} late · ${s.absent} absent` : <StatusChip status="AT_RISK" label={s.future ? "Upcoming" : "Not taken"} />) },
        { key: "edit", header: "", render: (s) => !s.future && <Link className="text-sm font-medium text-primary hover:underline" href={`/admin/courses/attendance/${s.id}`}>{s.lecturerCanEdit ? "Open" : "Change (admin)"}</Link> },
      ]}
    />
  );
}

function CourseDialog({ course, onClose, onDone }: { course?: Course; onClose: () => void; onDone: () => void }) {
  const semesters = useApi<Semester[]>("/reference/semesters");
  const lecturers = useApi<Page<User>>("/admin/users?role=LECTURER&staffOnly=true&size=200");
  const [f, setF] = useState({ code: course?.code ?? "", title: course?.title ?? "", semesterId: course?.semesterId ?? "", creditHours: String(course?.creditHours ?? 3) });
  const [ids, setIds] = useState<string[]>(course?.lecturers.map((l) => l.id) ?? []);
  const toast = useToast();
  const { run, pending, error } = useAction(async () => {
    const body = { ...f, creditHours: Number(f.creditHours), lecturerIds: ids };
    return course ? api.put(`/admin/courses/${course.id}`, body) : api.post("/admin/courses", body);
  });
  return (
    <Dialog
      open
      onClose={onClose}
      title={course ? `Edit ${course.code}` : "Add a course"}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} disabled={!f.code || !f.title || !f.semesterId} onClick={async () => { if (await run()) { toast("Course saved"); onDone(); } }}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField label="Code" value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} required />
        <TextField label="Credit hours" type="number" value={f.creditHours} onChange={(e) => setF({ ...f, creditHours: e.target.value })} />
        <div className="sm:col-span-2">
          <TextField label="Title" value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} required />
        </div>
        <div className="sm:col-span-2">
          <SelectField label="Semester" value={f.semesterId} onChange={(e) => setF({ ...f, semesterId: e.target.value })} required>
            <option value="">Choose…</option>
            {semesters.data?.map((s) => <option key={s.id} value={s.id}>{s.label}{s.current ? " (current)" : ""}</option>)}
          </SelectField>
        </div>
        <div className="sm:col-span-2">
          <Field label="Lecturers">
            <div className="grid max-h-48 grid-cols-1 gap-2 overflow-y-auto rounded-lg border border-border p-3 sm:grid-cols-2">
              {lecturers.data?.content.filter((u) => u.active).map((u) => (
                <Checkbox key={u.id} label={u.name} checked={ids.includes(u.id)} onChange={(on) => setIds(on ? [...ids, u.id] : ids.filter((x) => x !== u.id))} />
              ))}
            </div>
          </Field>
        </div>
        <div className="sm:col-span-2">
          <ErrorBox error={error} />
        </div>
      </div>
    </Dialog>
  );
}
