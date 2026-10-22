"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { X } from "lucide-react";
import { Button, ErrorBox, Field, Textarea, cx } from "./ui";
import type { ApiError } from "@/lib/api";

/** Accessible modal: focus moves in on open, Escape closes, focus returns to the opener. */
export function Dialog({
  open,
  onClose,
  title,
  children,
  footer,
  wide,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const titleId = useId();
  useEffect(() => {
    if (!open) return;
    const opener = document.activeElement as HTMLElement | null;
    const panel = ref.current;
    const focusable = panel?.querySelector<HTMLElement>("input, textarea, select, button:not([data-close])");
    (focusable ?? panel)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      if (e.key === "Tab" && panel) {
        const items = Array.from(panel.querySelectorAll<HTMLElement>("a, button, input, textarea, select, [tabindex]:not([tabindex='-1'])")).filter(
          (el) => !el.hasAttribute("disabled"),
        );
        if (items.length === 0) return;
        const first = items[0];
        const last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
      opener?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/45 p-0 sm:items-center sm:p-4" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={cx(
          "flex max-h-[92vh] w-full flex-col rounded-t-2xl bg-surface shadow-[var(--shadow-lg)] sm:rounded-2xl",
          wide ? "sm:max-w-3xl" : "sm:max-w-lg",
        )}
      >
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <h2 id={titleId} className="text-base font-semibold">
            {title}
          </h2>
          <button data-close type="button" onClick={onClose} className="rounded-md p-1 text-muted hover:bg-surface-2" aria-label="Close">
            <X size={18} />
          </button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2 border-t border-border px-5 py-3">{footer}</div>}
      </div>
    </div>
  );
}

/**
 * Destructive or final actions need a confirmation that states the consequence (PRD section 18).
 * With {@code reasonLabel} the dialog collects a required written reason.
 */
export function ConfirmDialog({
  open,
  onClose,
  title,
  consequence,
  confirmLabel,
  tone = "primary",
  reasonLabel,
  reasonOptional,
  onConfirm,
  pending,
  error,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  consequence: ReactNode;
  confirmLabel: string;
  tone?: "primary" | "danger";
  reasonLabel?: string;
  reasonOptional?: boolean;
  onConfirm: (reason: string) => void | Promise<void>;
  pending?: boolean;
  error?: ApiError | null;
}) {
  const [reason, setReason] = useState("");
  const id = useId();
  const needsReason = !!reasonLabel && !reasonOptional;
  return (
    <Dialog
      open={open}
      onClose={() => {
        setReason("");
        onClose();
      }}
      title={title}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant={tone === "danger" ? "danger" : "primary"}
            loading={pending}
            disabled={needsReason && reason.trim().length === 0}
            onClick={() => onConfirm(reason.trim())}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="text-sm text-muted">{consequence}</div>
        {reasonLabel && (
          <Field label={reasonLabel} required={!reasonOptional} htmlFor={id}>
            <Textarea id={id} value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
          </Field>
        )}
        <ErrorBox error={error} />
      </div>
    </Dialog>
  );
}
