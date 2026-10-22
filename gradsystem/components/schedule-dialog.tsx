"use client";

import { useState } from "react";
import { Plus, X } from "lucide-react";
import { api } from "@/lib/api";
import { accraToIso, formatDate, isoDay } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Attempt, Page, User, Venue } from "@/lib/types";
import { Dialog } from "./dialog";
import { useToast } from "./toast";
import { Banner, Button, ErrorBox, Field, Input, Select, SelectField, TextField, TextareaField } from "./ui";

type PanelRow = { userId: string; externalName: string; role: "CHAIR" | "MEMBER" };

export type ScheduleTarget = { milestoneIds: string[]; title: string; reschedule?: Attempt };

function tomorrow() {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() + 1);
  return isoDay(d);
}

/**
 * Admin scheduling (FR-MIL-02..05): date, start time, duration, venue or online link, panel. With several
 * milestones it schedules one slot after another (bulk). With an attempt it reschedules and needs a reason.
 * Student and venue clashes are refused by the server; panel clashes come back as warnings.
 */
export function ScheduleDialog({ target, onClose, onDone }: { target: ScheduleTarget; onClose: () => void; onDone: () => void }) {
  const r = target.reschedule;
  const bulk = target.milestoneIds.length > 1;
  const venues = useApi<Venue[]>("/reference/venues");
  const staff = useApi<Page<User>>("/admin/users?role=LECTURER&staffOnly=true&size=200");
  const [day, setDay] = useState(r ? r.scheduledStart.slice(0, 10) : tomorrow());
  const [time, setTime] = useState(r ? r.scheduledStart.slice(11, 16) : "09:00");
  const [duration, setDuration] = useState(r ? String(Math.round((new Date(r.scheduledEnd).getTime() - new Date(r.scheduledStart).getTime()) / 60000)) : "45");
  const [gap, setGap] = useState("15");
  const [venueId, setVenueId] = useState(r?.venueId ?? "");
  const [onlineLink, setOnlineLink] = useState(r?.onlineLink ?? "");
  const [panel, setPanel] = useState<PanelRow[]>(
    r?.panel.map((p) => ({ userId: p.userId ?? "", externalName: p.userId ? "" : p.externalName ?? "", role: p.role as "CHAIR" | "MEMBER" })) ?? [{ userId: "", externalName: "", role: "CHAIR" }],
  );
  const [reason, setReason] = useState("");
  const [warnings, setWarnings] = useState<string[]>([]);
  const toast = useToast();

  const panelBody = panel.filter((p) => p.userId || p.externalName.trim()).map((p) => ({ userId: p.userId || null, externalName: p.userId ? null : p.externalName.trim(), role: p.role }));
  const { run, pending, error } = useAction(async () => {
    const start = accraToIso(day, time);
    const base = { venueId: venueId || null, onlineLink: onlineLink || null, panel: panelBody };
    if (r) return api.post(`/admin/attempts/${r.id}/reschedule`, { ...base, start, durationMinutes: Number(duration), reason });
    if (bulk) {
      const results = await api.post<{ warnings: string[] }[]>("/admin/schedule/bulk", {
        ...base,
        milestoneIds: target.milestoneIds,
        start,
        slotMinutes: Number(duration),
        breakMinutes: Number(gap),
      });
      return results.flatMap((x) => x.warnings);
    }
    const result = await api.post<{ warnings: string[] }>(`/admin/milestones/${target.milestoneIds[0]}/schedule`, { ...base, start, durationMinutes: Number(duration) });
    return result.warnings;
  });

  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title={target.title}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            disabled={(!venueId && !onlineLink) || (!!r && !reason.trim())}
            onClick={async () => {
              const result = await run();
              if (result === undefined) return;
              const w = Array.isArray(result) ? (result as string[]) : [];
              toast(r ? "Presentation rescheduled. Everyone involved was notified." : bulk ? `${target.milestoneIds.length} presentations scheduled` : "Presentation scheduled");
              if (w.length) setWarnings(w);
              else onDone();
            }}
          >
            {r ? "Reschedule" : bulk ? `Schedule ${target.milestoneIds.length} presentations` : "Schedule"}
          </Button>
        </>
      }
    >
      {warnings.length > 0 ? (
        <div className="flex flex-col gap-3">
          <Banner tone="warn" title="Scheduled, with panel clashes">
            <ul className="list-disc pl-5">
              {warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          </Banner>
          <Button onClick={onDone}>Done</Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <TextField label={bulk ? "Date of the session" : "Date"} type="date" min={isoDay()} value={day} onChange={(e) => setDay(e.target.value)} required hint={formatDate(day)} />
          <TextField label={bulk ? "First slot starts" : "Start time"} type="time" value={time} onChange={(e) => setTime(e.target.value)} required hint="24-hour, Africa/Accra" />
          <TextField label={bulk ? "Minutes per student" : "Duration (minutes)"} type="number" min={10} max={480} value={duration} onChange={(e) => setDuration(e.target.value)} required />
          {bulk ? (
            <TextField label="Break between slots (minutes)" type="number" min={0} value={gap} onChange={(e) => setGap(e.target.value)} />
          ) : (
            <span />
          )}
          <SelectField label="Venue" value={venueId} onChange={(e) => setVenueId(e.target.value)}>
            <option value="">Online only</option>
            {venues.data?.filter((v) => v.active).map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
                {v.capacity ? ` (${v.capacity})` : ""}
              </option>
            ))}
          </SelectField>
          <TextField label="Online link" type="url" placeholder="https://" value={onlineLink} onChange={(e) => setOnlineLink(e.target.value)} hint="Needed when no venue is chosen." />
          <div className="sm:col-span-2">
            <Field label="Panel">
              <div className="flex flex-col gap-2">
                {panel.map((p, i) => (
                  <div key={i} className="grid grid-cols-1 gap-2 rounded-lg border border-border p-2 sm:grid-cols-[1fr_1fr_120px_auto]">
                    <Select aria-label="Staff member" value={p.userId} onChange={(e) => setPanel(panel.map((x, j) => (j === i ? { ...x, userId: e.target.value } : x)))}>
                      <option value="">External member…</option>
                      {staff.data?.content.filter((u) => u.active).map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.name}
                        </option>
                      ))}
                    </Select>
                    <Input
                      aria-label="External name"
                      placeholder="External name"
                      disabled={!!p.userId}
                      value={p.externalName}
                      onChange={(e) => setPanel(panel.map((x, j) => (j === i ? { ...x, externalName: e.target.value } : x)))}
                    />
                    <Select aria-label="Role" value={p.role} onChange={(e) => setPanel(panel.map((x, j) => (j === i ? { ...x, role: e.target.value as "CHAIR" | "MEMBER" } : x)))}>
                      <option value="CHAIR">Chair</option>
                      <option value="MEMBER">Member</option>
                    </Select>
                    <Button variant="ghost" aria-label="Remove panel member" onClick={() => setPanel(panel.filter((_, j) => j !== i))}>
                      <X size={15} />
                    </Button>
                  </div>
                ))}
                <Button size="sm" className="self-start" icon={<Plus size={14} />} onClick={() => setPanel([...panel, { userId: "", externalName: "", role: "MEMBER" }])}>
                  Add panel member
                </Button>
              </div>
            </Field>
          </div>
          {r && (
            <div className="sm:col-span-2">
              <TextareaField label="Reason for the change" value={reason} onChange={(e) => setReason(e.target.value)} rows={2} required hint="The student, supervisor and secretary are notified." />
            </div>
          )}
          <div className="sm:col-span-2">
            <ErrorBox error={error} />
          </div>
        </div>
      )}
    </Dialog>
  );
}
