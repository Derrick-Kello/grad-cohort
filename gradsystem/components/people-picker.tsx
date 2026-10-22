"use client";

import { useApi } from "@/lib/hooks";
import type { Page, User } from "@/lib/types";
import { Select } from "./ui";

/** Dropdown of active staff holding a role, e.g. supervisors or lecturers. */
export function StaffSelect({
  role,
  value,
  onChange,
  label,
  allowEmpty = true,
  exclude = [],
}: {
  role: string;
  value: string;
  onChange: (id: string) => void;
  label: string;
  allowEmpty?: boolean;
  exclude?: string[];
}) {
  const { data } = useApi<Page<User>>(`/admin/users?role=${role}&staffOnly=true&size=200`);
  const people = data?.content.filter((u) => u.active && !exclude.includes(u.id)) ?? [];
  return (
    <Select aria-label={label} value={value} onChange={(e) => onChange(e.target.value)}>
      {allowEmpty && <option value="">Choose…</option>}
      {people.map((u) => (
        <option key={u.id} value={u.id}>
          {u.name} ({u.number})
        </option>
      ))}
    </Select>
  );
}
