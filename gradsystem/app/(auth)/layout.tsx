import { GraduationCap } from "lucide-react";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <div className="mb-6 flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary text-on-primary">
          <GraduationCap size={22} aria-hidden />
        </span>
        <div className="leading-tight">
          <p className="text-lg font-semibold">Graduate Research Progress System</p>
          <p className="text-sm text-muted">Graduate School</p>
        </div>
      </div>
      <div className="w-full max-w-md rounded-2xl border border-border bg-surface p-6 shadow-[var(--shadow)] sm:p-8">{children}</div>
    </div>
  );
}
