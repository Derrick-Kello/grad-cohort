"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { StudentDetail } from "@/components/student-detail";

export default function SuperviseePage() {
  const { id } = useParams<{ id: string }>();
  return (
    <>
      <Link href="/supervisor/students" className="mb-4 inline-flex items-center gap-1 text-sm text-primary hover:underline">
        <ChevronLeft size={15} /> My students
      </Link>
      <StudentDetail studentId={id} canComment />
    </>
  );
}
