"use client";

import { useRouter } from "next/navigation";
import { PresentationAgenda } from "@/components/presentation-list";
import { StudentTable } from "@/components/student-table";
import { Card, ErrorBox, Loading, PageHeader, Stat } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { PresentationRow, StudentSummary } from "@/lib/types";

type SupervisorDashboard = { students: StudentSummary[]; upcoming: PresentationRow[]; byStatus: Record<string, number>; awaitingVerification: number };

export default function SupervisorDashboardPage() {
  const router = useRouter();
  const { data, error, mutate } = useApi<SupervisorDashboard>("/supervisor/dashboard");
  return (
    <>
      <PageHeader title="Supervision" description="Your supervisees' progress, upcoming presentations and anything that needs attention." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat label="Supervisees" value={data.students.length} />
            <Stat label="On track" value={data.byStatus.ACTIVE ?? 0} tone="ok" />
            <Stat label="At risk or flagged" value={(data.byStatus.AT_RISK ?? 0) + (data.byStatus.FLAGGED_FOR_EXPULSION ?? 0)} tone={(data.byStatus.AT_RISK ?? 0) + (data.byStatus.FLAGGED_FOR_EXPULSION ?? 0) > 0 ? "warn" : undefined} />
            <Stat label="Awaiting verification" value={data.awaitingVerification} />
          </div>
          <Card title="Upcoming presentations (30 days)">
            <PresentationAgenda rows={data.upcoming} empty="No presentations in the next 30 days." />
          </Card>
          <section>
            <h2 className="mb-3 text-base font-semibold">Supervisees</h2>
            <StudentTable rows={data.students} onOpen={(s) => router.push(`/supervisor/students/${s.id}`)} />
          </section>
        </div>
      )}
    </>
  );
}
