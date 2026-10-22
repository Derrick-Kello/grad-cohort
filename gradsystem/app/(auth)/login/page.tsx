"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState, type FormEvent } from "react";
import { Button, ErrorBox, TextField } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { homeFor, useAuth } from "@/lib/auth";
import { viewForPath } from "@/lib/nav";

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const { login, status, view, views } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get("next");
  const [loginName, setLoginName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<ApiError | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    if (status === "signed-in") router.replace(target(next, views) ?? homeFor(view));
  }, [status, view, views, next, router]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setPending(true);
    setError(null);
    try {
      await login(loginName.trim(), password);
    } catch (err) {
      setError(err instanceof ApiError ? err : null);
    } finally {
      setPending(false);
    }
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
      <div>
        <h1 className="text-xl font-semibold">Sign in</h1>
        <p className="mt-1 text-sm text-muted">Use your university email or staff / student number.</p>
      </div>
      <TextField label="Email or number" autoComplete="username" value={loginName} onChange={(e) => setLoginName(e.target.value)} required />
      <TextField label="Password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      <ErrorBox error={error} />
      <Button type="submit" variant="primary" loading={pending} disabled={!loginName || !password}>
        Sign in
      </Button>
      <Link href="/forgot-password" className="text-center text-sm text-primary hover:underline">
        Forgot your password?
      </Link>
    </form>
  );
}

function target(next: string | null, views: string[]): string | null {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return null;
  const v = viewForPath(next);
  return v === null || views.includes(v) ? next : null;
}
