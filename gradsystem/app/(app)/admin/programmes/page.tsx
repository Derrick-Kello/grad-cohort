"use client";

import { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { DataTable } from "@/components/data-table";
import { Dialog } from "@/components/dialog";
import { useToast } from "@/components/toast";
import { Banner, Button, Card, Checkbox, ErrorBox, Input, Loading, PageHeader, Select, SelectField, StatusChip, Tabs, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { useAction, useApi, useEditable } from "@/lib/hooks";
import type { Cohort, Programme, Semester, Template, Venue } from "@/lib/types";

type Tab = "programmes" | "templates" | "rules" | "semesters" | "cohorts" | "venues";

export default function ProgrammesPage() {
  const [tab, setTab] = useState<Tab>("programmes");
  return (
    <>
      <PageHeader title="Programmes and calendar" description="Every value here is a setting. Changes apply from now on and never alter milestones already generated for enrolled students." />
      <Tabs
        value={tab}
        onChange={setTab}
        items={[
          { value: "programmes", label: "Programmes" },
          { value: "templates", label: "Milestone templates" },
          { value: "rules", label: "Thesis format rules" },
          { value: "semesters", label: "Semesters" },
          { value: "cohorts", label: "Cohorts" },
          { value: "venues", label: "Venues" },
        ]}
      />
      {tab === "programmes" && <ProgrammesTab />}
      {tab === "templates" && <TemplatesTab />}
      {tab === "rules" && <RulesTab />}
      {tab === "semesters" && <SemestersTab />}
      {tab === "cohorts" && <CohortsTab />}
      {tab === "venues" && <VenuesTab />}
    </>
  );
}

function useProgrammes() {
  return useApi<Programme[]>("/admin/programmes");
}

function ProgrammePicker({ value, onChange }: { value: string; onChange: (id: string) => void }) {
  const { data } = useProgrammes();
  useEffect(() => {
    if (!value && data?.[0]) onChange(data[0].id);
  }, [value, data, onChange]);
  return (
    <Select aria-label="Programme" value={value} onChange={(e) => onChange(e.target.value)} className="mb-4 w-full sm:w-72">
      {data?.map((p) => (
        <option key={p.id} value={p.id}>
          {p.name}
        </option>
      ))}
    </Select>
  );
}

function ProgrammesTab() {
  const { data, error, mutate } = useProgrammes();
  const [editing, setEditing] = useState<Programme | "new" | null>(null);
  return (
    <>
      <div className="mb-3 flex justify-end">
        <Button icon={<Plus size={16} />} onClick={() => setEditing("new")}>
          Add programme
        </Button>
      </div>
      <ErrorBox error={error} />
      {!data && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(p) => p.id}
          onRowClick={setEditing}
          columns={[
            { key: "code", header: "Code", value: (p) => p.code, className: "font-medium" },
            { key: "name", header: "Name", value: (p) => p.name },
            { key: "level", header: "Level", value: (p) => p.level },
            { key: "duration", header: "Duration", value: (p) => `${p.durationMonths} months, ${p.semesters} semesters` },
            { key: "milestones", header: "Presentations", value: (p) => p.requiredMilestones, className: "tabular" },
            { key: "checkpoint", header: "Checkpoint", value: (p) => `${p.checkpointMilestones} by month ${p.checkpointMonths}` },
            { key: "evidence", header: "Evidence", value: (p) => (p.evidenceRequired ? "Required" : "Optional") },
          ]}
        />
      )}
      {editing && <ProgrammeDialog programme={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} onDone={() => { setEditing(null); void mutate(); }} />}
    </>
  );
}

function ProgrammeDialog({ programme, onClose, onDone }: { programme?: Programme; onClose: () => void; onDone: () => void }) {
  const p = programme;
  const [f, setF] = useState({
    code: p?.code ?? "",
    name: p?.name ?? "",
    level: p?.level ?? "MASTERS",
    durationMonths: String(p?.durationMonths ?? 24),
    semesters: String(p?.semesters ?? 4),
    requiredMilestones: String(p?.requiredMilestones ?? 5),
    checkpointMonths: String(p?.checkpointMonths ?? 18),
    checkpointMilestones: String(p?.checkpointMilestones ?? 4),
    firstMilestoneSemester: String(p?.firstMilestoneSemester ?? 2),
    requiredPublications: String(p?.requiredPublications ?? 0),
  });
  const [evidence, setEvidence] = useState(p?.evidenceRequired ?? true);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));
  const toast = useToast();
  const { run, pending, error } = useAction(async () => {
    const body = {
      ...f,
      durationMonths: Number(f.durationMonths),
      semesters: Number(f.semesters),
      requiredMilestones: Number(f.requiredMilestones),
      checkpointMonths: Number(f.checkpointMonths),
      checkpointMilestones: Number(f.checkpointMilestones),
      firstMilestoneSemester: Number(f.firstMilestoneSemester),
      requiredPublications: Number(f.requiredPublications),
      evidenceRequired: evidence,
      version: p?.version,
    };
    return p ? api.put(`/admin/programmes/${p.id}`, body) : api.post("/admin/programmes", body);
  });
  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title={p ? `Edit ${p.name}` : "Add a programme"}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} onClick={async () => { if (await run()) { toast("Programme saved"); onDone(); } }}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField label="Code" value={f.code} onChange={set("code")} required />
        <TextField label="Name" value={f.name} onChange={set("name")} required />
        <SelectField label="Level" value={f.level} onChange={set("level")}>
          <option value="MASTERS">Masters</option>
          <option value="PHD">PhD</option>
        </SelectField>
        <TextField label="Presentations required" type="number" min={1} value={f.requiredMilestones} onChange={set("requiredMilestones")} hint="Update the milestone templates to match." />
        <TextField label="Duration (months)" type="number" value={f.durationMonths} onChange={set("durationMonths")} />
        <TextField label="Semesters" type="number" value={f.semesters} onChange={set("semesters")} />
        <TextField label="Checkpoint month" type="number" value={f.checkpointMonths} onChange={set("checkpointMonths")} />
        <TextField label="Presentations needed by the checkpoint" type="number" value={f.checkpointMilestones} onChange={set("checkpointMilestones")} />
        <TextField label="First presentation from semester" type="number" value={f.firstMilestoneSemester} onChange={set("firstMilestoneSemester")} />
        <TextField label="Approved publications before thesis" type="number" min={0} value={f.requiredPublications} onChange={set("requiredPublications")} />
        <div className="sm:col-span-2">
          <Checkbox label="Evidence is mandatory before a presentation is approved" checked={evidence} onChange={setEvidence} />
        </div>
        <div className="sm:col-span-2">
          <ErrorBox error={error} />
        </div>
      </div>
    </Dialog>
  );
}

function TemplatesTab() {
  const [programmeId, setProgrammeId] = useState("");
  const programmes = useProgrammes();
  const programme = programmes.data?.find((p) => p.id === programmeId);
  const { data, mutate } = useApi<Template[]>(programmeId ? `/admin/programmes/${programmeId}/milestone-templates` : null);
  const [rows, setRows] = useEditable<Template[], Template[]>(data, (d) => d, []);
  const toast = useToast();
  const count = programme?.requiredMilestones ?? rows.length;
  const editable = Array.from({ length: count }, (_, i) => rows.find((r) => r.sequenceNo === i + 1) ?? { id: `new-${i}`, sequenceNo: i + 1, title: "", description: "", finalPreDefense: i + 1 === count, evidenceRequired: true });
  const save = useAction(async () => api.put<Template[]>(`/admin/programmes/${programmeId}/milestone-templates`, editable));
  const update = (seq: number, patch: Partial<Template>) =>
    setRows(editable.map((r) => (r.sequenceNo === seq ? { ...r, ...patch } : patch.finalPreDefense ? { ...r, finalPreDefense: false } : r)));

  return (
    <>
      <ProgrammePicker value={programmeId} onChange={setProgrammeId} />
      {!data ? (
        <Loading />
      ) : (
        <Card title={`${programme?.name ?? ""}: ${count} presentations`} description="Exactly one template, the last, is the final pre-defense.">
          <div className="flex flex-col gap-3">
            {editable.map((t) => (
              <div key={t.sequenceNo} className="grid grid-cols-1 gap-2 rounded-lg border border-border p-3 sm:grid-cols-[40px_1fr_1fr] sm:items-start">
                <span className="tabular text-sm font-semibold text-muted">{t.sequenceNo}</span>
                <Input aria-label={`Title of presentation ${t.sequenceNo}`} value={t.title} onChange={(e) => update(t.sequenceNo, { title: e.target.value })} placeholder="Title" />
                <Input aria-label={`Description of presentation ${t.sequenceNo}`} value={t.description ?? ""} onChange={(e) => update(t.sequenceNo, { description: e.target.value })} placeholder="Description" />
                <span />
                <div className="flex flex-wrap gap-4 sm:col-span-2">
                  <Checkbox label="Evidence required" checked={t.evidenceRequired} onChange={(v) => update(t.sequenceNo, { evidenceRequired: v })} />
                  <Checkbox label="Final pre-defense" checked={t.finalPreDefense} onChange={(v) => update(t.sequenceNo, { finalPreDefense: v })} />
                </div>
              </div>
            ))}
            <ErrorBox error={save.error} />
            <div>
              <Button variant="primary" loading={save.pending} onClick={async () => { if (await save.run()) { toast("Templates saved"); void mutate(); } }}>
                Save templates
              </Button>
            </div>
          </div>
        </Card>
      )}
    </>
  );
}

type Rule = { id: string; ruleCode: string; parameters: Record<string, unknown>; active: boolean };
const RULE_NAMES: Record<string, string> = {
  "FMT-PAGE": "Paper size", "FMT-MARGIN": "Margins", "FMT-FONT": "Body font and size", "FMT-SPACING": "Line spacing",
  "FMT-HEAD": "Heading styles", "FMT-PAGENUM": "Page numbering", "FMT-SECTIONS": "Required sections", "FMT-ABSTRACT": "Abstract length",
  "FMT-TOC": "Table of contents", "FMT-REFS": "References",
};

function RulesTab() {
  const [programmeId, setProgrammeId] = useState("");
  const { data, mutate } = useApi<Rule[]>(programmeId ? `/admin/programmes/${programmeId}/format-rules` : null);
  const [rules, setRules] = useEditable<Rule[], Rule[]>(data, (d) => d, []);
  const toast = useToast();
  const save = useAction(async () => api.put<Rule[]>(`/admin/programmes/${programmeId}/format-rules`, rules.map((r) => ({ ruleCode: r.ruleCode, parameters: r.parameters, active: r.active }))));
  const setParam = (code: string, key: string, raw: string, original: unknown) => {
    const value = Array.isArray(original) ? raw.split(",").map((s) => s.trim()).filter(Boolean) : typeof original === "number" ? Number(raw) : typeof original === "boolean" ? raw === "true" : raw;
    setRules(rules.map((r) => (r.ruleCode === code ? { ...r, parameters: { ...r.parameters, [key]: value } } : r)));
  };
  return (
    <>
      <ProgrammePicker value={programmeId} onChange={setProgrammeId} />
      <Banner tone="info">The default rules follow the school&apos;s draft thesis guideline. Confirm them against the official guideline before go-live.</Banner>
      {!data ? (
        <Loading />
      ) : (
        <div className="mt-4 flex flex-col gap-3">
          {rules.map((r) => (
            <Card key={r.ruleCode} title={RULE_NAMES[r.ruleCode] ?? r.ruleCode} description={r.ruleCode} actions={<Checkbox label="Active" checked={r.active} onChange={(v) => setRules(rules.map((x) => (x.ruleCode === r.ruleCode ? { ...x, active: v } : x)))} />}>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                {Object.entries(r.parameters).map(([k, v]) => (
                  <label key={k} className={`flex flex-col gap-1 text-sm ${Array.isArray(v) ? "sm:col-span-3" : ""}`}>
                    <span className="font-medium">{k}</span>
                    <Input value={Array.isArray(v) ? v.join(", ") : String(v)} onChange={(e) => setParam(r.ruleCode, k, e.target.value, v)} disabled={!r.active} />
                  </label>
                ))}
              </div>
            </Card>
          ))}
          <ErrorBox error={save.error} />
          <div>
            <Button variant="primary" loading={save.pending} onClick={async () => { if (await save.run()) { toast("Format rules saved"); void mutate(); } }}>
              Save rules
            </Button>
          </div>
        </div>
      )}
    </>
  );
}

function SemestersTab() {
  const { data, mutate } = useApi<Semester[]>("/admin/semesters");
  const [f, setF] = useState({ academicYear: "", number: "1", startsOn: "", endsOn: "" });
  const toast = useToast();
  const add = useAction(async () => api.post("/admin/semesters", { ...f, number: Number(f.number) }));
  return (
    <div className="flex flex-col gap-4">
      <Card title="Add a semester" description="Semesters cannot overlap. The current semester is worked out from today's date.">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-5 sm:items-end">
          <TextField label="Academic year" placeholder="2027/2028" value={f.academicYear} onChange={(e) => setF({ ...f, academicYear: e.target.value })} />
          <SelectField label="Semester" value={f.number} onChange={(e) => setF({ ...f, number: e.target.value })}>
            <option value="1">1</option>
            <option value="2">2</option>
          </SelectField>
          <TextField label="Starts" type="date" value={f.startsOn} onChange={(e) => setF({ ...f, startsOn: e.target.value })} />
          <TextField label="Ends" type="date" value={f.endsOn} onChange={(e) => setF({ ...f, endsOn: e.target.value })} />
          <Button variant="primary" loading={add.pending} disabled={!f.academicYear || !f.startsOn || !f.endsOn} onClick={async () => { if (await add.run()) { toast("Semester added"); void mutate(); } }}>
            Add
          </Button>
        </div>
        <div className="mt-3">
          <ErrorBox error={add.error} />
        </div>
      </Card>
      {data && (
        <DataTable
          rows={data}
          rowKey={(s) => s.id}
          columns={[
            { key: "label", header: "Semester", value: (s) => s.label, className: "font-medium" },
            { key: "starts", header: "Starts", value: (s) => s.startsOn, render: (s) => formatDate(s.startsOn) },
            { key: "ends", header: "Ends", value: (s) => s.endsOn, render: (s) => formatDate(s.endsOn) },
            { key: "current", header: "", render: (s) => (s.current ? <StatusChip status="ACTIVE" label="Current" /> : null) },
          ]}
        />
      )}
    </div>
  );
}

function CohortsTab() {
  const programmes = useProgrammes();
  const semesters = useApi<Semester[]>("/admin/semesters");
  const { data, mutate } = useApi<Cohort[]>("/admin/cohorts");
  const [f, setF] = useState({ programmeId: "", name: "", intakeSemesterId: "" });
  const toast = useToast();
  const add = useAction(async () => api.post("/admin/cohorts", { ...f, intakeSemesterId: f.intakeSemesterId || null }));
  return (
    <div className="flex flex-col gap-4">
      <Card title="Add a cohort" description="Every student belongs to exactly one cohort.">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-4 sm:items-end">
          <SelectField label="Programme" value={f.programmeId} onChange={(e) => setF({ ...f, programmeId: e.target.value })}>
            <option value="">Choose…</option>
            {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
          </SelectField>
          <TextField label="Name" placeholder="2027 Intake" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
          <SelectField label="Intake semester" value={f.intakeSemesterId} onChange={(e) => setF({ ...f, intakeSemesterId: e.target.value })}>
            <option value="">Not set</option>
            {semesters.data?.map((s) => <option key={s.id} value={s.id}>{s.label}</option>)}
          </SelectField>
          <Button variant="primary" loading={add.pending} disabled={!f.programmeId || !f.name} onClick={async () => { if (await add.run()) { toast("Cohort added"); void mutate(); } }}>
            Add
          </Button>
        </div>
        <div className="mt-3">
          <ErrorBox error={add.error} />
        </div>
      </Card>
      {data && (
        <DataTable
          rows={data}
          rowKey={(c) => c.id}
          columns={[
            { key: "programme", header: "Programme", value: (c) => c.programmeCode },
            { key: "name", header: "Cohort", value: (c) => c.name, className: "font-medium" },
            { key: "intake", header: "Intake", value: (c) => c.intakeSemesterLabel ?? "" },
          ]}
        />
      )}
    </div>
  );
}

function VenuesTab() {
  const { data, mutate } = useApi<Venue[]>("/admin/venues");
  const [f, setF] = useState({ name: "", capacity: "" });
  const toast = useToast();
  const add = useAction(async () => api.post("/admin/venues", { name: f.name, capacity: f.capacity ? Number(f.capacity) : null, active: true }));
  const toggle = async (v: Venue) => {
    await api.put(`/admin/venues/${v.id}`, { name: v.name, capacity: v.capacity, active: !v.active });
    void mutate();
  };
  return (
    <div className="flex flex-col gap-4">
      <Card title="Add a venue">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3 sm:items-end">
          <TextField label="Name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} />
          <TextField label="Capacity" type="number" value={f.capacity} onChange={(e) => setF({ ...f, capacity: e.target.value })} />
          <Button variant="primary" loading={add.pending} disabled={!f.name} onClick={async () => { if (await add.run()) { toast("Venue added"); setF({ name: "", capacity: "" }); void mutate(); } }}>
            Add
          </Button>
        </div>
        <div className="mt-3">
          <ErrorBox error={add.error} />
        </div>
      </Card>
      {data && (
        <DataTable
          rows={data}
          rowKey={(v) => v.id}
          columns={[
            { key: "name", header: "Venue", value: (v) => v.name, className: "font-medium" },
            { key: "capacity", header: "Capacity", value: (v) => v.capacity ?? "", className: "tabular" },
            { key: "status", header: "Status", value: (v) => (v.active ? "In use" : "Not in use"), render: (v) => <StatusChip status={v.active ? "ACTIVE" : "INACTIVE"} label={v.active ? "In use" : "Not in use"} tone={v.active ? "ok" : "grey"} /> },
            { key: "actions", header: "", render: (v) => <Button size="sm" onClick={() => toggle(v)}>{v.active ? "Take out of use" : "Put back in use"}</Button> },
          ]}
        />
      )}
    </div>
  );
}
