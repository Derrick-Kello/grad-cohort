"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Loading } from "@/components/ui";
import { homeFor, useAuth } from "@/lib/auth";

export default function Home() {
  const { status, view } = useAuth();
  const router = useRouter();
  useEffect(() => {
    if (status === "anonymous") router.replace("/login");
    if (status === "signed-in") router.replace(homeFor(view));
  }, [status, view, router]);
  return (
    <div className="flex min-h-screen items-center justify-center">
      <Loading label="Loading" />
    </div>
  );
}
