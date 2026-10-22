"use client";

import { Fragment, useState } from "react";
import { ChevronDown } from "lucide-react";
import { Pager } from "@/components/data-table";
import { Card, Empty, ErrorBox, Input, Loading, PageHeader, Select, cx } from "@/components/ui";
import { formatDateTime, pretty } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { AuditEntry, Page } from "@/lib/types";

const ENTITIES = ["Student", "StudentMilestone", "PresentationAttempt", "EvidenceFile", "Publication", "ThesisSubmission", "AppUser", "Course", "ClassSession", "Eligibility", "SystemSetting", "Programme"];

/** FR-AUD-02: filter by user, entity type and date range. Entries cannot be edited or deleted. */
export default function AuditLogPage() {
  const [entityType, setEntityType] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<string | null>(null);
  const q = new URLSearchParams({ page: String(page), size: "25" });
  if (entityType) q.set("entityType", entityType);
  if (from) q.set("from", from);
  if (to) q.set("to", to);
  const { data, error, mutate } = useApi<Page<AuditEntry>>(`/admin/audit?${q}`);

  return (
    <>
      <PageHeader title="Audit log" description="Every create, update, status change and approval, with who did it, when, the old and new values, and the IP address." />
      <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-3">
        <Select aria-label="Entity type" value={entityType} onChange={(e) => { setEntityType(e.target.value); setPage(0); }}>
          <option value="">All records</option>
          {ENTITIES.map((e) => <option key={e} value={e}>{e}</option>)}
        </Select>
        <Input aria-label="From" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <Input aria-label="To" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card padded={false}>
          {data.content.length === 0 ? (
            <Empty title="No entries" />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-sm">
                <thead className="bg-surface-2 text-left text-xs text-muted uppercase">
                  <tr>
                    <th scope="col" className="px-4 py-2.5">When</th>
                    <th scope="col" className="px-4 py-2.5">Who</th>
                    <th scope="col" className="px-4 py-2.5">Action</th>
                    <th scope="col" className="px-4 py-2.5">Record</th>
                    <th scope="col" className="px-4 py-2.5">IP</th>
                    <th scope="col" className="px-4 py-2.5" />
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((a) => (
                    <Fragment key={a.id}>
                      <tr className="border-t border-border">
                        <td className="tabular px-4 py-2.5 whitespace-nowrap">{formatDateTime(a.at)}</td>
                        <td className="px-4 py-2.5">{a.actorName}</td>
                        <td className="px-4 py-2.5 font-medium">{pretty(a.action)}</td>
                        <td className="px-4 py-2.5 text-muted">{a.entityType}</td>
                        <td className="px-4 py-2.5 text-muted">{a.ipAddress}</td>
                        <td className="px-4 py-2.5 text-right">
                          {(a.oldValue || a.newValue) && (
                            <button type="button" className="inline-flex items-center gap-1 text-primary" aria-expanded={open === a.id} onClick={() => setOpen(open === a.id ? null : a.id)}>
                              Details <ChevronDown size={13} className={cx("transition-transform", open === a.id && "rotate-180")} />
                            </button>
                          )}
                        </td>
                      </tr>
                      {open === a.id && (
                        <tr className="bg-surface-2">
                          <td colSpan={6} className="px-4 py-3">
                            <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                              <Values title="Before" values={a.oldValue} />
                              <Values title="After" values={a.newValue} />
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
      {data && (
        <div className="mt-3">
          <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </div>
      )}
    </>
  );
}

function Values({ title, values }: { title: string; values: Record<string, unknown> | null }) {
  return (
    <div>
      <p className="mb-1 text-xs font-semibold text-muted uppercase">{title}</p>
      {!values ? (
        <p className="text-xs text-muted">Nothing recorded.</p>
      ) : (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs">
          {Object.entries(values).map(([k, v]) => (
            <Fragment key={k}>
              <dt className="text-muted">{k}</dt>
              <dd className="font-mono break-all">{String(v)}</dd>
            </Fragment>
          ))}
        </dl>
      )}
    </div>
  );
}
