"use client";

import Link from "next/link";
import { Card, Empty, ErrorBox, Loading, PageHeader, StatusChip } from "@/components/ui";
import { formatDate } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { Course, Session } from "@/lib/types";

/** Sessions that still need attendance, or can still be edited, across the lecturer's courses. */
export default function AttendanceOverviewPage() {
  const { data, error, mutate } = useApi<Course[]>("/lecturer/courses");
  return (
    <>
      <PageHeader title="Attendance" description="Sessions waiting for a roster, and sessions you can still edit (within 7 days)." />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data?.length === 0 && (
        <Card>
          <Empty title="No courses assigned" />
        </Card>
      )}
      <div className="flex flex-col gap-4">
        {data?.map((c) => (
          <CourseSessions key={c.id} course={c} />
        ))}
      </div>
    </>
  );
}

function CourseSessions({ course }: { course: Course }) {
  const { data } = useApi<Session[]>(`/lecturer/courses/${course.id}/sessions`);
  const open = data?.filter((s) => !s.future && (!s.attendanceTaken || s.lecturerCanEdit)) ?? [];
  return (
    <Card title={`${course.code} ${course.title}`} actions={<Link className="text-sm font-medium text-primary hover:underline" href={`/lecturer/sessions?course=${course.id}`}>All sessions</Link>}>
      {!data ? (
        <Loading />
      ) : open.length === 0 ? (
        <p className="text-sm text-muted">Nothing to mark. Create a session from the Sessions page.</p>
      ) : (
        <ul className="flex flex-col divide-y divide-border">
          {open.map((s) => (
            <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
              <span>
                <span className="font-medium">{formatDate(s.heldOn)}</span> {s.startsAt.slice(0, 5)} {s.topic ? `· ${s.topic}` : ""}
              </span>
              <span className="flex items-center gap-3">
                {s.attendanceTaken ? <StatusChip status="APPROVED" label="Taken" /> : <StatusChip status="AT_RISK" label="Not taken" />}
                <Link className="font-medium text-primary hover:underline" href={`/lecturer/attendance/${s.id}`}>
                  {s.attendanceTaken ? "Edit" : "Mark attendance"}
                </Link>
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
