"use client";

import { Download, Eye } from "lucide-react";
import { openSigned } from "@/lib/api";
import { fileSize, formatDateTime, pretty } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Comment, Evidence } from "@/lib/types";
import { Button, StatusChip, cx } from "./ui";

/** Evidence files for one milestone with preview and download through signed links (FR-EVD-03). */
export function EvidenceFiles({ milestoneId, onReplace, readOnly }: { milestoneId: string; onReplace?: (e: Evidence) => void; readOnly?: boolean }) {
  const { data } = useApi<Evidence[]>(`/milestones/${milestoneId}/evidence`);
  if (!data) return null;
  if (data.length === 0) return <p className="text-sm text-muted">No files uploaded yet.</p>;
  return (
    <ul className="flex flex-col divide-y divide-border rounded-lg border border-border">
      {data.map((f) => (
        <li key={f.id} className={cx("flex flex-col gap-2 px-3 py-2.5 text-sm sm:flex-row sm:items-center", !f.current && "opacity-60")}>
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">{f.fileName}</p>
            <p className="text-xs text-muted">
              {pretty(f.kind)} · {fileSize(f.sizeBytes)} · version {f.fileVersion} · {formatDateTime(f.uploadedAt)}
              {!f.current && " · replaced"}
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {f.scanStatus !== "CLEAN" && <StatusChip status={f.scanStatus} />}
            {f.previewable && f.scanStatus === "CLEAN" && (
              <Button size="sm" variant="ghost" icon={<Eye size={14} />} onClick={() => openSigned(`/evidence/${f.id}/download?inline=true`)}>
                Preview
              </Button>
            )}
            {f.scanStatus === "CLEAN" && (
              <Button size="sm" variant="ghost" icon={<Download size={14} />} onClick={() => openSigned(`/evidence/${f.id}/download`)}>
                Download
              </Button>
            )}
            {!readOnly && f.current && onReplace && (
              <Button size="sm" onClick={() => onReplace(f)}>
                Replace
              </Button>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}

export function CommentThread({ targetType, targetId }: { targetType: "MILESTONE" | "EVIDENCE" | "THESIS"; targetId: string }) {
  const { data } = useApi<Comment[]>(`/comments?targetType=${targetType}&targetId=${targetId}`);
  if (!data || data.length === 0) return null;
  return (
    <ul className="flex flex-col gap-2">
      {data.map((c) => (
        <li key={c.id} className="rounded-lg bg-surface-2 px-3 py-2 text-sm">
          <p className="text-xs text-muted">
            {c.authorName} · {formatDateTime(c.createdAt)}
          </p>
          <p className="mt-0.5 whitespace-pre-wrap">{c.body}</p>
        </li>
      ))}
    </ul>
  );
}
