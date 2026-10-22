"use client";

import { useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { Banner, Button, ErrorBox, KeyValue, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { useAction } from "@/lib/hooks";

type Verification = { valid: boolean; studentName: string; studentNumber: string; programme: string; generatedAt: string; approvedItems: number };

export default function VerifyPage() {
  return (
    <Suspense>
      <VerifyForm />
    </Suspense>
  );
}

/** Public check of a progress statement's verification code (FR-ACH-03). */
function VerifyForm() {
  const [code, setCode] = useState(useSearchParams().get("code") ?? "");
  const [result, setResult] = useState<Verification | null>(null);
  const { run, pending, error } = useAction((c: string) => api.get<Verification>(`/public/statements/verify?code=${encodeURIComponent(c)}`));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setResult((await run(code.trim())) ?? null);
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold">Verify a progress statement</h1>
        <p className="mt-1 text-sm text-muted">Enter the code printed at the bottom of the statement.</p>
      </div>
      <TextField label="Verification code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="ABCDE-FGHJK" required />
      <ErrorBox error={error} />
      <Button type="submit" variant="primary" loading={pending} disabled={!code}>
        Verify
      </Button>
      {result && (
        <div className="flex flex-col gap-3">
          <Banner tone="ok" title="This statement is genuine" />
          <KeyValue
            items={[
              ["Student", `${result.studentName} (${result.studentNumber})`],
              ["Programme", result.programme],
              ["Generated", formatDateTime(result.generatedAt)],
              ["Approved items", result.approvedItems],
            ]}
          />
        </div>
      )}
    </form>
  );
}
