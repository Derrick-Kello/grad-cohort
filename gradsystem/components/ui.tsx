"use client";

import {
  forwardRef,
  useId,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";
import { Lock, TriangleAlert } from "lucide-react";
import type { ApiError } from "@/lib/api";
import { pretty } from "@/lib/format";

export function cx(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(" ");
}

// Buttons --------------------------------------------------------------------------------

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
  size?: "sm" | "md";
  loading?: boolean;
  icon?: ReactNode;
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "secondary", size = "md", loading, icon, className, children, disabled, type = "button", ...rest },
  ref,
) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-lg font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-55";
  const sizes = size === "sm" ? "h-8 px-3 text-sm" : "h-10 px-4 text-sm";
  const variants = {
    primary: "bg-primary text-on-primary hover:bg-primary-hover",
    secondary: "border border-border-strong bg-surface text-text hover:bg-surface-2",
    ghost: "text-text hover:bg-surface-2",
    danger: "bg-bad text-white hover:opacity-90 dark:text-[#1a0806]",
  }[variant];
  return (
    <button ref={ref} type={type} disabled={disabled || loading} className={cx(base, sizes, variants, className)} {...rest}>
      {loading ? <Spinner small /> : icon}
      {children}
    </button>
  );
});

// Form fields -------------------------------------------------------------------------------

export function Field({
  label,
  hint,
  error,
  children,
  required,
  htmlFor,
}: {
  label: string;
  hint?: ReactNode;
  error?: string | null;
  children: ReactNode;
  required?: boolean;
  htmlFor?: string;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-text">
        {label}
        {required && <span className="text-bad"> *</span>}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-muted">{hint}</p>}
      {error && <p className="text-xs text-bad">{error}</p>}
    </div>
  );
}

const control =
  "w-full rounded-lg border border-border-strong bg-surface px-3 text-sm text-text placeholder:text-faint disabled:bg-surface-2 disabled:text-muted";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input({ className, ...rest }, ref) {
  return <input ref={ref} className={cx(control, "h-10", className)} {...rest} />;
});

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, rows = 4, ...rest },
  ref,
) {
  return <textarea ref={ref} rows={rows} className={cx(control, "py-2", className)} {...rest} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...rest },
  ref,
) {
  return (
    <select ref={ref} className={cx(control, "h-10 pr-8", className)} {...rest}>
      {children}
    </select>
  );
});

/** Labelled input with generated id. */
export function TextField({
  label,
  hint,
  error,
  required,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: ReactNode; error?: string | null }) {
  const id = useId();
  return (
    <Field label={label} hint={hint} error={error} required={required} htmlFor={id}>
      <Input id={id} required={required} aria-invalid={!!error} {...rest} />
    </Field>
  );
}

export function SelectField({
  label,
  hint,
  error,
  required,
  children,
  ...rest
}: SelectHTMLAttributes<HTMLSelectElement> & { label: string; hint?: ReactNode; error?: string | null }) {
  const id = useId();
  return (
    <Field label={label} hint={hint} error={error} required={required} htmlFor={id}>
      <Select id={id} required={required} {...rest}>
        {children}
      </Select>
    </Field>
  );
}

export function TextareaField({
  label,
  hint,
  error,
  required,
  ...rest
}: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; hint?: ReactNode; error?: string | null }) {
  const id = useId();
  return (
    <Field label={label} hint={hint} error={error} required={required} htmlFor={id}>
      <Textarea id={id} required={required} {...rest} />
    </Field>
  );
}

export function Checkbox({ label, checked, onChange, disabled }: { label: ReactNode; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <label className="inline-flex cursor-pointer items-center gap-2 text-sm">
      <input
        type="checkbox"
        className="h-4 w-4 accent-[var(--primary)]"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      {label}
    </label>
  );
}

// Layout ------------------------------------------------------------------------------------

export function PageHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <h1 className="text-2xl font-semibold tracking-tight text-text">{title}</h1>
        {description && <p className="mt-1 max-w-3xl text-sm text-muted">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function Card({
  title,
  actions,
  children,
  className,
  padded = true,
  description,
}: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  padded?: boolean;
  description?: ReactNode;
}) {
  return (
    <section className={cx("rounded-[var(--radius)] border border-border bg-surface shadow-[var(--shadow)]", className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-5 py-3.5">
          <div>
            {title && <h2 className="text-[15px] font-semibold text-text">{title}</h2>}
            {description && <p className="text-xs text-muted">{description}</p>}
          </div>
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={padded ? "p-5" : ""}>{children}</div>
    </section>
  );
}

export function Stat({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: Tone }) {
  return (
    <div className="rounded-[var(--radius)] border border-border bg-surface p-4 shadow-[var(--shadow)]">
      <p className="text-xs font-medium uppercase tracking-wide text-muted">{label}</p>
      <p className={cx("tabular mt-1.5 text-2xl font-semibold", tone ? TONE_TEXT[tone] : "text-text")}>{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}

export function KeyValue({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
      {items.map(([k, v]) => (
        <div key={k} className="min-w-0">
          <dt className="text-xs font-medium text-muted">{k}</dt>
          <dd className="mt-0.5 break-words text-sm text-text">{v ?? "Not set"}</dd>
        </div>
      ))}
    </dl>
  );
}

// Status -----------------------------------------------------------------------------------

export type Tone = "ok" | "warn" | "bad" | "grey" | "info";

const TONE_CHIP: Record<Tone, string> = {
  ok: "bg-ok-bg text-ok",
  warn: "bg-warn-bg text-warn",
  bad: "bg-bad-bg text-bad",
  grey: "bg-grey-bg text-grey",
  info: "bg-info-bg text-info",
};

const TONE_TEXT: Record<Tone, string> = { ok: "text-ok", warn: "text-warn", bad: "text-bad", grey: "text-grey", info: "text-info" };

const STATUS_TONE: Record<string, Tone> = {
  ACTIVE: "ok", APPROVED: "ok", ELIGIBLE: "ok", PASSED: "ok", SCREENING_PASSED: "ok", ACCEPTED_FOR_EXAMINATION: "ok",
  FORMAT_PASSED: "ok", COMPLETED: "ok", CLEAN: "ok", PRESENT: "ok", PUBLISHED: "ok",
  AT_RISK: "warn", AWAITING_VERIFICATION: "warn", SUBMITTED: "warn", CHANGES_REQUESTED: "warn", RETURNED: "warn",
  FORMAT_CHECKING: "warn", SCREENING: "warn", UPLOADED: "warn", LATE: "warn", PENDING_SCAN: "warn",
  "ELIGIBLE (EXCEPTION)": "warn", EXCEPTION: "warn",
  FLAGGED_FOR_EXPULSION: "bad", EXPELLED: "bad", REJECTED: "bad", FAILED: "bad", FORMAT_FAILED: "bad",
  SCREENING_REJECTED: "bad", NOT_ELIGIBLE: "bad", ABSENT: "bad", INFECTED: "bad",
  LOCKED: "grey", DRAFT: "grey", WITHDRAWN: "grey", NOT_HELD: "grey", CANCELLED: "grey", INACTIVE: "grey",
  AVAILABLE: "info", SCHEDULED: "info", EXCUSED: "info", ACCEPTED: "info",
};

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "On track",
  AT_RISK: "At risk",
  FLAGGED_FOR_EXPULSION: "Flagged",
  NOT_ELIGIBLE: "Not eligible",
  "ELIGIBLE (EXCEPTION)": "Eligible (exception)",
  NOT_HELD: "Not held",
  PENDING_SCAN: "Waiting for virus scan",
};

export function statusTone(status: string): Tone {
  return STATUS_TONE[status] ?? "grey";
}

export function StatusChip({ status, label, tone }: { status: string; label?: string; tone?: Tone }) {
  const t = tone ?? statusTone(status);
  return (
    <span className={cx("inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap", TONE_CHIP[t])}>
      {status === "LOCKED" && <Lock size={11} aria-hidden />}
      {label ?? STATUS_LABEL[status] ?? pretty(status)}
    </span>
  );
}

export function Banner({ tone = "info", title, children, action }: { tone?: Tone; title?: ReactNode; children?: ReactNode; action?: ReactNode }) {
  return (
    <div role={tone === "bad" ? "alert" : "status"} className={cx("flex flex-col gap-2 rounded-[var(--radius)] px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between", TONE_CHIP[tone])}>
      <div className="min-w-0">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className="text-[13px] opacity-95">{children}</div>}
      </div>
      {action}
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error: ApiError | Error | null | undefined; onRetry?: () => void }) {
  if (!error) return null;
  const problem = "problem" in error ? error.problem : null;
  return (
    <div role="alert" className="flex items-start gap-3 rounded-[var(--radius)] bg-bad-bg px-4 py-3 text-sm text-bad">
      <TriangleAlert size={18} className="mt-0.5 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-medium">{error.message}</p>
        {problem?.errors && problem.errors.length > 0 && (
          <ul className="mt-1 list-disc pl-5 text-[13px]">
            {problem.errors.map((e: { field?: string; row?: number; message: string }, i: number) => (
              <li key={i}>
                {e.field ? `${e.field}: ` : e.row ? `Row ${e.row}: ` : ""}
                {e.message}
              </li>
            ))}
          </ul>
        )}
        {problem?.traceId && <p className="mt-1 text-xs opacity-80">Trace id {problem.traceId}</p>}
      </div>
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function Spinner({ small }: { small?: boolean }) {
  return (
    <span
      aria-hidden
      className={cx("inline-block animate-spin rounded-full border-2 border-current border-t-transparent opacity-70", small ? "h-3.5 w-3.5" : "h-5 w-5")}
    />
  );
}

export function Loading({ label = "Loading" }: { label?: string }) {
  return (
    <div className="flex items-center gap-3 py-10 text-sm text-muted" role="status">
      <Spinner /> {label}…
    </div>
  );
}

export function Empty({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <p className="font-medium text-text">{title}</p>
      {children && <p className="max-w-md text-sm text-muted">{children}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

export function ProgressBar({ value, max, tone = "info", label }: { value: number; max: number; tone?: Tone; label: string }) {
  const pct = max === 0 ? 0 : Math.min(100, Math.round((value / max) * 100));
  const fill = { ok: "bg-ok", warn: "bg-warn", bad: "bg-bad", grey: "bg-grey", info: "bg-primary" }[tone];
  return (
    <div role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={max} aria-valuenow={value} className="h-2 w-full overflow-hidden rounded-full bg-surface-2">
      <div className={cx("h-full rounded-full", fill)} style={{ width: `${pct}%` }} />
    </div>
  );
}

export function Tabs<T extends string>({ value, onChange, items }: { value: T; onChange: (v: T) => void; items: { value: T; label: ReactNode }[] }) {
  return (
    <div role="tablist" className="mb-4 flex gap-1 overflow-x-auto border-b border-border">
      {items.map((item) => (
        <button
          key={item.value}
          role="tab"
          type="button"
          aria-selected={value === item.value}
          onClick={() => onChange(item.value)}
          className={cx(
            "-mb-px border-b-2 px-3 py-2 text-sm font-medium whitespace-nowrap",
            value === item.value ? "border-primary text-primary" : "border-transparent text-muted hover:text-text",
          )}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}
