"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { StudentTable } from "@/components/student-table";
import { Card, ErrorBox, Loading, PageHeader, Stat } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { StudentSummary } from "@/lib/types";

type AdminDashboard = {
  studentsByStatus: Record<string, number>;
  verificationQueue: number;
  oldestVerificationDays: number;
  presentationsThisWeek: number;
  awaitingOutcome: number;
  publicationsAwaitingReview: number;
  thesesInPipeline: number;
  thesesNeedingManualScreening: number;
  schedulable: number;
  atRisk: StudentSummary[];
};

function Tile({ href, label, value, hint, tone }: { href: string; label: string; value: number | string; hint?: string; tone?: "warn" | "bad" | "ok" }) {
  return (
    <Link href={href} className="block rounded-[var(--radius)] focus-visible:outline-2">
      <Stat label={label} value={value} hint={hint} tone={tone} />
    </Link>
  );
}

export default function AdminDashboardPage() {
  const router = useRouter();
  const { data, error, mutate } = useApi<AdminDashboard>("/admin/dashboard");
  const s = data?.studentsByStatus ?? {};
  const total = Object.values(s).reduce((a, b) => a + b, 0);
  return (
    <>
      <PageHeader title="Graduate school overview" description="What needs action today, and where every student stands." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Tile href="/admin/verification" label="Verification queue" value={data.verificationQueue} hint={data.verificationQueue ? `Oldest ${data.oldestVerificationDays} days` : "Clear"} tone={data.oldestVerificationDays > 5 ? "warn" : undefined} />
            <Tile href="/admin/scheduling" label="Ready to schedule" value={data.schedulable} />
            <Tile href="/admin/scheduling" label="Presentations this week" value={data.presentationsThisWeek} />
            <Tile href="/admin/scheduling" label="Outcomes not recorded" value={data.awaitingOutcome} tone={data.awaitingOutcome ? "warn" : undefined} />
            <Tile href="/admin/publications" label="Publications in review" value={data.publicationsAwaitingReview} />
            <Tile href="/admin/theses" label="Theses being checked" value={data.thesesInPipeline} hint={data.thesesNeedingManualScreening ? `${data.thesesNeedingManualScreening} need manual screening` : undefined} tone={data.thesesNeedingManualScreening ? "warn" : undefined} />
            <Tile href="/admin/at-risk" label="At risk" value={s.AT_RISK ?? 0} tone={(s.AT_RISK ?? 0) > 0 ? "warn" : undefined} />
            <Tile href="/admin/at-risk" label="Flagged for expulsion" value={s.FLAGGED_FOR_EXPULSION ?? 0} tone={(s.FLAGGED_FOR_EXPULSION ?? 0) > 0 ? "bad" : undefined} />
          </div>
          <Card title="Students by status" description={`${total} students`}>
            <ul className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-3 lg:grid-cols-6">
              {[
                ["ACTIVE", "On track"],
                ["AT_RISK", "At risk"],
                ["FLAGGED_FOR_EXPULSION", "Flagged"],
                ["COMPLETED", "Completed"],
                ["EXPELLED", "Expelled"],
                ["WITHDRAWN", "Withdrawn"],
              ].map(([k, label]) => (
                <li key={k} className="rounded-lg bg-surface-2 p-3">
                  <p className="text-xs text-muted">{label}</p>
                  <p className="tabular text-xl font-semibold">{s[k] ?? 0}</p>
                </li>
              ))}
            </ul>
          </Card>
          {data.atRisk.length > 0 && (
            <section>
              <div className="mb-3 flex items-center justify-between">
                <h2 className="text-base font-semibold">Students needing attention</h2>
                <Link href="/admin/at-risk" className="text-sm font-medium text-primary hover:underline">
                  At-risk and expulsion
                </Link>
              </div>
              <StudentTable rows={data.atRisk} onOpen={(st) => router.push(`/admin/students/${st.id}`)} />
            </section>
          )}
        </div>
      )}
    </>
  );
}
