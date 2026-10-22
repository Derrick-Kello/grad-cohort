"use client";

import { useState } from "react";
import { useToast } from "@/components/toast";
import { Button, Card, Checkbox, ErrorBox, KeyValue, PageHeader, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { pretty } from "@/lib/format";
import { useAction } from "@/lib/hooks";
import type { Me } from "@/lib/types";

export default function ProfilePage() {
  const { me, reload } = useAuth();
  const toast = useToast();
  const [digest, setDigest] = useState(me?.emailDigest ?? false);
  const [phone, setPhone] = useState(me?.phone ?? "");
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const prefs = useAction(async () => api.put<Me>("/me/preferences", { emailDigest: digest, phone }));
  const password = useAction(async () => {
    await api.put("/me/password", { currentPassword: current, newPassword: next });
    return true;
  });
  if (!me) return null;
  const weak = next.length > 0 && (next.length < 10 || !/[A-Za-z]/.test(next) || !/\d/.test(next));

  return (
    <>
      <PageHeader title="Profile" />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card title="Your account">
          <KeyValue
            items={[
              ["Name", me.name],
              ["Email", me.email],
              ["Staff or student number", me.number],
              ["Roles", me.roles.map(pretty).join(", ")],
              ...(me.programmeCode ? ([["Programme", me.programmeCode]] as [string, string][]) : []),
            ]}
          />
          <p className="mt-4 text-xs text-muted">Ask the graduate school to correct your name, email or number.</p>
        </Card>
        <Card title="Contact and email">
          <form
            className="flex flex-col gap-4"
            onSubmit={async (e) => {
              e.preventDefault();
              if (await prefs.run()) {
                toast("Preferences saved");
                void reload();
              }
            }}
          >
            <TextField label="Phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
            <Checkbox label="Send non-urgent emails as one daily summary" checked={digest} onChange={setDigest} />
            <p className="-mt-2 text-xs text-muted">Deadline, rejection and expulsion notices are always sent immediately.</p>
            <ErrorBox error={prefs.error} />
            <div>
              <Button type="submit" variant="primary" loading={prefs.pending}>
                Save preferences
              </Button>
            </div>
          </form>
        </Card>
        <Card title="Change password">
          <form
            className="flex flex-col gap-4"
            onSubmit={async (e) => {
              e.preventDefault();
              if (await password.run()) {
                toast("Password changed. Other sessions were signed out.");
                setCurrent("");
                setNext("");
              }
            }}
          >
            <TextField label="Current password" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required />
            <TextField
              label="New password"
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              hint="At least 10 characters, with letters and numbers."
              error={weak ? "Use at least 10 characters with letters and numbers." : null}
              required
            />
            <ErrorBox error={password.error} />
            <div>
              <Button type="submit" variant="primary" loading={password.pending} disabled={!current || !next || weak}>
                Change password
              </Button>
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
