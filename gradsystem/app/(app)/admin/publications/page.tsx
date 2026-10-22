"use client";

import { Suspense, useState } from "react";
import { PublicationQueue } from "@/components/publication-queue";
import { Loading, Tabs } from "@/components/ui";

/** Admins read the library queue and can revoke an approval (FR-PUB-08). */
export default function AdminPublicationsPage() {
  const [tab, setTab] = useState<"approved" | "queue">("approved");
  return (
    <Suspense fallback={<Loading />}>
      <Tabs value={tab} onChange={setTab} items={[{ value: "approved", label: "Approved" }, { value: "queue", label: "In review" }]} />
      {tab === "approved" ? <PublicationQueue key="a" mode="approved" canRevoke /> : <PublicationQueue key="q" mode="queue" readOnly />}
    </Suspense>
  );
}
