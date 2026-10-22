"use client";

import { Award, BookOpen, GraduationCap, Presentation } from "lucide-react";
import { formatDate } from "@/lib/format";
import type { Achievement } from "@/lib/types";
import { Empty } from "./ui";

const ICON = { MILESTONE: Presentation, PUBLICATION: BookOpen, THESIS: GraduationCap } as const;

/** Every approved accomplishment in date order, with who approved it (FR-ACH-01) and badges (FR-ACH-02). */
export function AchievementTimeline({ items }: { items: Achievement[] }) {
  if (items.length === 0) return <Empty title="No approved achievements yet">Approved presentations, publications and thesis acceptance appear here.</Empty>;
  return (
    <ol className="flex flex-col gap-4">
      {items.map((a, i) => {
        const Icon = ICON[a.type as keyof typeof ICON] ?? Award;
        return (
          <li key={`${a.type}-${i}`} className="flex gap-4">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-ok-bg text-ok" aria-hidden>
              <Icon size={17} />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <p className="font-medium">{a.title}</p>
                {a.badge && (
                  <span className="inline-flex items-center gap-1 rounded-full border border-warn/40 bg-warn-bg px-2 py-0.5 text-xs font-medium text-warn">
                    <Award size={12} aria-hidden /> {a.badge}
                  </span>
                )}
              </div>
              <p className="text-sm text-muted">{a.detail}</p>
              <p className="text-xs text-muted">
                {formatDate(a.at)}
                {a.approvedBy ? ` · approved by ${a.approvedBy}` : ""}
              </p>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
