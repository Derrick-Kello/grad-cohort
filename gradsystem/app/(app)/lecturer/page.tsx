"use client";

import Link from "next/link";
import { Card, Empty, ErrorBox, Loading, PageHeader, StatusChip } from "@/components/ui";
import { useApi } from "@/lib/hooks";
import type { Course } from "@/lib/types";

type Summary = { course: Course; belowThreshold: number; belowWarning: number };

export default function LecturerDashboardPage() {
  const { data, error, mutate } = useApi<Summary[]>("/lecturer/dashboard");
  return (
    <>
      <PageHeader title="Teaching" description="Your courses this semester, with students whose attendance puts their exam eligibility at risk." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data?.length === 0 && (
        <Card>
          <Empty title="No courses assigned">The graduate school assigns lecturers to courses.</Empty>
        </Card>
      )}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {data?.map(({ course, belowThreshold, belowWarning }) => (
          <Card key={course.id} title={`${course.code} ${course.title}`} description={course.semesterLabel}>
            <dl className="grid grid-cols-3 gap-3 text-sm">
              <div>
                <dt className="text-xs text-muted">Students</dt>
                <dd className="tabular text-xl font-semibold">{course.studentCount}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Sessions held</dt>
                <dd className="tabular text-xl font-semibold">{course.sessionsHeld}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted">Not eligible</dt>
                <dd className="tabular text-xl font-semibold text-bad">{belowThreshold}</dd>
              </div>
            </dl>
            {belowWarning > 0 && (
              <p className="mt-3 text-sm">
                <StatusChip status="AT_RISK" label={`${belowWarning} close to the threshold`} />
              </p>
            )}
            <div className="mt-4 flex flex-wrap gap-3 text-sm font-medium">
              <Link className="text-primary hover:underline" href={`/lecturer/sessions?course=${course.id}`}>
                Sessions
              </Link>
              <Link className="text-primary hover:underline" href={`/lecturer/eligibility?course=${course.id}`}>
                Eligibility list
              </Link>
            </div>
          </Card>
        ))}
      </div>
    </>
  );
}
