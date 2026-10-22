"use client";

import { ProgressSummary, type StudentDashboard } from "@/components/progress-summary";
import { ErrorBox, Loading, PageHeader } from "@/components/ui";
import { useAuth } from "@/lib/auth";
import { useApi } from "@/lib/hooks";

export default function StudentDashboardPage() {
  const { me } = useAuth();
  const { data, error, mutate } = useApi<StudentDashboard>("/students/me/dashboard");
  return (
    <>
      <PageHeader
        title={`Welcome, ${me?.firstName ?? ""}`}
        description={data ? `${data.student.programmeName} · ${data.student.number}` : undefined}
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && <ProgressSummary data={data} audience="student" />}
    </>
  );
}
