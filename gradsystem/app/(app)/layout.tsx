import { Suspense } from "react";
import { AppShell } from "@/components/shell";
import { Loading } from "@/components/ui";

// The shell reads the URL and the signed-in user, which exist only in the browser, so it streams in
// behind Suspense instead of blocking prerendering.
export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense
      fallback={
        <div className="flex min-h-screen items-center justify-center">
          <Loading label="Opening your workspace" />
        </div>
      }
    >
      <AppShell>{children}</AppShell>
    </Suspense>
  );
}
