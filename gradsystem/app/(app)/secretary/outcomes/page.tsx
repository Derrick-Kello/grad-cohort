"use client";

import { useState } from "react";
import { OutcomeDialog } from "@/components/outcome-dialog";
import { PresentationAgenda } from "@/components/presentation-list";
import { Button, Card, ErrorBox, Loading, PageHeader } from "@/components/ui";
import { isoDay } from "@/lib/format";
import { useApi } from "@/lib/hooks";
import type { PresentationRow } from "@/lib/types";

function daysAgo(n: number) {
  const d = new Date();
  d.setUTCDate(d.getUTCDate() - n);
  return isoDay(d);
}

export default function RecordOutcomesPage() {
  const awaiting = useApi<PresentationRow[]>("/secretary/awaiting-outcome");
  const recent = useApi<PresentationRow[]>(`/secretary/presentations?from=${daysAgo(3)}&to=${isoDay()}`);
  const [target, setTarget] = useState<{ row: PresentationRow; correction: boolean } | null>(null);
  const recorded = recent.data?.filter((r) => r.outcome) ?? [];

  return (
    <>
      <PageHeader
        title="Record outcomes"
        description="Record what the panel decided, on or after the presentation date. Remarks are required when a presentation is not passed. You can correct an outcome for 48 hours; after that, ask an admin."
      />
      <ErrorBox error={awaiting.error} onRetry={() => awaiting.mutate()} />
      <div className="flex flex-col gap-6">
        <Card title="Waiting for an outcome">
          {!awaiting.data ? (
            <Loading />
          ) : (
            <PresentationAgenda
              rows={awaiting.data}
              empty="All outcomes are recorded."
              action={(r) => (
                <Button size="sm" variant="primary" onClick={() => setTarget({ row: r, correction: false })}>
                  Record
                </Button>
              )}
            />
          )}
        </Card>
        <Card title="Recently recorded" description="Last three days">
          {!recent.data ? (
            <Loading />
          ) : (
            <PresentationAgenda
              rows={recorded}
              empty="Nothing recorded in the last three days."
              action={(r) => (
                <Button size="sm" onClick={() => setTarget({ row: r, correction: true })}>
                  Correct
                </Button>
              )}
            />
          )}
        </Card>
      </div>
      {target && (
        <OutcomeDialog
          row={target.row}
          correction={target.correction}
          onClose={() => setTarget(null)}
          onDone={() => {
            setTarget(null);
            void awaiting.mutate();
            void recent.mutate();
          }}
        />
      )}
    </>
  );
}
