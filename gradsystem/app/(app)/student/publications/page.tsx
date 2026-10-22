"use client";

import { useState } from "react";
import { Download, Plus } from "lucide-react";
import { ConfirmDialog, Dialog } from "@/components/dialog";
import { FileInput } from "@/components/file-input";
import { PUB_TYPES, PublicationForm } from "@/components/publication-form";
import { useToast } from "@/components/toast";
import { Banner, Button, Card, Empty, ErrorBox, Loading, PageHeader, StatusChip } from "@/components/ui";
import { api, openSigned } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Publication } from "@/lib/types";

const typeLabel = (t: string) => PUB_TYPES.find(([v]) => v === t)?.[1] ?? t;

export default function StudentPublicationsPage() {
  const { data, error, mutate } = useApi<Publication[]>("/publications");
  const [editing, setEditing] = useState<Publication | "new" | null>(null);
  const [uploading, setUploading] = useState<Publication | null>(null);
  const [submitting, setSubmitting] = useState<Publication | null>(null);
  const [deleting, setDeleting] = useState<Publication | null>(null);
  const toast = useToast();
  const submit = useAction(async (p: Publication) => api.post<Publication>(`/publications/${p.id}/submit`));
  const remove = useAction(async (p: Publication) => {
    await api.del(`/publications/${p.id}`);
    return true;
  });

  return (
    <>
      <PageHeader
        title="Publications"
        description="A publication counts on your record only after the library verifies it. Drafts are visible only to you."
        actions={
          <Button variant="primary" icon={<Plus size={16} />} onClick={() => setEditing("new")}>
            Add publication
          </Button>
        }
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && data.length === 0 && (
        <Card>
          <Empty title="No publications yet" action={<Button onClick={() => setEditing("new")}>Add your first publication</Button>}>
            Add accepted or published papers, then request a library review.
          </Empty>
        </Card>
      )}
      <div className="flex flex-col gap-4">
        {data?.map((p) => {
          const editable = p.reviewStatus === "DRAFT" || p.reviewStatus === "CHANGES_REQUESTED";
          return (
            <Card key={p.id}>
              <div className="flex flex-col gap-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold">{p.title}</p>
                    <p className="text-sm text-muted">
                      {typeLabel(p.type)}
                      {p.venueName ? ` · ${p.venueName}` : ""}
                      {p.year ? ` · ${p.year}` : ""}
                      {p.doi ? ` · DOI ${p.doi}` : ""}
                    </p>
                    <p className="text-sm text-muted">{p.authors}</p>
                  </div>
                  <StatusChip status={p.reviewStatus} />
                </div>
                {p.reviewNote && (
                  <Banner tone={p.reviewStatus === "APPROVED" ? "ok" : p.reviewStatus === "REJECTED" ? "bad" : "warn"} title="Library note">
                    {p.reviewNote}
                  </Banner>
                )}
                {p.duplicateWarnings.map((w) => (
                  <Banner key={w} tone="warn">
                    {w}
                  </Banner>
                ))}
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  {p.hasFile ? (
                    <Button size="sm" variant="ghost" icon={<Download size={14} />} onClick={() => openSigned(`/publications/${p.id}/file`)}>
                      {p.fileName}
                    </Button>
                  ) : (
                    <span className="text-muted">No full-text PDF yet.</span>
                  )}
                  {p.submittedAt && <span className="text-muted">Submitted {formatDate(p.submittedAt)}</span>}
                  {p.reviewedAt && <span className="text-muted">Reviewed {formatDate(p.reviewedAt)}{p.reviewedByName ? ` by ${p.reviewedByName}` : ""}</span>}
                </div>
                {editable && (
                  <div className="flex flex-wrap gap-2 border-t border-border pt-3">
                    <Button size="sm" onClick={() => setEditing(p)}>
                      Edit
                    </Button>
                    <Button size="sm" onClick={() => setUploading(p)}>
                      {p.hasFile ? "Replace PDF" : "Upload PDF"}
                    </Button>
                    <Button size="sm" variant="primary" onClick={() => setSubmitting(p)}>
                      {p.reviewStatus === "CHANGES_REQUESTED" ? "Resubmit for review" : "Request review"}
                    </Button>
                    {p.reviewStatus === "DRAFT" && (
                      <Button size="sm" variant="ghost" onClick={() => setDeleting(p)}>
                        Delete draft
                      </Button>
                    )}
                  </div>
                )}
              </div>
            </Card>
          );
        })}
      </div>

      {editing && (
        <PublicationForm
          publication={editing === "new" ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            toast("Draft saved");
            void mutate();
          }}
        />
      )}
      {uploading && <UploadPdf publication={uploading} onClose={() => setUploading(null)} onDone={() => { setUploading(null); void mutate(); }} />}
      <ConfirmDialog
        open={!!submitting}
        onClose={() => setSubmitting(null)}
        title="Request a library review?"
        consequence="The library will verify the venue, authorship and indexing. You cannot edit the record while it is under review."
        confirmLabel="Request review"
        pending={submit.pending}
        error={submit.error}
        onConfirm={async () => {
          if (submitting && (await submit.run(submitting))) {
            toast("Sent to the library");
            setSubmitting(null);
            void mutate();
          }
        }}
      />
      <ConfirmDialog
        open={!!deleting}
        onClose={() => setDeleting(null)}
        title="Delete this draft?"
        consequence="The draft and its PDF are removed. This cannot be undone."
        confirmLabel="Delete draft"
        tone="danger"
        pending={remove.pending}
        error={remove.error}
        onConfirm={async () => {
          if (deleting && (await remove.run(deleting))) {
            setDeleting(null);
            void mutate();
          }
        }}
      />
    </>
  );
}

function UploadPdf({ publication, onClose, onDone }: { publication: Publication; onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const { run, pending, error } = useAction(async () => {
    const form = new FormData();
    form.append("file", file!);
    return api.upload<Publication>(`/publications/${publication.id}/file`, form);
  });
  return (
    <Dialog
      open
      onClose={onClose}
      title="Upload full-text PDF"
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button variant="primary" loading={pending} disabled={!file} onClick={async () => (await run()) && onDone()}>
            Upload
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <FileInput label="PDF" accept=".pdf" maxMb={25} file={file} onChange={setFile} hint="PDF up to 25 MB." />
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
