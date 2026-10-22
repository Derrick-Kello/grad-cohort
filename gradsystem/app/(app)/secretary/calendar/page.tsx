"use client";

import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { PresentationAgenda } from "@/components/presentation-list";
import { Button, Card, ErrorBox, Loading, PageHeader, Select, Tabs } from "@/components/ui";
import { formatDate, isoDay } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { PresentationRow, Programme, Venue } from "@/lib/types";

function addDays(day: string, n: number) {
  const d = new Date(`${day}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

function monday(day: string) {
  const d = new Date(`${day}T12:00:00Z`);
  const dow = (d.getUTCDay() + 6) % 7;
  return addDays(day, -dow);
}

/** Day and week list of scheduled presentations, filterable by programme, venue and status (FR-MIL-06). */
export default function CalendarPage() {
  const [mode, setMode] = useState<"day" | "week">("week");
  const [anchor, setAnchor] = useState(isoDay());
  const [programmeId, setProgrammeId] = useState("");
  const [venueId, setVenueId] = useState("");
  const [state, setState] = useState("");
  const from = mode === "day" ? anchor : monday(anchor);
  const to = mode === "day" ? anchor : addDays(from, 6);
  const q = new URLSearchParams({ from, to });
  if (programmeId) q.set("programmeId", programmeId);
  if (venueId) q.set("venueId", venueId);
  if (state) q.set("state", state);
  const { data, error, mutate } = useApi<PresentationRow[]>(`/secretary/presentations?${q}`);
  const programmes = useApi<Programme[]>("/reference/programmes");
  const venues = useApi<Venue[]>("/reference/venues");
  const step = mode === "day" ? 1 : 7;

  return (
    <>
      <PageHeader title="Presentation calendar" />
      <Tabs value={mode} onChange={setMode} items={[{ value: "day", label: "Day" }, { value: "week", label: "Week" }]} />
      <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-center gap-2">
          <Button size="sm" aria-label="Previous" onClick={() => setAnchor(addDays(anchor, -step))}>
            <ChevronLeft size={15} />
          </Button>
          <Button size="sm" onClick={() => setAnchor(isoDay())}>
            Today
          </Button>
          <Button size="sm" aria-label="Next" onClick={() => setAnchor(addDays(anchor, step))}>
            <ChevronRight size={15} />
          </Button>
          <p className="ml-2 text-sm font-semibold">{mode === "day" ? formatDate(from) : `${formatDate(from)} to ${formatDate(to)}`}</p>
        </div>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
          <Select aria-label="Programme" value={programmeId} onChange={(e) => setProgrammeId(e.target.value)}>
            <option value="">All programmes</option>
            {programmes.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.code}
              </option>
            ))}
          </Select>
          <Select aria-label="Venue" value={venueId} onChange={(e) => setVenueId(e.target.value)}>
            <option value="">All venues</option>
            {venues.data?.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Status" value={state} onChange={(e) => setState(e.target.value)}>
            <option value="">All statuses</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="PASSED">Passed</option>
            <option value="FAILED">Not passed</option>
            <option value="NOT_HELD">Not held</option>
            <option value="CANCELLED">Cancelled</option>
          </Select>
        </div>
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card>
          <PresentationAgenda rows={data} />
        </Card>
      )}
    </>
  );
}
