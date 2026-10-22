"use client";

import { MilestoneTimeline } from "@/components/milestones";
import { Card, ErrorBox, Loading, PageHeader } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { Milestone } from "@/lib/types";

export default function MyPresentationsPage() {
  const { data, error, mutate } = useApi<Milestone[]>("/students/me/milestones");
  return (
    <>
      <PageHeader
        title="My presentations"
        description="Presentations run in a fixed order. Each one is scheduled by the graduate school, recorded by the secretary, and must be approved before the next one opens."
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card>
          <MilestoneTimeline milestones={data} showCalendar />
        </Card>
      )}
    </>
  );
}
