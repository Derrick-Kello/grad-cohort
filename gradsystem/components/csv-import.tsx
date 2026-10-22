"use client";

import { useState } from "react";
import { api, ApiError } from "@/lib/api";
import { Dialog } from "./dialog";
import { FileInput } from "./file-input";
import { Banner, Button, ErrorBox } from "./ui";

type ImportResult = { valid: boolean; saved: boolean; rows: number; staff: number; students: number; errors: { row: number; column: string | null; message: string }[] };

const TEMPLATE =
  "type,email,number,first_name,last_name,phone,roles,programme_code,cohort,enrolled_on,supervisor\r\n" +
  "STAFF,k.mensah@university.edu,STF-4001,Kofi,Mensah,,LECTURER;SUPERVISOR,,,,\r\n" +
  "STUDENT,a.owusu@st.university.edu,PG-26050,Abena,Owusu,,,MASTERS,2026 Intake,2026-08-15,k.mensah@university.edu\r\n";

/**
 * FR-USR-02: validate every row first and report errors per row; nothing is saved if any row fails.
 * "Check file" runs validation only; "Import" saves when the whole file is valid.
 */
export function CsvImportDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [pending, setPending] = useState(false);

  const send = async (validateOnly: boolean) => {
    if (!file) return;
    setPending(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      const r = await api.upload<ImportResult>(`/admin/users/import?validateOnly=${validateOnly}`, form);
      setResult(r);
      if (r.saved) onDone();
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setPending(false);
    }
  };

  const downloadTemplate = () => {
    const url = URL.createObjectURL(new Blob([TEMPLATE], { type: "text/csv" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "grps-import-template.csv";
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title="Import staff and students from CSV"
      footer={
        <>
          <Button onClick={onClose}>{result?.saved ? "Close" : "Cancel"}</Button>
          {!result?.saved && (
            <>
              <Button loading={pending} disabled={!file} onClick={() => send(true)}>
                Check file
              </Button>
              <Button variant="primary" loading={pending} disabled={!file} onClick={() => send(false)}>
                Import
              </Button>
            </>
          )}
        </>
      }
    >
      <div className="flex flex-col gap-4 text-sm">
        <p className="text-muted">
          Columns: type, email, number, first_name, last_name, phone, roles (staff, separated by semicolons), programme_code, cohort,
          enrolled_on (YYYY-MM-DD) and supervisor (email or staff number) for students.{" "}
          <button type="button" onClick={downloadTemplate} className="font-medium text-primary underline">
            Download a template
          </button>
          . New accounts receive an activation email.
        </p>
        <FileInput label="CSV file" accept=".csv" maxMb={10} file={file} onChange={(f) => { setFile(f); setResult(null); }} />
        <ErrorBox error={error} />
        {result && result.saved && (
          <Banner tone="ok" title="Import complete">
            {result.staff} staff and {result.students} students created.
          </Banner>
        )}
        {result && !result.saved && result.valid && (
          <Banner tone="ok" title="The file is valid">
            {result.rows} rows: {result.staff} staff and {result.students} students. Select Import to save them.
          </Banner>
        )}
        {result && !result.valid && (
          <div className="flex flex-col gap-2">
            <Banner tone="bad" title={`${result.errors.length} problem${result.errors.length === 1 ? "" : "s"} found. Nothing was saved.`} />
            <div className="max-h-64 overflow-y-auto rounded-lg border border-border">
              <table className="w-full text-left text-sm">
                <thead className="bg-surface-2 text-xs text-muted uppercase">
                  <tr>
                    <th className="px-3 py-2">Row</th>
                    <th className="px-3 py-2">Column</th>
                    <th className="px-3 py-2">Problem</th>
                  </tr>
                </thead>
                <tbody>
                  {result.errors.map((e, i) => (
                    <tr key={i} className="border-t border-border">
                      <td className="tabular px-3 py-1.5">{e.row}</td>
                      <td className="px-3 py-1.5">{e.column}</td>
                      <td className="px-3 py-1.5">{e.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </Dialog>
  );
}
