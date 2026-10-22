"use client";

import { useState } from "react";
import { mutate as globalMutate } from "swr";
import { Dialog } from "@/components/dialog";
import { CommentThread, EvidenceFiles } from "@/components/evidence-list";
import { FileInput } from "@/components/file-input";
import { useToast } from "@/components/toast";
import { Banner, Button, Card, Empty, ErrorBox, Loading, PageHeader, SelectField, StatusChip } from "@/components/ui";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/hooks";
import type { Evidence, Milestone } from "@/lib/types";

const KINDS = [
  ["SLIDES", "Slides"],
  ["REPORT", "Written report"],
  ["PANEL_SHEET", "Signed panel sheet"],
  ["PHOTO", "Photo"],
  ["OTHER", "Other"],
] as const;

const UPLOADABLE = ["SCHEDULED", "AWAITING_VERIFICATION", "RETURNED"];

export default function EvidencePage() {
  const { data, error, mutate } = useApi<Milestone[]>("/students/me/milestones");
  const [target, setTarget] = useState<{ milestone: Milestone; replaces?: Evidence } | null>(null);
  const relevant = data?.filter((m) => UPLOADABLE.includes(m.status) || m.status === "APPROVED") ?? [];

  return (
    <>
      <PageHeader
        title="Evidence"
        description="Attach proof that each presentation took place: slides, written report, signed panel sheet or photos. PDF, DOCX, PPTX, JPG or PNG, up to 25 MB each and 10 files per presentation. Files are virus-scanned before they are stored."
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && relevant.length === 0 && (
        <Card>
          <Empty title="No presentations need evidence yet">Evidence can be added once a presentation is scheduled.</Empty>
        </Card>
      )}
      <div className="flex flex-col gap-5">
        {relevant.map((m) => {
          const canUpload = UPLOADABLE.includes(m.status);
          return (
            <Card
              key={m.id}
              title={`${m.label}: ${m.title}`}
              actions={
                <>
                  <StatusChip status={m.status} />
                  {canUpload && (
                    <Button size="sm" variant="primary" onClick={() => setTarget({ milestone: m })}>
                      Upload evidence
                    </Button>
                  )}
                </>
              }
            >
              <div className="flex flex-col gap-3">
                {m.status === "RETURNED" && <Banner tone="warn" title="Returned by the graduate school">Read the comment below, then upload a corrected file.</Banner>}
                {m.status === "AWAITING_VERIFICATION" && m.evidenceRequired && m.evidenceCount === 0 && (
                  <Banner tone="warn" title="Evidence required">This presentation cannot be approved until at least one file is uploaded.</Banner>
                )}
                {m.status === "APPROVED" && <p className="text-sm text-muted">Approved. Evidence is now read-only.</p>}
                <EvidenceFiles milestoneId={m.id} readOnly={!canUpload} onReplace={(e) => setTarget({ milestone: m, replaces: e })} />
                <CommentThread targetType="MILESTONE" targetId={m.id} />
              </div>
            </Card>
          );
        })}
      </div>
      {target && (
        <UploadDialog
          milestone={target.milestone}
          replaces={target.replaces}
          onClose={() => setTarget(null)}
          onDone={() => {
            void mutate();
            void globalMutate(`/milestones/${target.milestone.id}/evidence`);
            setTarget(null);
          }}
        />
      )}
    </>
  );
}

function UploadDialog({ milestone, replaces, onClose, onDone }: { milestone: Milestone; replaces?: Evidence; onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [kind, setKind] = useState<string>(replaces?.kind ?? "SLIDES");
  const toast = useToast();
  const { run, pending, error } = useAction(async () => {
    const form = new FormData();
    form.append("file", file!);
    const params = new URLSearchParams({ kind });
    if (replaces) params.set("replaces", replaces.id);
    await api.upload(`/milestones/${milestone.id}/evidence?${params}`, form);
    return true;
  });
  return (
    <Dialog
      open
      onClose={onClose}
      title={replaces ? `Replace ${replaces.fileName}` : `Upload evidence for ${milestone.label}`}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            disabled={!file}
            onClick={async () => {
              if (await run()) {
                toast(milestone.status === "RETURNED" ? "Uploaded and resubmitted for verification" : "Evidence uploaded");
                onDone();
              }
            }}
          >
            Upload
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <SelectField label="What is this file?" value={kind} onChange={(e) => setKind(e.target.value)}>
          {KINDS.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </SelectField>
        <FileInput label="File" accept=".pdf,.docx,.pptx,.jpg,.png" maxMb={25} file={file} onChange={setFile} hint="PDF, DOCX, PPTX, JPG or PNG up to 25 MB." />
        {replaces && <p className="text-xs text-muted">The current file is kept as version {replaces.fileVersion}.</p>}
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
