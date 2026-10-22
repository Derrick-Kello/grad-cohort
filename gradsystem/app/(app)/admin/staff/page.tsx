"use client";

import { useState } from "react";
import { Plus, Upload } from "lucide-react";
import { CsvImportDialog } from "@/components/csv-import";
import { DataTable, Pager } from "@/components/data-table";
import { ConfirmDialog, Dialog } from "@/components/dialog";
import { useToast } from "@/components/toast";
import { Button, Checkbox, ErrorBox, Field, Input, Loading, PageHeader, Select, StatusChip, TextField } from "@/components/ui";
import { api } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { formatDateTime, pretty } from "@/lib/format";
import { useAction, useApi } from "@/lib/hooks";
import type { Page, User } from "@/lib/types";

const STAFF_ROLES = ["LECTURER", "SUPERVISOR", "SECRETARY", "LIBRARIAN", "ADMIN", "SUPER_ADMIN"];

export default function StaffPage() {
  const [q, setQ] = useState("");
  const [role, setRole] = useState("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<User | "new" | null>(null);
  const [importing, setImporting] = useState(false);
  const [toggling, setToggling] = useState<User | null>(null);
  const toast = useToast();
  const params = new URLSearchParams({ q, page: String(page), size: "25", staffOnly: "true" });
  if (role) params.set("role", role);
  const { data, error, mutate } = useApi<Page<User>>(`/admin/users?${params}`);
  const toggle = useAction(async (u: User) => api.put<User>(`/admin/users/${u.id}/active`, { active: !u.active }));
  const act = async (fn: () => Promise<unknown>, message: string) => {
    try {
      await fn();
      toast(message);
      void mutate();
    } catch (e) {
      toast((e as Error).message, "bad");
    }
  };

  return (
    <>
      <PageHeader
        title="Staff"
        description="Accounts for admins, secretaries, librarians, lecturers and supervisors. A supervisor must also be a lecturer."
        actions={
          <>
            <Button icon={<Upload size={16} />} onClick={() => setImporting(true)}>
              Import CSV
            </Button>
            <Button variant="primary" icon={<Plus size={16} />} onClick={() => setEditing("new")}>
              Add staff member
            </Button>
          </>
        }
      />
      <div className="mb-4 grid grid-cols-1 gap-2 sm:grid-cols-3">
        <Input className="sm:col-span-2" aria-label="Search" placeholder="Search name, email or number" value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        <Select aria-label="Role" value={role} onChange={(e) => { setRole(e.target.value); setPage(0); }}>
          <option value="">All roles</option>
          {STAFF_ROLES.map((r) => <option key={r} value={r}>{pretty(r)}</option>)}
        </Select>
      </div>
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data && (
        <div className="flex flex-col gap-3">
          <DataTable
            rows={data.content}
            rowKey={(u) => u.id}
            pageSize={200}
            columns={[
              { key: "name", header: "Name", value: (u) => u.name, render: (u) => <div><p className="font-medium">{u.name}</p><p className="text-xs text-muted">{u.email} · {u.number}</p></div> },
              { key: "roles", header: "Roles", value: (u) => u.roles.join(", "), render: (u) => <div className="flex flex-wrap gap-1">{u.roles.map((r) => <span key={r} className="rounded bg-surface-2 px-1.5 py-0.5 text-xs">{pretty(r)}</span>)}</div> },
              { key: "status", header: "Account", value: (u) => (u.active ? "Active" : "Deactivated"), render: (u) => (
                <div className="flex flex-col items-start gap-1">
                  <StatusChip status={u.active ? "ACTIVE" : "INACTIVE"} label={u.active ? "Active" : "Deactivated"} tone={u.active ? "ok" : "grey"} />
                  {!u.activated && <StatusChip status="PENDING" label="Not activated" tone="warn" />}
                  {u.lockedUntil && new Date(u.lockedUntil) > new Date() && <StatusChip status="LOCKED" label="Locked" tone="bad" />}
                </div>
              ) },
              { key: "last", header: "Last sign-in", value: (u) => u.lastLoginAt ?? "", render: (u) => (u.lastLoginAt ? formatDateTime(u.lastLoginAt) : <span className="text-muted">Never</span>), hideOnMobile: true },
              {
                key: "actions",
                header: "",
                render: (u) => (
                  <div className="flex flex-wrap justify-end gap-1">
                    <Button size="sm" onClick={() => setEditing(u)}>Edit</Button>
                    {u.lockedUntil && <Button size="sm" variant="ghost" onClick={() => act(() => api.post(`/admin/users/${u.id}/unlock`), "Account unlocked")}>Unlock</Button>}
                    {!u.activated && <Button size="sm" variant="ghost" onClick={() => act(() => api.post(`/admin/users/${u.id}/resend-activation`), "Activation email sent")}>Resend activation</Button>}
                    <Button size="sm" variant="ghost" onClick={() => setToggling(u)}>{u.active ? "Deactivate" : "Reactivate"}</Button>
                  </div>
                ),
              },
            ]}
          />
          <Pager page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} onPage={setPage} />
        </div>
      )}
      {editing && <UserDialog user={editing === "new" ? undefined : editing} onClose={() => setEditing(null)} onDone={() => { setEditing(null); void mutate(); }} />}
      {importing && <CsvImportDialog onClose={() => setImporting(false)} onDone={() => mutate()} />}
      <ConfirmDialog
        open={!!toggling}
        onClose={() => setToggling(null)}
        title={toggling?.active ? `Deactivate ${toggling?.name}?` : `Reactivate ${toggling?.name}?`}
        consequence={toggling?.active ? "They can no longer sign in and their sessions end. Their history is kept." : "They can sign in again."}
        confirmLabel={toggling?.active ? "Deactivate" : "Reactivate"}
        tone={toggling?.active ? "danger" : "primary"}
        pending={toggle.pending}
        error={toggle.error}
        onConfirm={async () => {
          if (toggling && (await toggle.run(toggling))) {
            setToggling(null);
            void mutate();
          }
        }}
      />
    </>
  );
}

function UserDialog({ user, onClose, onDone }: { user?: User; onClose: () => void; onDone: () => void }) {
  const { me } = useAuth();
  const superAdmin = me?.roles.includes("SUPER_ADMIN");
  const [f, setF] = useState({ firstName: user?.firstName ?? "", lastName: user?.lastName ?? "", email: user?.email ?? "", number: user?.number ?? "", phone: user?.phone ?? "" });
  const [roles, setRoles] = useState<string[]>(user?.roles.filter((r) => r !== "STUDENT") ?? ["LECTURER"]);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF((x) => ({ ...x, [k]: e.target.value }));
  const toast = useToast();
  const { run, pending, error } = useAction(async () => {
    const body = { ...f, roles, version: user?.version };
    if (!user) return api.post<User>("/admin/users", body);
    await api.put<User>(`/admin/users/${user.id}`, body);
    const sameRoles = [...roles].sort().join() === user.roles.filter((r) => r !== "STUDENT").sort().join();
    if (!sameRoles) await api.put(`/admin/users/${user.id}/roles`, { roles: user.roles.includes("STUDENT") ? [...roles, "STUDENT"] : roles });
    return true;
  });
  const flip = (r: string, on: boolean) => {
    setRoles((current) => {
      let next = on ? [...current, r] : current.filter((x) => x !== r);
      if (r === "SUPERVISOR" && on && !next.includes("LECTURER")) next = [...next, "LECTURER"];
      if (r === "LECTURER" && !on) next = next.filter((x) => x !== "SUPERVISOR");
      return next;
    });
  };
  return (
    <Dialog
      open
      onClose={onClose}
      title={user ? `Edit ${user.name}` : "Add a staff member"}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={pending}
            disabled={!f.firstName || !f.lastName || !f.email || !f.number || roles.length === 0}
            onClick={async () => {
              if (await run()) {
                toast(user ? "Saved" : "Account created. An activation email was sent.");
                onDone();
              }
            }}
          >
            {user ? "Save" : "Create account"}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField label="First name" value={f.firstName} onChange={set("firstName")} required />
        <TextField label="Last name" value={f.lastName} onChange={set("lastName")} required />
        <TextField label="Email" type="email" value={f.email} onChange={set("email")} required />
        <TextField label="Staff number" value={f.number} onChange={set("number")} required />
        <TextField label="Phone" type="tel" value={f.phone} onChange={set("phone")} />
        <div className="sm:col-span-2">
          <Field label="Roles" hint="Supervisor needs Lecturer. Only a Super Admin can grant admin roles.">
            <div className="grid grid-cols-2 gap-2">
              {STAFF_ROLES.map((r) => (
                <Checkbox key={r} label={pretty(r)} checked={roles.includes(r)} disabled={(r === "ADMIN" || r === "SUPER_ADMIN") && !superAdmin} onChange={(on) => flip(r, on)} />
              ))}
            </div>
          </Field>
        </div>
        <div className="sm:col-span-2">
          <ErrorBox error={error} />
        </div>
      </div>
    </Dialog>
  );
}
