"use client";

import { useMemo, useState, type ReactNode } from "react";
import { ArrowUpDown, ChevronLeft, ChevronRight, Download, Search } from "lucide-react";
import { Button, Empty, Input, cx } from "./ui";

export type Column<T> = {
  key: string;
  header: string;
  render?: (row: T) => ReactNode;
  /** Plain value used for sorting and CSV export. */
  value?: (row: T) => string | number | null | undefined;
  sortable?: boolean;
  className?: string;
  hideOnMobile?: boolean;
};

function toCsv<T>(rows: T[], columns: Column<T>[]): string {
  const esc = (v: unknown) => {
    let s = v === null || v === undefined ? "" : String(v);
    if (/^[=+\-@]/.test(s) && !/^-?\d+(\.\d+)?$/.test(s)) s = "'" + s;
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const cols = columns.filter((c) => c.value);
  return [cols.map((c) => esc(c.header)).join(","), ...rows.map((r) => cols.map((c) => esc(c.value!(r))).join(","))].join("\r\n");
}

/**
 * List table with search, sorting, pagination (25 rows by default) and CSV export of the filtered rows
 * (PRD section 18). Server-paged lists use {@link Pager} instead.
 */
export function DataTable<T>({
  rows,
  columns,
  rowKey,
  searchText,
  searchPlaceholder = "Search",
  toolbar,
  pageSize = 25,
  exportName,
  emptyTitle = "Nothing here yet",
  emptyText,
  initialSort,
  onRowClick,
  rowClassName,
}: {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => string;
  searchText?: (row: T) => string;
  searchPlaceholder?: string;
  toolbar?: ReactNode;
  pageSize?: number;
  exportName?: string;
  emptyTitle?: string;
  emptyText?: ReactNode;
  initialSort?: { key: string; dir: "asc" | "desc" };
  onRowClick?: (row: T) => void;
  rowClassName?: (row: T) => string | undefined;
}) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState(initialSort ?? null);
  const [page, setPage] = useState(0);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let list = q && searchText ? rows.filter((r) => searchText(r).toLowerCase().includes(q)) : rows;
    if (sort) {
      const col = columns.find((c) => c.key === sort.key);
      if (col?.value) {
        list = [...list].sort((a, b) => {
          const av = col.value!(a);
          const bv = col.value!(b);
          if (av === bv) return 0;
          if (av === null || av === undefined) return 1;
          if (bv === null || bv === undefined) return -1;
          const cmp = typeof av === "number" && typeof bv === "number" ? av - bv : String(av).localeCompare(String(bv));
          return sort.dir === "asc" ? cmp : -cmp;
        });
      }
    }
    return list;
  }, [rows, query, sort, columns, searchText]);

  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const current = Math.min(page, pages - 1);
  const visible = filtered.slice(current * pageSize, current * pageSize + pageSize);

  const exportCsv = () => {
    const blob = new Blob(["﻿" + toCsv(filtered, columns)], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${exportName}.csv`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return (
    <div className="flex flex-col gap-3">
      {(searchText || toolbar || exportName) && (
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
          {searchText && (
            <div className="relative w-full sm:max-w-xs">
              <Search size={16} className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-faint" aria-hidden />
              <Input
                aria-label={searchPlaceholder}
                placeholder={searchPlaceholder}
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setPage(0);
                }}
                className="pl-9"
              />
            </div>
          )}
          {toolbar}
          {exportName && (
            <Button size="md" className="sm:ml-auto" icon={<Download size={16} />} onClick={exportCsv} disabled={filtered.length === 0}>
              Export CSV
            </Button>
          )}
        </div>
      )}
      <div className="overflow-x-auto rounded-[var(--radius)] border border-border bg-surface">
        <table className="w-full min-w-[640px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-border bg-surface-2 text-left">
              {columns.map((c) => (
                <th key={c.key} scope="col" className={cx("px-4 py-2.5 text-xs font-semibold tracking-wide text-muted uppercase", c.className, c.hideOnMobile && "hidden md:table-cell")}>
                  {c.sortable && c.value ? (
                    <button
                      type="button"
                      className="inline-flex items-center gap-1 uppercase hover:text-text"
                      onClick={() => setSort((s) => (s?.key === c.key ? { key: c.key, dir: s.dir === "asc" ? "desc" : "asc" } : { key: c.key, dir: "asc" }))}
                      aria-sort={sort?.key === c.key ? (sort.dir === "asc" ? "ascending" : "descending") : undefined}
                    >
                      {c.header}
                      <ArrowUpDown size={12} aria-hidden />
                    </button>
                  ) : (
                    c.header
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.map((row) => (
              <tr
                key={rowKey(row)}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
                className={cx("border-b border-border last:border-0", onRowClick && "cursor-pointer hover:bg-surface-2", rowClassName?.(row))}
              >
                {columns.map((c) => (
                  <td key={c.key} className={cx("px-4 py-3 align-top", c.className, c.hideOnMobile && "hidden md:table-cell")}>
                    {c.render ? c.render(row) : (c.value?.(row) ?? "")}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && <Empty title={query ? "No matches" : emptyTitle}>{query ? "Try a different search." : emptyText}</Empty>}
      </div>
      {filtered.length > pageSize && (
        <Pager page={current} totalPages={pages} totalElements={filtered.length} onPage={setPage} />
      )}
    </div>
  );
}

export function Pager({ page, totalPages, totalElements, onPage }: { page: number; totalPages: number; totalElements: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return <p className="text-xs text-muted">{totalElements} result{totalElements === 1 ? "" : "s"}</p>;
  return (
    <div className="flex items-center justify-between gap-2 text-sm">
      <p className="text-muted">
        Page {page + 1} of {totalPages} · {totalElements} results
      </p>
      <div className="flex gap-2">
        <Button size="sm" icon={<ChevronLeft size={14} />} disabled={page === 0} onClick={() => onPage(page - 1)}>
          Previous
        </Button>
        <Button size="sm" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
          Next <ChevronRight size={14} />
        </Button>
      </div>
    </div>
  );
}
