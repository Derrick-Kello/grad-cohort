"use client";

import { useRouter, useSearchParams } from "next/navigation";
import type { Course } from "@/lib/types";
import { Select } from "./ui";

/** Course selector that keeps the choice in the URL (?course=) so links and reloads keep it. */
export function useCourseParam(courses: Course[] | undefined): [Course | undefined, (id: string) => void] {
  const params = useSearchParams();
  const router = useRouter();
  const id = params.get("course");
  const course = courses?.find((c) => c.id === id) ?? courses?.[0];
  const set = (next: string) => {
    const p = new URLSearchParams(params.toString());
    p.set("course", next);
    router.replace(`?${p.toString()}`);
  };
  return [course, set];
}

export function CoursePicker({ courses, value, onChange }: { courses: Course[]; value?: Course; onChange: (id: string) => void }) {
  return (
    <Select aria-label="Course" value={value?.id ?? ""} onChange={(e) => onChange(e.target.value)} className="w-full sm:w-80">
      {courses.map((c) => (
        <option key={c.id} value={c.id}>
          {c.code} {c.title} ({c.semesterLabel})
        </option>
      ))}
    </Select>
  );
}
