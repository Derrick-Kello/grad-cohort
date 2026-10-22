"use client";

import { useRouter } from "next/navigation";
import { DataTable } from "@/components/data-table";
import { ErrorBox, Loading, PageHeader } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { Course } from "@/lib/types";

export default function LecturerCoursesPage() {
  const router = useRouter();
  const { data, error, mutate } = useApi<Course[]>("/lecturer/courses");
  return (
    <>
      <PageHeader title="My courses" description="Courses you teach. Open one to manage its sessions." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <DataTable
          rows={data}
          rowKey={(c) => c.id}
          onRowClick={(c) => router.push(`/lecturer/sessions?course=${c.id}`)}
          searchText={(c) => `${c.code} ${c.title}`}
          emptyTitle="No courses assigned"
          columns={[
            { key: "code", header: "Code", sortable: true, value: (c) => c.code, className: "font-medium" },
            { key: "title", header: "Title", sortable: true, value: (c) => c.title },
            { key: "semester", header: "Semester", sortable: true, value: (c) => c.semesterLabel },
            { key: "lecturers", header: "Lecturers", value: (c) => c.lecturers.map((l) => l.name).join(", "), hideOnMobile: true },
            { key: "students", header: "Students", sortable: true, value: (c) => c.studentCount, className: "tabular" },
            { key: "held", header: "Sessions held", sortable: true, value: (c) => c.sessionsHeld, className: "tabular" },
          ]}
        />
      )}
    </>
  );
}
