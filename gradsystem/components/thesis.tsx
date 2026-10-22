"use client";

import { useState } from "react";
import { Check, ChevronDown, Download, ExternalLink, X } from "lucide-react";
import { download, openSigned } from "@/lib/api";
import { fileSize, formatDateTime } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { FormatReport, Thesis } from "@/lib/types";
import { CommentThread } from "./evidence-list";
import { Banner, Button, Card, Loading, StatusChip, cx } from "./ui";

const RULE_NAMES: Record<string, string> = {
  "FMT-PAGE": "Paper size",
  "FMT-MARGIN": "Margins",
  "FMT-FONT": "Body font and size",
  "FMT-SPACING": "Line spacing",
  "FMT-HEAD": "Heading styles",
  "FMT-PAGENUM": "Page numbering",
  "FMT-SECTIONS": "Required sections",
  "FMT-ABSTRACT": "Abstract length",
  "FMT-TOC": "Table of contents",
  "FMT-REFS": "References",
};

export const IN_PIPELINE = ["UPLOADED", "FORMAT_CHECKING", "FORMAT_PASSED", "SCREENING"];

/** Two automatic gates in order: format, then similarity / AI screening (section 11). */
function Gates({ t }: { t: Thesis }) {
  const formatState =
    t.status === "UPLOADED" || t.status === "FORMAT_CHECKING" ? "running" : t.status === "FORMAT_FAILED" ? "failed" : "passed";
  const screeningState =
    formatState !== "passed"
      ? "waiting"
      : t.status === "SCREENING" || t.status === "FORMAT_PASSED"
        ? "running"
        : t.status === "SCREENING_REJECTED" && !t.overrideReason
          ? "failed"
          : "passed";
  const acceptedState = t.status === "ACCEPTED_FOR_EXAMINATION" ? "passed" : "waiting";
  const steps: [string, string][] = [
    ["Format check", formatState],
    ["Similarity and AI screening", screeningState],
    ["Accepted for examination", acceptedState],
  ];
  return (
    <ol className="flex flex-col gap-2 sm:flex-row sm:items-center sm:gap-0">
      {steps.map(([label, state], i) => (
        <li key={label} className="flex items-center gap-2 sm:flex-1">
          <span
            className={cx(
              "flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold",
              state === "passed" && "bg-ok text-white dark:text-[#0b1a12]",
              state === "failed" && "bg-bad text-white dark:text-[#1a0806]",
              state === "running" && "bg-warn-bg text-warn",
              state === "waiting" && "bg-surface-2 text-faint",
            )}
            aria-hidden
          >
            {state === "passed" ? <Check size={13} /> : state === "failed" ? <X size={13} /> : i + 1}
          </span>
          <span className="text-sm">
            {label}
            <span className="sr-only">: {state}</span>
            {state === "running" && <span className="ml-1 text-xs text-warn">in progress</span>}
          </span>
          {i < steps.length - 1 && <span aria-hidden className="mx-3 hidden h-px flex-1 bg-border sm:block" />}
        </li>
      ))}
    </ol>
  );
}

export function FormatReportTable({ thesisId, versionNo }: { thesisId: string; versionNo: number }) {
  const { data } = useApi<FormatReport>(`/thesis/${thesisId}/format-report`);
  if (!data) return <Loading label="Loading the format report" />;
  if (data.results.length === 0) return <p className="text-sm text-muted">The format check has not finished yet.</p>;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted">
          {data.fileType === "PDF" ? "PDF uploads can only be checked for page size, margins, page count and section headings. Upload DOCX for the full check." : "Full check of the Word document."}
          {data.pageCount ? ` ${data.pageCount} pages.` : ""}
        </p>
        <Button size="sm" icon={<Download size={14} />} onClick={() => download(`/thesis/${thesisId}/format-report.pdf`, `format-report-v${versionNo}.pdf`)}>
          Download report
        </Button>
      </div>
      <ul className="flex flex-col divide-y divide-border rounded-lg border border-border">
        {data.results.map((r) => (
          <li key={r.ruleCode} className="flex gap-3 px-3 py-2.5 text-sm">
            <span className="mt-0.5 shrink-0">
              {r.skipped ? <StatusChip status="SKIPPED" label="Skipped" tone="grey" /> : r.passed ? <StatusChip status="PASSED" label="Pass" /> : <StatusChip status="FAILED" label="Fail" />}
            </span>
            <div className="min-w-0">
              <p className="font-medium">
                {RULE_NAMES[r.ruleCode] ?? r.ruleCode} <span className="text-xs font-normal text-faint">{r.ruleCode}</span>
              </p>
              <p className="text-muted">{r.detail}</p>
              {r.location && <p className="text-xs text-muted">Where: {r.location}</p>}
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function ScreeningResult({ t }: { t: Thesis }) {
  if (t.similarityPct === null || t.aiPct === null) {
    return <p className="text-sm text-muted">{t.status === "SCREENING" ? "Waiting for the screening provider." : "Screening runs after the format check passes."}</p>;
  }
  const over = (v: number, limit: number | null) => limit !== null && v > limit;
  return (
    <div className="flex flex-col gap-3 text-sm">
      <div className="grid grid-cols-2 gap-3 sm:max-w-md">
        <div className="rounded-lg bg-surface-2 p-3">
          <p className="text-xs text-muted">Similarity index</p>
          <p className={cx("tabular text-xl font-semibold", over(t.similarityPct, t.similarityLimit) ? "text-bad" : "text-ok")}>{t.similarityPct}%</p>
          <p className="text-xs text-muted">Limit {t.similarityLimit}%</p>
        </div>
        <div className="rounded-lg bg-surface-2 p-3">
          <p className="text-xs text-muted">AI-generated content</p>
          <p className={cx("tabular text-xl font-semibold", over(t.aiPct, t.aiLimit) ? "text-bad" : "text-ok")}>{t.aiPct}%</p>
          <p className="text-xs text-muted">Limit {t.aiLimit}%</p>
        </div>
      </div>
      {t.screeningManual && <p className="text-xs text-muted">Scores entered by the graduate school from the provider&apos;s report.</p>}
      <div className="flex flex-wrap gap-2">
        {t.hasProviderReport && (
          <Button size="sm" icon={<ExternalLink size={14} />} onClick={() => openSigned(`/thesis/${t.id}/provider-report`)}>
            Provider report
          </Button>
        )}
        {t.providerReportUrl && (
          <a className="inline-flex items-center gap-1 text-sm text-primary underline" href={t.providerReportUrl} target="_blank" rel="noreferrer">
            Full report <ExternalLink size={13} />
          </a>
        )}
      </div>
      {t.overrideReason && (
        <Banner tone="warn" title={`Rejection overridden by ${t.overrideByName ?? "the graduate school"}`}>
          {t.overrideReason}
        </Banner>
      )}
    </div>
  );
}

export function ThesisVersion({ t, defaultOpen, actions }: { t: Thesis; defaultOpen?: boolean; actions?: React.ReactNode }) {
  const [open, setOpen] = useState(!!defaultOpen);
  return (
    <Card
      title={`Version ${t.versionNo}: ${t.title}`}
      description={`${t.fileName} · ${fileSize(t.sizeBytes)} · uploaded ${formatDateTime(t.submittedAt)}`}
      actions={
        <>
          <StatusChip status={t.status} />
          <Button size="sm" variant="ghost" icon={<Download size={14} />} onClick={() => openSigned(`/thesis/${t.id}/file`)}>
            File
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Gates t={t} />
        {actions}
        <button type="button" onClick={() => setOpen(!open)} aria-expanded={open} className="inline-flex items-center gap-1 self-start text-sm font-medium text-primary">
          {open ? "Hide" : "Show"} reports <ChevronDown size={14} className={cx("transition-transform", open && "rotate-180")} />
        </button>
        {open && (
          <div className="flex flex-col gap-5">
            <section>
              <h3 className="mb-2 text-sm font-semibold">Format check</h3>
              <FormatReportTable thesisId={t.id} versionNo={t.versionNo} />
            </section>
            <section>
              <h3 className="mb-2 text-sm font-semibold">Screening</h3>
              <ScreeningResult t={t} />
            </section>
            <section>
              <h3 className="mb-2 text-sm font-semibold">Supervisor comments</h3>
              <CommentThread targetType="THESIS" targetId={t.id} />
            </section>
          </div>
        )}
      </div>
    </Card>
  );
}
