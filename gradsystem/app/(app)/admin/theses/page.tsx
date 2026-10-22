"use client";

import { useState } from "react";
import { ChevronLeft } from "lucide-react";
import { DataTable } from "@/components/data-table";
import { ConfirmDialog, Dialog } from "@/components/dialog";
import { FileInput } from "@/components/file-input";
import { IN_PIPELINE, ThesisVersion } from "@/components/thesis";
import { useToast } from "@/components/toast";
import { Banner, Button, ErrorBox, Loading, PageHeader, Select, StatusChip, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime, pretty } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Thesis } from "@/lib/types";

const STATUSES = ["UPLOADED", "FORMAT_CHECKING", "FORMAT_FAILED", "FORMAT_PASSED", "SCREENING", "SCREENING_REJECTED", "SCREENING_PASSED", "ACCEPTED_FOR_EXAMINATION"];

export default function AdminThesesPage() {
  const [status, setStatus] = useState("");
  const { data, error, mutate } = useApi<Thesis[]>(`/admin/thesis${status ? `?status=${status}` : ""}`, {
    refreshInterval: (d) => (d?.some((t) => IN_PIPELINE.includes(t.status)) ? 4000 : 0),
  });
  const [openId, setOpenId] = useState<string | null>(null);
  const open = data?.find((t) => t.id === openId);

  if (open) {
    return (
      <>
        <Button size="sm" className="mb-4" icon={<ChevronLeft size={14} />} onClick={() => setOpenId(null)}>
          All theses
        </Button>
        <PageHeader title={`${open.studentName}: thesis version ${open.versionNo}`} description={`${open.studentNumber} · ${open.programmeCode}`} />
        <ThesisVersion t={open} defaultOpen actions={<AdminActions t={open} onDone={() => mutate()} />} />
      </>
    );
  }
  return (
    <>
      <PageHeader title="Theses" description="Every version goes through the format check and then similarity and AI screening. Enter results by hand when the provider is unavailable; override a rejection only with a written justification." />
      <div className="mb-4">
        <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)} className="w-64">
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{pretty(s)}</option>)}
        </Select>
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(t) => t.id}
          onRowClick={(t) => setOpenId(t.id)}
          exportName="theses"
          searchText={(t) => `${t.studentName} ${t.studentNumber} ${t.title}`}
          emptyTitle="No theses submitted"
          columns={[
            { key: "student", header: "Student", sortable: true, value: (t) => t.studentName, render: (t) => <div><p className="font-medium">{t.studentName}</p><p className="text-xs text-muted">{t.studentNumber} · {t.programmeCode}</p></div> },
            { key: "version", header: "Version", sortable: true, value: (t) => t.versionNo, className: "tabular" },
            { key: "title", header: "Title", value: (t) => t.title, hideOnMobile: true },
            { key: "scores", header: "Similarity / AI", value: (t) => (t.similarityPct === null ? "" : `${t.similarityPct}% / ${t.aiPct}%`) },
            { key: "submitted", header: "Uploaded", sortable: true, value: (t) => t.submittedAt, render: (t) => formatDateTime(t.submittedAt) },
            { key: "status", header: "Status", sortable: true, value: (t) => t.status, render: (t) => <StatusChip status={t.status} /> },
          ]}
        />
      )}
    </>
  );
}

function AdminActions({ t, onDone }: { t: Thesis; onDone: () => void }) {
  const [manual, setManual] = useState(false);
  const [override, setOverride] = useState(false);
  const toast = useToast();
  const doOverride = useAction(async (reason: string) => api.post(`/admin/thesis/${t.id}/override`, { reason }));
  const canManual = t.status === "SCREENING" || t.status === "FORMAT_PASSED";
  return (
    <>
      {canManual && (
        <Banner tone="info" title="Waiting for the screening provider" action={<Button size="sm" onClick={() => setManual(true)}>Enter result manually</Button>}>
          If the provider API is unavailable, upload its PDF report and enter both scores.
        </Banner>
      )}
      {t.status === "SCREENING_REJECTED" && (
        <Banner tone="bad" title="Rejected at screening" action={<Button size="sm" onClick={() => setOverride(true)}>Override rejection</Button>}>
          Use an override only when the matches are legitimate, for example the student&apos;s own approved publications.
        </Banner>
      )}
      {manual && <ManualScreening t={t} onClose={() => setManual(false)} onDone={() => { setManual(false); onDone(); }} />}
      <ConfirmDialog
        open={override}
        onClose={() => setOverride(false)}
        title="Override the screening rejection?"
        consequence={`Version ${t.versionNo} will be accepted for examination despite similarity ${t.similarityPct}% and AI score ${t.aiPct}%. The override and your justification are audited, and the student, supervisor and admins are notified.`}
        reasonLabel="Written justification"
        confirmLabel="Override and accept"
        tone="danger"
        pending={doOverride.pending}
        error={doOverride.error}
        onConfirm={async (reason) => {
          if (await doOverride.run(reason)) {
            toast("Rejection overridden");
            setOverride(false);
            onDone();
          }
        }}
      />
    </>
  );
}

function ManualScreening({ t, onClose, onDone }: { t: Thesis; onClose: () => void; onDone: () => void }) {
  const [similarity, setSimilarity] = useState("");
  const [ai, setAi] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const toast = useToast();
  const { run, pending, error } = useAction(async () => {
    const form = new FormData();
    form.append("report", file!);
    return api.upload(`/admin/thesis/${t.id}/manual-screening?similarityPct=${encodeURIComponent(similarity)}&aiPct=${encodeURIComponent(ai)}`, form);
  });
  return (
    <Dialog
      open
      onClose={onClose}
      title="Enter the screening result"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} disabled={!similarity || !ai || !file} onClick={async () => { if (await run()) { toast("Screening result saved"); onDone(); } }}>
            Save result
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-2 gap-3">
          <TextField label="Similarity index (%)" inputMode="decimal" value={similarity} onChange={(e) => setSimilarity(e.target.value)} required />
          <TextField label="AI score (%)" inputMode="decimal" value={ai} onChange={(e) => setAi(e.target.value)} required />
        </div>
        <p className="text-xs text-muted">The same limits apply as for automatic screening.</p>
        <FileInput label="Provider's PDF report" accept=".pdf" maxMb={50} file={file} onChange={setFile} />
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
