"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Plus, Upload } from "lucide-react";
import { CsvImportDialog } from "@/components/csv-import";
import { Pager } from "@/components/data-table";
import { Dialog } from "@/components/dialog";
import { StaffSelect } from "@/components/people-picker";
import { StudentTable } from "@/components/student-table";
import { useToast } from "@/components/toast";
import { Button, ErrorBox, Field, Input, Loading, PageHeader, Select, SelectField, TextField } from "@/components/ui";
import { api, download } from "@/lib/api";
import { isoDay } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Cohort, Page, Programme, StudentSummary } from "@/lib/types";

export default function AdminStudentsPage() {
  const router = useRouter();
  const [q, setQ] = useState("");
  const [programmeId, setProgrammeId] = useState("");
  const [cohortId, setCohortId] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [enrolling, setEnrolling] = useState(false);
  const [importing, setImporting] = useState(false);
  const params = new URLSearchParams({ q, page: String(page), size: "25" });
  if (programmeId) params.set("programmeId", programmeId);
  if (cohortId) params.set("cohortId", cohortId);
  if (status) params.set("status", status);
  const { data, error, mutate } = useApi<Page<StudentSummary>>(`/admin/students?${params}`);
  const programmes = useApi<Programme[]>("/reference/programmes");
  const cohorts = useApi<Cohort[]>(programmeId ? `/reference/cohorts?programmeId=${programmeId}` : "/reference/cohorts");

  return (
    <>
      <PageHeader
        title="Students"
        description="Enrol students, assign supervisors and follow every student's progress."
        actions={
          <>
            <Button icon={<Upload size={16} />} onClick={() => setImporting(true)}>
              Import CSV
            </Button>
            <Button variant="primary" icon={<Plus size={16} />} onClick={() => setEnrolling(true)}>
              Enrol student
            </Button>
          </>
        }
      />
      <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-5">
        <Input className="lg:col-span-2" aria-label="Search" placeholder="Search name, email or number" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <Select aria-label="Programme" value={programmeId} onChange={(e) => { setProgrammeId(e.target.value); setCohortId(""); setPage(0); }}>
          <option value="">All programmes</option>
          {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
        </Select>
        <Select aria-label="Cohort" value={cohortId} onChange={(e) => { setCohortId(e.target.value); setPage(0); }}>
          <option value="">All cohorts</option>
          {cohorts.data?.map((c) => <option key={c.id} value={c.id}>{c.programmeCode} {c.name}</option>)}
        </Select>
        <Select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All statuses</option>
          <option value="ACTIVE">On track</option>
          <option value="AT_RISK">At risk</option>
          <option value="FLAGGED_FOR_EXPULSION">Flagged</option>
          <option value="COMPLETED">Completed</option>
          <option value="EXPELLED">Expelled</option>
          <option value="WITHDRAWN">Withdrawn</option>
        </Select>
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-3">
          <StudentTable searchable={false} rows={data.content} onOpen={(s) => router.push(`/admin/students/${s.id}`)} />
          <div className="flex flex-wrap items-center justify-between gap-2">
            <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
            <Button size="sm" onClick={() => download(`/admin/reports/programme-progress?format=csv${programmeId ? `&programmeId=${programmeId}` : ""}`, "programme-progress.csv")}>
              Export progress report
            </Button>
          </div>
        </div>
      )}
      {enrolling && <EnrolDialog onClose={() => setEnrolling(false)} onDone={(id) => router.push(`/admin/students/${id}`)} />}
      {importing && <CsvImportDialog onClose={() => setImporting(false)} onDone={() => mutate()} />}
    </>
  );
}

function EnrolDialog({ onClose, onDone }: { onClose: () => void; onDone: (id: string) => void }) {
  const programmes = useApi<Programme[]>("/reference/programmes");
  const [f, setF] = useState({ firstName: "", lastName: "", email: "", number: "", phone: "", programmeId: "", cohortId: "", enrolledOn: isoDay(), supervisorUserId: "", coSupervisorUserId: "" });
  const cohorts = useApi<Cohort[]>(f.programmeId ? `/reference/cohorts?programmeId=${f.programmeId}` : null);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));
  const toast = useToast();
  const { run, pending, error } = useAction(async () =>
    api.post<StudentSummary>("/admin/students", { ...f, coSupervisorUserId: f.coSupervisorUserId || null, supervisorUserId: f.supervisorUserId || null }),
  );
  const ready = f.firstName && f.lastName && f.email && f.number && f.programmeId && f.cohortId && f.supervisorUserId;
  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title="Enrol a student"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            disabled={!ready}
            onClick={async () => {
              const s = await run();
              if (s) {
                toast(`${s.name} enrolled. An activation email was sent.`);
                onDone(s.id);
              }
            }}
          >
            Enrol student
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField label="First name" value={f.firstName} onChange={set("firstName")} required />
        <TextField label="Last name" value={f.lastName} onChange={set("lastName")} required />
        <TextField label="Email" type="email" value={f.email} onChange={set("email")} required />
        <TextField label="Student number" value={f.number} onChange={set("number")} required />
        <TextField label="Phone" type="tel" value={f.phone} onChange={set("phone")} />
        <TextField label="Enrolment date" type="date" value={f.enrolledOn} onChange={set("enrolledOn")} required hint="Due dates are computed from this date." />
        <SelectField label="Programme" value={f.programmeId} onChange={(e) => setF((x) => ({ ...x, programmeId: e.target.value, cohortId: "" }))} required>
          <option value="">Choose…</option>
          {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.name} ({p.requiredMilestones} presentations)</option>)}
        </SelectField>
        <SelectField label="Cohort" value={f.cohortId} onChange={set("cohortId")} required disabled={!f.programmeId}>
          <option value="">Choose…</option>
          {cohorts.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </SelectField>
        <Field label="Supervisor" required hint="Must hold the Lecturer and Supervisor roles.">
          <StaffSelect role="SUPERVISOR" label="Supervisor" value={f.supervisorUserId} onChange={(id) => setF((x) => ({ ...x, supervisorUserId: id }))} />
        </Field>
        <Field label="Co-supervisor (optional)">
          <StaffSelect role="SUPERVISOR" label="Co-supervisor" value={f.coSupervisorUserId} exclude={[f.supervisorUserId]} onChange={(id) => setF((x) => ({ ...x, coSupervisorUserId: id }))} />
        </Field>
        <div className="sm:col-span-2">
          <ErrorBox error={error} />
        </div>
      </div>
    </Dialog>
  );
}
