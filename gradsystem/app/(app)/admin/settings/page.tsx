"use client";

import { useState } from "react";
import { Megaphone } from "lucide-react";
import { useToast } from "@/components/toast";
import { Button, Card, Checkbox, ErrorBox, Field, Loading, PageHeader, Select, TextField, TextareaField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime, pretty } from "@/lib/format";
import { useAction, useApi, useEditable } from "@/lib/hooks";
import type { Cohort, Programme, Setting } from "@/lib/types";

const GROUPS: [string, string][] = [
  ["screening", "Thesis screening"],
  ["attendance", "Attendance"],
  ["milestone", "Presentations"],
  ["deadline", "Deadline defaults for new programmes"],
  ["reminder", "Reminders"],
  ["upload", "Uploads"],
];

/** Every threshold is a setting (FR-SET-01). Changes apply from now on and are audited (FR-SET-02). */
export default function SettingsPage() {
  const { data, error, mutate } = useApi<Setting[]>("/admin/settings");
  const [values, setValues] = useEditable<Setting[], Record<string, string>>(data, (d) => Object.fromEntries(d.map((s) => [s.key, s.value])), {});
  const toast = useToast();
  const save = useAction(async () => {
    const changed = data!.filter((s) => values[s.key] !== s.value).map((s) => ({ key: s.key, value: values[s.key], version: s.version }));
    if (changed.length === 0) return data!;
    return api.put<Setting[]>("/admin/settings", { settings: changed });
  });
  const dirty = data?.some((s) => values[s.key] !== undefined && values[s.key] !== s.value);

  return (
    <>
      <PageHeader title="Settings" description="Thresholds and limits. A change applies from the moment you save it; past decisions are not recalculated." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-5">
          {GROUPS.map(([prefix, title]) => (
            <Card key={prefix} title={title}>
              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                {data.filter((s) => s.key.startsWith(prefix + ".")).map((s) => (
                  <TextField
                    key={s.key}
                    label={pretty(s.key.split(".")[1])}
                    value={values[s.key] ?? ""}
                    inputMode={s.valueType === "LIST" ? "text" : "decimal"}
                    onChange={(e) => setValues({ ...values, [s.key]: e.target.value })}
                    hint={`${s.description ?? ""}${s.updatedAt ? ` · last changed ${formatDateTime(s.updatedAt)}` : ""}`}
                  />
                ))}
              </div>
            </Card>
          ))}
          <ErrorBox error={save.error} />
          <div className="sticky bottom-20 flex justify-end sm:bottom-4">
            <Button
              variant="primary"
              loading={save.pending}
              disabled={!dirty}
              onClick={async () => {
                const r = await save.run();
                if (r) {
                  toast("Settings saved");
                  void mutate(r, false);
                }
              }}
            >
              Save changes
            </Button>
          </div>
          <Announcement />
        </div>
      )}
    </>
  );
}

const ROLES = ["STUDENT", "SUPERVISOR", "LECTURER", "SECRETARY", "LIBRARIAN", "ADMIN"];

/** FR-NTF-05: announcements by role, programme or cohort. */
function Announcement() {
  const programmes = useApi<Programme[]>("/reference/programmes");
  const [programmeId, setProgrammeId] = useState("");
  const cohorts = useApi<Cohort[]>(programmeId ? `/reference/cohorts?programmeId=${programmeId}` : null);
  const [cohortId, setCohortId] = useState("");
  const [roles, setRoles] = useState<string[]>([]);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const toast = useToast();
  const send = useAction(async () =>
    api.post<{ recipients: number }>("/admin/announcements", { title, body, roles, programmeId: programmeId || null, cohortId: cohortId || null }),
  );
  return (
    <Card title={<span className="inline-flex items-center gap-2"><Megaphone size={16} aria-hidden /> Send an announcement</span>} description="Goes to the in-app inbox and by email.">
      <div className="flex flex-col gap-4">
        <Field label="To everyone with these roles">
          <div className="flex flex-wrap gap-4">
            {ROLES.map((r) => <Checkbox key={r} label={pretty(r)} checked={roles.includes(r)} onChange={(on) => setRoles(on ? [...roles, r] : roles.filter((x) => x !== r))} />)}
          </div>
        </Field>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label="And students in programme">
            <Select aria-label="Programme" value={programmeId} onChange={(e) => { setProgrammeId(e.target.value); setCohortId(""); }}>
              <option value="">No programme filter</option>
              {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
            </Select>
          </Field>
          <Field label="Cohort">
            <Select aria-label="Cohort" value={cohortId} disabled={!programmeId} onChange={(e) => setCohortId(e.target.value)}>
              <option value="">All cohorts</option>
              {cohorts.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </Select>
          </Field>
        </div>
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <TextareaField label="Message" value={body} onChange={(e) => setBody(e.target.value)} rows={4} required />
        <ErrorBox error={send.error} />
        <div>
          <Button
            variant="primary"
            loading={send.pending}
            disabled={!title || !body || (roles.length === 0 && !programmeId)}
            onClick={async () => {
              const r = await send.run();
              if (r) {
                toast(`Sent to ${r.recipients} people`);
                setTitle("");
                setBody("");
              }
            }}
          >
            Send announcement
          </Button>
        </div>
      </div>
    </Card>
  );
}

