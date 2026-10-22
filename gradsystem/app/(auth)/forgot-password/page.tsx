"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { Banner, Button, ErrorBox, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { useAction } from "@/lib/hooks";

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(false);
  const { run, pending, error } = useAction(async (address: string) => {
    await api.post("/auth/password/forgot", { email: address });
    return true;
  });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(email.trim())) setSent(true);
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold">Reset your password</h1>
        <p className="mt-1 text-sm text-muted">We will email a link that works once and expires after 30 minutes.</p>
      </div>
      {sent ? (
        <Banner tone="ok" title="Check your email">
          If an account uses {email}, a reset link is on its way.
        </Banner>
      ) : (
        <>
          <TextField label="Email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <ErrorBox error={error} />
          <Button type="submit" variant="primary" loading={pending} disabled={!email}>
            Send reset link
          </Button>
        </>
      )}
      <Link href="/login" className="text-center text-sm text-primary hover:underline">
        Back to sign in
      </Link>
    </form>
  );
}
