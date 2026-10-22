"use client";

import { useRouter } from "next/navigation";
import { StudentTable } from "@/components/student-table";
import { ErrorBox, Loading, PageHeader } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { StudentSummary } from "@/lib/types";

export default function MyStudentsPage() {
  const router = useRouter();
  const { data, error, mutate } = useApi<StudentSummary[]>("/supervisor/students");
  return (
    <>
      <PageHeader title="My students" description="Open a student to see milestones, evidence, publications, thesis versions and attendance." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && <StudentTable rows={data} onOpen={(s) => router.push(`/supervisor/students/${s.id}`)} exportName="my-students" />}
    </>
  );
}
