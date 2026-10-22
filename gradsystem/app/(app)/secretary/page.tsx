"use client";

import Link from "next/link";
import { PresentationAgenda } from "@/components/presentation-list";
import { Card, ErrorBox, Loading, PageHeader, Stat } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { PresentationRow } from "@/lib/types";

type SecretaryDashboard = { today: PresentationRow[]; awaitingOutcome: PresentationRow[]; thisWeek: number };

export default function SecretaryDashboardPage() {
  const { data, error, mutate } = useApi<SecretaryDashboard>("/secretary/dashboard");
  return (
    <>
      <PageHeader title="Presentations office" description="Today's presentations and outcomes still to record." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-6">
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <Stat label="Today" value={data.today.length} />
            <Stat label="Next 7 days" value={data.thisWeek} />
            <Stat label="Outcomes to record" value={data.awaitingOutcome.length} tone={data.awaitingOutcome.length > 0 ? "warn" : undefined} />
          </div>
          <Card title="Today" actions={<Link href="/secretary/calendar" className="text-sm font-medium text-primary hover:underline">Calendar</Link>}>
            <PresentationAgenda rows={data.today} empty="No presentations today." />
          </Card>
          <Card title="Outcomes to record" actions={<Link href="/secretary/outcomes" className="text-sm font-medium text-primary hover:underline">Record outcomes</Link>}>
            <PresentationAgenda rows={data.awaitingOutcome} empty="All outcomes are recorded." />
          </Card>
        </div>
      )}
    </>
  );
}
