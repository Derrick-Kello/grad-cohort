"use client";

import { FileText } from "lucide-react";
import { AchievementTimeline } from "@/components/achievements";
import { useToast } from "@/components/toast";
import { Button, Card, ErrorBox, Loading, PageHeader } from "@/components/ui";
import { download } from "@/lib/api";
import { useAction, useApi } from "@/lib/hooks";
import type { Achievement } from "@/lib/types";

export default function AchievementsPage() {
  const { data, error, mutate } = useApi<Achievement[]>("/students/me/achievements");
  const toast = useToast();
  const statement = useAction(async () => {
    await download("/students/me/statement.pdf", "progress-statement.pdf");
    return true;
  });
  return (
    <>
      <PageHeader
        title="Achievements"
        description="Approved presentations, library-verified publications and thesis acceptance, in date order."
        actions={
          <Button
            variant="primary"
            icon={<FileText size={16} />}
            loading={statement.pending}
            onClick={async () => (await statement.run()) && toast("Progress statement downloaded")}
          >
            Download progress statement
          </Button>
        }
      />
      <ErrorBox error={error ?? statement.error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <Card>
          <AchievementTimeline items={data} />
        </Card>
      )}
    </>
  );
}
