"use client";

import { useId, useRef, useState } from "react";
import { Upload } from "lucide-react";
import { fileSize } from "@/lib/format";
import { cx } from "./ui";

/** File picker with drag and drop. Checks size and extension in the browser; the server re-checks content. */
export function FileInput({
  label,
  accept,
  maxMb,
  file,
  onChange,
  hint,
}: {
  label: string;
  accept: string;
  maxMb: number;
  file: File | null;
  onChange: (file: File | null) => void;
  hint?: string;
}) {
  const id = useId();
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);
  const [over, setOver] = useState(false);

  const pick = (f: File | undefined | null) => {
    if (!f) return;
    const ext = "." + (f.name.split(".").pop() ?? "").toLowerCase();
    const allowed = accept.split(",").map((s) => s.trim().toLowerCase());
    if (!allowed.includes(ext) && !(ext === ".jpeg" && allowed.includes(".jpg"))) {
      setError(`Choose a ${allowed.map((a) => a.slice(1).toUpperCase()).join(", ")} file.`);
      onChange(null);
      return;
    }
    if (f.size > maxMb * 1024 * 1024) {
      setError(`The file is ${fileSize(f.size)}; the limit is ${maxMb} MB.`);
      onChange(null);
      return;
    }
    setError(null);
    onChange(f);
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setOver(true);
        }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => {
          e.preventDefault();
          setOver(false);
          pick(e.dataTransfer.files?.[0]);
        }}
        className={cx(
          "flex flex-col items-center gap-2 rounded-lg border border-dashed px-4 py-5 text-center text-sm",
          over ? "border-primary bg-primary-soft" : "border-border-strong bg-surface-2",
        )}
      >
        <Upload size={20} className="text-muted" aria-hidden />
        {file ? (
          <p className="font-medium break-all">
            {file.name} <span className="font-normal text-muted">({fileSize(file.size)})</span>
          </p>
        ) : (
          <p className="text-muted">Drag a file here or</p>
        )}
        <button type="button" onClick={() => input.current?.click()} className="font-medium text-primary hover:underline">
          {file ? "Choose a different file" : "browse your computer"}
        </button>
        <input id={id} ref={input} type="file" accept={accept} className="sr-only" onChange={(e) => pick(e.target.files?.[0])} />
      </div>
      {hint && !error && <p className="text-xs text-muted">{hint}</p>}
      {error && <p className="text-xs text-bad">{error}</p>}
    </div>
  );
}
