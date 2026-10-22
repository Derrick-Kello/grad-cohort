// Dates shown as "19 Oct 2026", times in 24-hour Africa/Accra time (PRD section 18, NFR-14).
const ZONE = "Africa/Accra";

const dateFmt = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: ZONE });
const timeFmt = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false, timeZone: ZONE });
const weekdayFmt = new Intl.DateTimeFormat("en-GB", { weekday: "short", timeZone: ZONE });

function toDate(value: string | Date): Date {
  if (value instanceof Date) return value;
  // Plain dates (2026-10-19) are calendar dates; read them at noon UTC so the day never shifts.
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T12:00:00Z`) : new Date(value);
}

export function formatDate(value?: string | Date | null): string {
  if (!value) return "";
  return dateFmt.format(toDate(value));
}

export function formatTime(value?: string | Date | null): string {
  if (!value) return "";
  return timeFmt.format(toDate(value));
}

export function formatDateTime(value?: string | Date | null): string {
  if (!value) return "";
  return `${formatDate(value)}, ${formatTime(value)}`;
}

export function weekday(value: string | Date): string {
  return weekdayFmt.format(toDate(value));
}

/** "yyyy-mm-dd" for a date in Accra. */
export function isoDay(value: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", { timeZone: ZONE, year: "numeric", month: "2-digit", day: "2-digit" }).format(value);
  return parts;
}

/** Converts a local Accra date and time ("2026-10-20", "09:00") to an ISO UTC instant. Accra is UTC+0 all year. */
export function accraToIso(day: string, time: string): string {
  return new Date(`${day}T${time}:00Z`).toISOString();
}

export function relativeDays(days: number): string {
  if (days === 0) return "today";
  if (days === 1) return "tomorrow";
  if (days === -1) return "yesterday";
  if (days > 0) return days > 60 ? `in ${Math.round(days / 30.4)} months` : `in ${days} days`;
  const ago = -days;
  return ago > 60 ? `${Math.round(ago / 30.4)} months ago` : `${ago} days ago`;
}

export function pretty(code?: string | null): string {
  if (!code) return "";
  const s = code.replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function initials(name?: string | null): string {
  if (!name) return "?";
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join("");
}
