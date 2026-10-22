"use client";

import { AttendanceTable } from "@/components/attendance-table";
import { ErrorBox, Loading, PageHeader } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { StudentAttendance } from "@/lib/types";

export default function StudentAttendancePage() {
  const { data, error, mutate } = useApi<StudentAttendance[]>("/students/me/attendance");
  const threshold = data?.[0]?.threshold ?? 70;
  return (
    <>
      <PageHeader
        title="Attendance"
        description={`You need at least ${threshold}% attendance in a course to sit its exam. Late counts as attended; excused sessions are not counted.`}
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && <AttendanceTable rows={data} />}
    </>
  );
}
