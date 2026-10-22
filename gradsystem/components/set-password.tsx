"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Banner, Button, ErrorBox, TextField } from "./ui";
import { api } from "@/lib/api";
import { useAction } from "@/lib/hooks";

/** Shared form for account activation (FR-AUTH-04) and password reset (FR-AUTH-03). */
export function SetPasswordForm({ endpoint, title, intro }: { endpoint: string; title: string; intro: string }) {
  const token = useSearchParams().get("token") ?? "";
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [done, setDone] = useState(false);
  const { run, pending, error } = useAction(async (pw: string) => {
    await api.post(endpoint, { token, password: pw });
    return true;
  });
  const weak = password.length > 0 && (password.length < 10 || !/[A-Za-z]/.test(password) || !/\d/.test(password));
  const mismatch = confirm.length > 0 && confirm !== password;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(password)) setDone(true);
  };

  if (!token) return <Banner tone="bad" title="This link is incomplete">Open the link from your email again.</Banner>;
  if (done) {
    return (
      <div className="flex flex-col gap-4">
        <Banner tone="ok" title="Password saved">You can now sign in with your new password.</Banner>
        <Link href="/login" className="text-center text-sm font-medium text-primary hover:underline">
          Go to sign in
        </Link>
      </div>
    );
  }
  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold">{title}</h1>
        <p className="mt-1 text-sm text-muted">{intro}</p>
      </div>
      <TextField
        label="New password"
        type="password"
        autoComplete="new-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        hint="At least 10 characters, with letters and numbers."
        error={weak ? "Use at least 10 characters with letters and numbers." : null}
        required
      />
      <TextField
        label="Repeat password"
        type="password"
        autoComplete="new-password"
        value={confirm}
        onChange={(e) => setConfirm(e.target.value)}
        error={mismatch ? "The passwords do not match." : null}
        required
      />
      <ErrorBox error={error} />
      <Button type="submit" variant="primary" loading={pending} disabled={!password || weak || mismatch || confirm !== password}>
        Save password
      </Button>
    </form>
  );
}
