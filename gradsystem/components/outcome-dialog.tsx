"use client";

import { useState } from "react";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useAction } from "@/lib/hooks";
import type { PresentationRow } from "@/lib/types";
import { Dialog } from "./dialog";
import { useToast } from "./toast";
import { Button, ErrorBox, TextField, TextareaField, cx } from "./ui";

const OUTCOMES = [
  ["PASSED", "Passed", "The milestone goes to the graduate school for verification."],
  ["FAILED", "Not passed", "The milestone reopens for a new attempt. Remarks are required."],
  ["NOT_HELD", "Did not take place", "The milestone reopens for rescheduling."],
] as const;

/** Records or corrects a panel outcome (FR-MIL-07, FR-MIL-13). */
export function OutcomeDialog({ row, correction, onClose, onDone }: { row: PresentationRow; correction?: boolean; onClose: () => void; onDone: () => void }) {
  const [outcome, setOutcome] = useState<string>(correction ? row.outcome ?? "PASSED" : "PASSED");
  const [remarks, setRemarks] = useState("");
  const [score, setScore] = useState("");
  const [reason, setReason] = useState("");
  const toast = useToast();
  const { run, pending, error } = useAction(async () =>
    api.post(`/secretary/attempts/${row.attemptId}/outcome${correction ? "/correct" : ""}`, {
      outcome,
      remarks,
      score: score ? Number(score) : null,
      reason: correction ? reason : null,
    }),
  );
  const remarksMissing = outcome === "FAILED" && !remarks.trim();
  return (
    <Dialog
      open
      onClose={onClose}
      title={correction ? "Correct the outcome" : "Record the outcome"}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            disabled={remarksMissing || (correction && !reason.trim())}
            onClick={async () => {
              if (await run()) {
                toast(correction ? "Outcome corrected" : "Outcome recorded");
                onDone();
              }
            }}
          >
            {correction ? "Save correction" : "Record outcome"}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="rounded-lg bg-surface-2 p-3 text-sm">
          <p className="font-medium">
            {row.studentName} ({row.studentNumber})
          </p>
          <p className="text-muted">
            Presentation {row.sequenceNo}: {row.milestoneTitle} · attempt {row.attemptNo} · {formatDateTime(row.scheduledStart)}
          </p>
        </div>
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium">Outcome</legend>
          {OUTCOMES.map(([value, label, help]) => (
            <label
              key={value}
              className={cx("flex cursor-pointer gap-3 rounded-lg border p-3 text-sm", outcome === value ? "border-primary bg-primary-soft" : "border-border")}
            >
              <input type="radio" name="outcome" value={value} checked={outcome === value} onChange={() => setOutcome(value)} className="mt-1 accent-[var(--primary)]" />
              <span>
                <span className="font-medium">{label}</span>
                <span className="block text-muted">{help}</span>
              </span>
            </label>
          ))}
        </fieldset>
        <TextareaField
          label="Panel remarks"
          value={remarks}
          onChange={(e) => setRemarks(e.target.value)}
          required={outcome === "FAILED"}
          error={remarksMissing && remarks.length > 0 ? "Remarks are required." : null}
          rows={3}
        />
        <TextField label="Score (optional)" inputMode="decimal" value={score} onChange={(e) => setScore(e.target.value)} hint="0 to 100" />
        {correction && <TextareaField label="Reason for the correction" value={reason} onChange={(e) => setReason(e.target.value)} required rows={2} />}
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
