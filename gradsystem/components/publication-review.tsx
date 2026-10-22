"use client";

import { useEffect, useState } from "react";
import { ExternalLink, FileText } from "lucide-react";
import { api } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { useAction } from "@/lib/hooks";
import type { Publication } from "@/lib/types";
import { ConfirmDialog } from "./dialog";
import { PUB_TYPES } from "./publication-form";
import { useToast } from "./toast";
import { Banner, Button, Card, Checkbox, ErrorBox, KeyValue, StatusChip } from "./ui";

/** Librarian review screen: metadata, PDF preview, DOI link, checklist and decision (FR-PUB-05..07). */
export function PublicationReview({
  publication,
  onChanged,
  readOnly,
  canRevoke,
}: {
  publication: Publication;
  onChanged: (p?: Publication) => void;
  readOnly?: boolean;
  canRevoke?: boolean;
}) {
  const p = publication;
  const [pdf, setPdf] = useState<string | null>(null);
  const [decision, setDecision] = useState<"approve" | "changes" | "reject" | "revoke" | null>(null);
  const toast = useToast();
  const checklist = useAction(async (body: { venueVerified: boolean; authorshipVerified: boolean; indexingChecked: boolean }) =>
    api.put<Publication>(`/librarian/publications/${p.id}/checklist`, body),
  );
  const decide = useAction(async (kind: string, reason: string) => {
    const path =
      kind === "approve" ? "approve" : kind === "changes" ? "request-changes" : kind === "reject" ? "reject" : null;
    if (kind === "revoke") return api.post<Publication>(`/admin/publications/${p.id}/revoke`, { reason });
    return api.post<Publication>(`/librarian/publications/${p.id}/${path}`, kind === "approve" ? { note: reason || null } : { reason });
  });

  useEffect(() => {
    let cancelled = false;
    if (p.hasFile) {
      api
        .get<{ url: string }>(`/publications/${p.id}/file?inline=true`)
        .then((l) => !cancelled && setPdf(l.url))
        .catch(() => undefined);
    }
    return () => {
      cancelled = true;
    };
  }, [p.id, p.hasFile]);

  const toggle = async (key: "venueVerified" | "authorshipVerified" | "indexingChecked", value: boolean) => {
    const updated = await checklist.run({ venueVerified: p.venueVerified, authorshipVerified: p.authorshipVerified, indexingChecked: p.indexingChecked, [key]: value });
    if (updated) onChanged(updated);
  };
  const underReview = p.reviewStatus === "SUBMITTED" && !readOnly;
  const type = PUB_TYPES.find(([v]) => v === p.type)?.[1] ?? p.type;

  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-5">
      <div className="flex flex-col gap-5 xl:col-span-2">
        <Card title="Submission" actions={<StatusChip status={p.reviewStatus} />}>
          <div className="flex flex-col gap-4">
            <p className="font-semibold">{p.title}</p>
            <KeyValue
              items={[
                ["Student", `${p.studentName} (${p.studentNumber}, ${p.programmeCode})`],
                ["Type", type],
                ["Journal or conference", p.venueName],
                ["Authors", p.authors],
                ["Year", p.year],
                ["Volume / issue / pages", [p.volume, p.issue, p.pages].filter(Boolean).join(" / ") || null],
                ["ISSN / ISBN", p.issnIsbn],
                ["Status", p.pubStatus === "PUBLISHED" ? "Published" : "Accepted"],
                ["Submitted", p.submittedAt ? `${formatDate(p.submittedAt)} (${p.daysWaiting} days waiting)` : null],
              ]}
            />
            <div className="flex flex-wrap gap-3 text-sm">
              {p.doi && (
                <a href={`https://doi.org/${p.doi}`} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-primary hover:underline">
                  doi.org/{p.doi} <ExternalLink size={13} />
                </a>
              )}
              {p.url && (
                <a href={p.url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 font-medium text-primary hover:underline">
                  Publisher page <ExternalLink size={13} />
                </a>
              )}
            </div>
            {p.duplicateWarnings.map((w) => (
              <Banner key={w} tone="warn" title="Possible duplicate">
                {w}
              </Banner>
            ))}
            {p.reviewNote && <Banner tone="info" title="Review note">{p.reviewNote}</Banner>}
          </div>
        </Card>
        <Card title="Verification checklist">
          <div className="flex flex-col gap-3">
            <Checkbox label="Venue verified" checked={p.venueVerified} disabled={!underReview} onChange={(v) => toggle("venueVerified", v)} />
            <Checkbox label="Authorship verified" checked={p.authorshipVerified} disabled={!underReview} onChange={(v) => toggle("authorshipVerified", v)} />
            <Checkbox label="Indexing checked" checked={p.indexingChecked} disabled={!underReview} onChange={(v) => toggle("indexingChecked", v)} />
            <ErrorBox error={checklist.error} />
          </div>
        </Card>
        {underReview && (
          <div className="flex flex-wrap gap-2">
            <Button variant="primary" onClick={() => setDecision("approve")}>
              Approve
            </Button>
            <Button onClick={() => setDecision("changes")}>Request changes</Button>
            <Button variant="danger" onClick={() => setDecision("reject")}>
              Reject
            </Button>
          </div>
        )}
        {canRevoke && p.reviewStatus === "APPROVED" && (
          <div>
            <Button variant="danger" onClick={() => setDecision("revoke")}>
              Revoke approval
            </Button>
          </div>
        )}
      </div>
      <Card title="Full text" className="xl:col-span-3" padded={false}>
        {pdf ? (
          <iframe title={`PDF of ${p.title}`} src={pdf} className="h-[70vh] w-full rounded-b-[var(--radius)]" />
        ) : (
          <div className="flex flex-col items-center gap-2 py-16 text-sm text-muted">
            <FileText size={22} aria-hidden />
            {p.hasFile ? "Loading the PDF…" : "No PDF attached."}
          </div>
        )}
      </Card>

      <ConfirmDialog
        open={decision === "approve"}
        onClose={() => setDecision(null)}
        title="Approve this publication?"
        consequence={`It will be linked to ${p.studentName}'s record, shown in achievements and reports, and becomes read-only. ${p.studentName} and their supervisor are notified.`}
        reasonLabel="Note (optional)"
        reasonOptional
        confirmLabel="Approve"
        pending={decide.pending}
        error={decide.error}
        onConfirm={async (note) => {
          const r = await decide.run("approve", note);
          if (r) {
            toast("Publication approved");
            setDecision(null);
            onChanged(r);
          }
        }}
      />
      <ConfirmDialog
        open={decision === "changes"}
        onClose={() => setDecision(null)}
        title="Request changes?"
        consequence="The student can edit the record and resubmit it."
        reasonLabel="What needs to change"
        confirmLabel="Request changes"
        pending={decide.pending}
        error={decide.error}
        onConfirm={async (reason) => {
          const r = await decide.run("changes", reason);
          if (r) {
            toast("Changes requested");
            setDecision(null);
            onChanged(r);
          }
        }}
      />
      <ConfirmDialog
        open={decision === "reject"}
        onClose={() => setDecision(null)}
        title="Reject this publication?"
        consequence="Rejection is final. The student cannot resubmit this record."
        reasonLabel="Reason"
        confirmLabel="Reject"
        tone="danger"
        pending={decide.pending}
        error={decide.error}
        onConfirm={async (reason) => {
          const r = await decide.run("reject", reason);
          if (r) {
            toast("Publication rejected");
            setDecision(null);
            onChanged(r);
          }
        }}
      />
      <ConfirmDialog
        open={decision === "revoke"}
        onClose={() => setDecision(null)}
        title="Revoke this approval?"
        consequence="The publication is removed from the student's record and returned to them for changes. The decision is audited and the student is notified."
        reasonLabel="Reason"
        confirmLabel="Revoke approval"
        tone="danger"
        pending={decide.pending}
        error={decide.error}
        onConfirm={async (reason) => {
          const r = await decide.run("revoke", reason);
          if (r) {
            toast("Approval revoked");
            setDecision(null);
            onChanged(r);
          }
        }}
      />
    </div>
  );
}
