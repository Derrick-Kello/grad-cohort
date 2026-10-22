"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { ChevronLeft } from "lucide-react";
import { formatDate } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Programme, Publication } from "@/lib/types";
import { DataTable } from "./data-table";
import { PUB_TYPES } from "./publication-form";
import { PublicationReview } from "./publication-review";
import { Button, ErrorBox, Loading, PageHeader, Select, StatusChip } from "./ui";

/**
 * Review queue (FR-PUB-04): oldest first, filter by programme, type and status. Opening a row shows the review
 * screen. In "approved" mode it lists approved publications instead; admins can revoke from there.
 */
export function PublicationQueue({ mode, canRevoke, readOnly }: { mode: "queue" | "approved"; canRevoke?: boolean; readOnly?: boolean }) {
  const router = useRouter();
  const params = useSearchParams();
  const openId = params.get("id");
  const [programme, setProgramme] = useState("");
  const [type, setType] = useState("");
  const [status, setStatus] = useState("SUBMITTED");
  const q = new URLSearchParams();
  if (programme) q.set("programme", programme);
  if (type) q.set("type", type);
  if (status) q.set("status", status);
  const path = mode === "queue" ? `/librarian/publications/queue?${q}` : "/librarian/publications/approved";
  const { data, error, mutate } = useApi<Publication[]>(path);
  const programmes = useApi<Programme[]>("/reference/programmes");
  const opened = data?.find((p) => p.id === openId);
  const single = useApi<Publication>(openId && !opened ? `/publications/${openId}` : null);
  const current = opened ?? single.data;

  const open = (id: string | null) => {
    const p = new URLSearchParams(params.toString());
    if (id) p.set("id", id);
    else p.delete("id");
    router.push(`?${p.toString()}`);
  };

  if (openId) {
    return (
      <>
        <Button size="sm" className="mb-4" icon={<ChevronLeft size={14} />} onClick={() => open(null)}>
          Back to the list
        </Button>
        <ErrorBox error={single.error} />
        {current ? (
          <PublicationReview
            publication={current}
            readOnly={mode === "approved" || readOnly}
            canRevoke={canRevoke}
            onChanged={() => {
              void mutate();
              void single.mutate();
            }}
          />
        ) : (
          <Loading />
        )}
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={mode === "queue" ? "Review queue" : "Approved publications"}
        description={mode === "queue" ? "Oldest submissions first." : "Publications verified by the library and linked to student records."}
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(p) => p.id}
          onRowClick={(p) => open(p.id)}
          searchText={(p) => `${p.title} ${p.studentName} ${p.doi ?? ""} ${p.venueName ?? ""}`}
          searchPlaceholder="Search title, student, DOI"
          exportName={mode === "queue" ? "publication-queue" : "approved-publications"}
          emptyTitle={mode === "queue" ? "Nothing waiting for review" : "No approved publications yet"}
          toolbar={
            mode === "queue" ? (
              <div className="grid w-full grid-cols-1 gap-2 sm:w-auto sm:grid-cols-3">
                <Select aria-label="Programme" value={programme} onChange={(e) => setProgramme(e.target.value)}>
                  <option value="">All programmes</option>
                  {programmes.data?.map((p) => (
                    <option key={p.id} value={p.code}>
                      {p.code}
                    </option>
                  ))}
                </Select>
                <Select aria-label="Type" value={type} onChange={(e) => setType(e.target.value)}>
                  <option value="">All types</option>
                  {PUB_TYPES.map(([v, l]) => (
                    <option key={v} value={v}>
                      {l}
                    </option>
                  ))}
                </Select>
                <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
                  <option value="SUBMITTED">Awaiting review</option>
                  <option value="CHANGES_REQUESTED">Changes requested</option>
                  <option value="REJECTED">Rejected</option>
                  <option value="APPROVED">Approved</option>
                </Select>
              </div>
            ) : null
          }
          columns={[
            { key: "title", header: "Publication", sortable: true, value: (p) => p.title, render: (p) => <div className="max-w-md"><p className="font-medium">{p.title}</p><p className="text-xs text-muted">{p.venueName}{p.year ? ` (${p.year})` : ""}</p></div> },
            { key: "student", header: "Student", sortable: true, value: (p) => p.studentName, render: (p) => <div><p>{p.studentName}</p><p className="text-xs text-muted">{p.programmeCode}</p></div> },
            { key: "type", header: "Type", value: (p) => PUB_TYPES.find(([v]) => v === p.type)?.[1] ?? p.type, hideOnMobile: true },
            mode === "queue"
              ? { key: "waiting", header: "Days waiting", sortable: true, value: (p) => p.daysWaiting, className: "tabular" }
              : { key: "approved", header: "Approved", sortable: true, value: (p) => p.reviewedAt ?? "", render: (p) => formatDate(p.reviewedAt) },
            { key: "flags", header: "", render: (p) => (p.duplicateWarnings.length ? <StatusChip status="AT_RISK" label="Possible duplicate" /> : null) },
            { key: "status", header: "Status", value: (p) => p.reviewStatus, render: (p) => <StatusChip status={p.reviewStatus} /> },
          ]}
        />
      )}
    </>
  );
}
