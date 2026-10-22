"use client";

import { useState } from "react";
import { Download } from "lucide-react";
import { Button, Card, ErrorBox, Input, Loading, PageHeader, Select } from "@/components/ui";
import { download } from "@/lib/api";
import { useApi } from "@/lib/hooks";
import type { Cohort, Programme, ReportTable } from "@/lib/types";

type ReportInfo = { key: string; title: string };

/** Standard reports (FR-RPT-01..07). Exports use the same filters as the table on screen (FR-RPT-08). */
export default function ReportsPage() {
  const reports = useApi<ReportInfo[]>("/admin/reports");
  const programmes = useApi<Programme[]>("/reference/programmes");
  const [key, setKey] = useState("programme-progress");
  const [programmeId, setProgrammeId] = useState("");
  const [cohortId, setCohortId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const cohorts = useApi<Cohort[]>(programmeId ? `/reference/cohorts?programmeId=${programmeId}` : null);
  const q = new URLSearchParams();
  if (programmeId) q.set("programmeId", programmeId);
  if (cohortId) q.set("cohortId", cohortId);
  if (from) q.set("from", from);
  if (to) q.set("to", to);
  const { data, error, mutate } = useApi<ReportTable>(`/admin/reports/${key}?${q}`);
  const exportAs = (format: "csv" | "pdf") => {
    const p = new URLSearchParams(q);
    p.set("format", format);
    void download(`/admin/reports/${key}?${p}`, `${key}.${format}`);
  };
  const dated = key === "presentation-schedule";

  return (
    <>
      <PageHeader
        title="Reports"
        actions={
          <>
            <Button icon={<Download size={16} />} onClick={() => exportAs("csv")}>CSV</Button>
            <Button icon={<Download size={16} />} onClick={() => exportAs("pdf")}>PDF</Button>
          </>
        }
      />
      <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-5">
        <Select aria-label="Report" value={key} onChange={(e) => setKey(e.target.value)} className="lg:col-span-2">
          {reports.data?.map((r) => <option key={r.key} value={r.key}>{r.title}</option>)}
        </Select>
        <Select aria-label="Programme" value={programmeId} onChange={(e) => { setProgrammeId(e.target.value); setCohortId(""); }}>
          <option value="">All programmes</option>
          {programmes.data?.map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
        </Select>
        {dated ? (
          <>
            <Input aria-label="From" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
            <Input aria-label="To" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </>
        ) : (
          <Select aria-label="Cohort" value={cohortId} onChange={(e) => setCohortId(e.target.value)} disabled={!programmeId}>
            <option value="">All cohorts</option>
            {cohorts.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        )}
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card title={data.title} description={data.subtitle.join(" · ")} padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead className="bg-surface-2 text-left text-xs text-muted uppercase">
                <tr>{data.columns.map((c) => <th key={c} scope="col" className="px-4 py-2.5 font-semibold">{c}</th>)}</tr>
              </thead>
              <tbody>
                {data.rows.map((r, i) => (
                  <tr key={i} className="border-t border-border">
                    {r.map((cell, j) => <td key={j} className="px-4 py-2.5 align-top">{cell}</td>)}
                  </tr>
                ))}
                {data.rows.length === 0 && (
                  <tr>
                    <td colSpan={data.columns.length} className="px-4 py-8 text-center text-muted">No rows match these filters.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </>
  );
}
