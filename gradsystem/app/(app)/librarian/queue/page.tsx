"use client";

import { Suspense } from "react";
import { PublicationQueue } from "@/components/publication-queue";
import { Loading } from "@/components/ui";

export default function ReviewQueuePage() {
  return (
    <Suspense fallback={<Loading />}>
      <PublicationQueue mode="queue" />
    </Suspense>
  );
}
