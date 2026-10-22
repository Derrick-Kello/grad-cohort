"use client";

import { Suspense } from "react";
import { CoursePicker, useCourseParam } from "@/components/course-picker";
import { EligibilityPanel } from "@/components/eligibility-panel";
import { Card, Empty, ErrorBox, Loading, PageHeader } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { Course } from "@/lib/types";

export default function EligibilityListsPage() {
  return (
    <Suspense fallback={<Loading />}>
      <Eligibility />
    </Suspense>
  );
}

function Eligibility() {
  const { data, error } = useApi<Course[]>("/lecturer/courses");
  const [course, setCourse] = useCourseParam(data);
  return (
    <>
      <PageHeader
        title="Exam eligibility lists"
        description="Students below the attendance threshold cannot sit the course exam. Lists come only from recorded attendance."
      />
      <ErrorBox error={error} />
      {!data && !error && <Loading />}
      {data?.length === 0 && (
        <Card>
          <Empty title="No courses assigned" />
        </Card>
      )}
      {data && course && (
        <div className="flex flex-col gap-4">
          <CoursePicker courses={data} value={course} onChange={setCourse} />
          <EligibilityPanel key={course.id} courseId={course.id} courseCode={course.code} />
        </div>
      )}
    </>
  );
}
