"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Bell, ChevronDown, Ellipsis, GraduationCap, Lock, LogOut, User, X } from "lucide-react";
import { homeFor, useAuth } from "@/lib/auth";
import { useApi } from "@/lib/hooks";
import { useUnreadCount } from "@/lib/live";
import { NAV, VIEW_LABEL, isActive, titleFor, viewForPath, type NavItem, type View } from "@/lib/nav";
import { initials } from "@/lib/format";
import type { ThesisOverview } from "@/lib/types";
import { Loading, cx } from "./ui";
import { useToast } from "./toast";

/**
 * The persistent frame (PRD section 18): a fixed 240 px sidebar from 1024 px, a 72 px icon rail with labels on
 * hover from 640 px, and a bottom tab bar on phones. None of them collapse or disappear on navigation.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { status, me, view, views, setView } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const pathView = viewForPath(pathname);

  useEffect(() => {
    if (status === "anonymous") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, pathname, router]);

  useEffect(() => {
    // Opening a link for another of the user's roles switches the role view to match.
    if (pathView && views.includes(pathView) && pathView !== view) setView(pathView);
  }, [pathView, views, view, setView]);

  if (status !== "signed-in" || !me) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Loading label="Opening your workspace" />
      </div>
    );
  }

  const forbidden = pathView !== null && !views.includes(pathView);
  const active = pathView && views.includes(pathView) ? pathView : view;

  return (
    <div className="min-h-screen">
      <Sidebar view={active} pathname={pathname} />
      <div className="flex min-h-screen flex-col pb-20 sm:pb-0 sm:pl-[72px] lg:pl-[240px]">
        <TopBar view={active} pathname={pathname} />
        <main id="main" className="mx-auto w-full max-w-[1280px] flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {forbidden ? (
            <div className="py-16 text-center">
              <p className="text-lg font-semibold">This page belongs to a role you do not hold.</p>
              <Link className="mt-3 inline-block text-primary underline" href={homeFor(view)}>
                Go to your dashboard
              </Link>
            </div>
          ) : (
            children
          )}
        </main>
      </div>
      <BottomTabs view={active} pathname={pathname} />
    </div>
  );
}

function useLocks(view: View | null) {
  const { data } = useApi<ThesisOverview>(view === "student" ? "/thesis/me" : null);
  return { thesis: data?.locked ? data.lockedReason ?? "Locked" : null };
}

function Sidebar({ view, pathname }: { view: View | null; pathname: string }) {
  const items = view ? NAV[view] : [];
  const locks = useLocks(view);
  return (
    <nav
      aria-label="Main"
      className="fixed inset-y-0 left-0 z-30 hidden w-[72px] flex-col bg-sidebar text-sidebar-text sm:flex lg:w-[240px]"
    >
      <Link href={homeFor(view)} className="flex h-16 shrink-0 items-center gap-3 px-5 text-sidebar-strong lg:px-5">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#2c6aa3] text-white">
          <GraduationCap size={18} aria-hidden />
        </span>
        <span className="hidden flex-col leading-tight lg:flex">
          <span className="text-sm font-semibold">GRPS</span>
          <span className="text-[11px] text-sidebar-text">Graduate School</span>
        </span>
      </Link>
      <ul className="flex flex-1 flex-col gap-0.5 overflow-y-auto px-3 pb-4">
        {items.map((item) => (
          <SideLink key={item.href} item={item} active={isActive(item, pathname)} lockReason={item.lockKey ? locks[item.lockKey] : null} />
        ))}
      </ul>
    </nav>
  );
}

function SideLink({ item, active, lockReason }: { item: NavItem; active: boolean; lockReason: string | null }) {
  const Icon = item.icon;
  return (
    <li className="group relative">
      <Link
        href={item.href}
        aria-current={active ? "page" : undefined}
        title={lockReason ?? undefined}
        className={cx(
          "flex h-10 items-center gap-3 rounded-lg px-3 text-sm font-medium transition-colors",
          "justify-center lg:justify-start",
          active ? "bg-sidebar-active text-sidebar-strong" : "hover:bg-sidebar-hover hover:text-sidebar-strong",
          lockReason && "opacity-55",
        )}
      >
        <Icon size={18} aria-hidden className="shrink-0" />
        <span className="hidden truncate lg:inline">{item.label}</span>
        {lockReason && <Lock size={13} aria-label="Locked" className="ml-auto hidden lg:inline" />}
        <span className="sr-only lg:hidden">{item.label}</span>
      </Link>
      {/* Icon rail: the label appears on hover and keyboard focus. */}
      <span
        role="tooltip"
        className="pointer-events-none absolute top-1/2 left-[60px] z-40 hidden -translate-y-1/2 rounded-md bg-[#0b1620] px-2.5 py-1.5 text-xs whitespace-nowrap text-white shadow-lg group-focus-within:block group-hover:block lg:!hidden"
      >
        {item.label}
        {lockReason ? ` (locked: ${lockReason})` : ""}
      </span>
    </li>
  );
}

function BottomTabs({ view, pathname }: { view: View | null; pathname: string }) {
  const [open, setOpen] = useState(false);
  const items = view ? NAV[view] : [];
  const primary = items.slice(0, 4);
  const rest = items.slice(4);
  return (
    <>
      <nav aria-label="Main" className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface sm:hidden">
        <ul className="grid grid-cols-5">
          {primary.map((item) => {
            const Icon = item.icon;
            const active = isActive(item, pathname);
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cx("flex h-16 flex-col items-center justify-center gap-1 px-1 text-[11px]", active ? "text-primary" : "text-muted")}
                >
                  <Icon size={20} aria-hidden />
                  <span className="max-w-full truncate">{item.label.replace("My ", "")}</span>
                </Link>
              </li>
            );
          })}
          <li>
            <button
              type="button"
              onClick={() => setOpen(true)}
              aria-expanded={open}
              className={cx(
                "flex h-16 w-full flex-col items-center justify-center gap-1 text-[11px]",
                rest.some((i) => isActive(i, pathname)) ? "text-primary" : "text-muted",
              )}
            >
              <Ellipsis size={20} aria-hidden />
              More
            </button>
          </li>
        </ul>
      </nav>
      {open && (
        <div className="fixed inset-0 z-40 bg-black/40 sm:hidden" onClick={() => setOpen(false)}>
          <div className="absolute inset-x-0 bottom-0 rounded-t-2xl bg-surface p-3 pb-6" onClick={(e) => e.stopPropagation()}>
            <div className="mb-2 flex items-center justify-between px-2">
              <p className="text-sm font-semibold">More</p>
              <button type="button" aria-label="Close" onClick={() => setOpen(false)} className="p-1 text-muted">
                <X size={18} />
              </button>
            </div>
            <ul className="grid grid-cols-3 gap-1">
              {rest.map((item) => {
                const Icon = item.icon;
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      onClick={() => setOpen(false)}
                      className={cx(
                        "flex flex-col items-center gap-1 rounded-lg px-2 py-3 text-center text-xs",
                        isActive(item, pathname) ? "bg-primary-soft text-primary" : "text-text hover:bg-surface-2",
                      )}
                    >
                      <Icon size={20} aria-hidden />
                      {item.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>
      )}
    </>
  );
}

function TopBar({ view, pathname }: { view: View | null; pathname: string }) {
  const { me, views, setView, logout } = useAuth();
  const router = useRouter();
  const toast = useToast();
  const [unread] = useUnreadCount(!!me, (n) => toast(n.title));
  const [menu, setMenu] = useState<"role" | "profile" | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setMenu(null);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, []);

  return (
    <header className="sticky top-0 z-20 border-b border-border bg-surface/95 backdrop-blur">
      <div ref={ref} className="mx-auto flex h-16 w-full max-w-[1280px] items-center gap-3 px-4 sm:px-6 lg:px-8">
        <a href="#main" className="sr-only focus:not-sr-only">
          Skip to content
        </a>
        <p className="min-w-0 flex-1 truncate text-base font-semibold">{titleFor(pathname, view)}</p>

        {views.length > 1 && view && (
          <div className="relative">
            <button
              type="button"
              onClick={() => setMenu(menu === "role" ? null : "role")}
              aria-haspopup="menu"
              aria-expanded={menu === "role"}
              className="flex h-9 items-center gap-1.5 rounded-lg border border-border-strong px-3 text-sm font-medium hover:bg-surface-2"
            >
              <span className="hidden text-muted sm:inline">Role:</span> {VIEW_LABEL[view]}
              <ChevronDown size={14} aria-hidden />
            </button>
            {menu === "role" && (
              <ul role="menu" className="absolute right-0 mt-2 w-48 overflow-hidden rounded-lg border border-border bg-surface py-1 shadow-[var(--shadow-lg)]">
                {views.map((v) => (
                  <li key={v}>
                    <button
                      role="menuitemradio"
                      aria-checked={v === view}
                      type="button"
                      onClick={() => {
                        setView(v);
                        setMenu(null);
                        router.push(homeFor(v));
                      }}
                      className={cx("w-full px-3 py-2 text-left text-sm hover:bg-surface-2", v === view && "font-semibold text-primary")}
                    >
                      {VIEW_LABEL[v]}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        <Link
          href="/notifications"
          className="relative flex h-9 w-9 items-center justify-center rounded-lg hover:bg-surface-2"
          aria-label={`Notifications${unread ? `, ${unread} unread` : ""}`}
        >
          <Bell size={19} aria-hidden />
          {!!unread && (
            <span className="tabular absolute -top-0.5 -right-0.5 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-bad px-1 text-[10px] font-semibold text-white">
              {unread > 99 ? "99+" : unread}
            </span>
          )}
        </Link>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenu(menu === "profile" ? null : "profile")}
            aria-haspopup="menu"
            aria-expanded={menu === "profile"}
            aria-label="Profile menu"
            className="flex h-9 w-9 items-center justify-center rounded-full bg-primary-soft text-xs font-semibold text-primary"
          >
            {initials(me?.name)}
          </button>
          {menu === "profile" && (
            <div role="menu" className="absolute right-0 mt-2 w-60 overflow-hidden rounded-lg border border-border bg-surface shadow-[var(--shadow-lg)]">
              <div className="border-b border-border px-3 py-2.5">
                <p className="truncate text-sm font-semibold">{me?.name}</p>
                <p className="truncate text-xs text-muted">{me?.email}</p>
              </div>
              <Link role="menuitem" href="/profile" onClick={() => setMenu(null)} className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-surface-2">
                <User size={15} aria-hidden /> Profile
              </Link>
              <button
                role="menuitem"
                type="button"
                onClick={async () => {
                  await logout();
                  router.replace("/login");
                }}
                className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm hover:bg-surface-2"
              >
                <LogOut size={15} aria-hidden /> Sign out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
