"use client";

import { useState } from "react";
import { Search } from "lucide-react";
import { api } from "@/lib/api";
import { useAction } from "@/lib/hooks";
import type { Publication } from "@/lib/types";
import { Dialog } from "./dialog";
import { Button, ErrorBox, SelectField, TextField } from "./ui";

export const PUB_TYPES = [
  ["JOURNAL_ARTICLE", "Journal article"],
  ["CONFERENCE_PAPER", "Conference paper"],
  ["BOOK_CHAPTER", "Book chapter"],
  ["OTHER", "Other"],
] as const;

type Form = {
  title: string;
  type: string;
  venueName: string;
  authors: string;
  year: string;
  volume: string;
  issue: string;
  pages: string;
  doi: string;
  url: string;
  issnIsbn: string;
  pubStatus: string;
};

function fromPublication(p?: Publication): Form {
  return {
    title: p?.title ?? "",
    type: p?.type ?? "JOURNAL_ARTICLE",
    venueName: p?.venueName ?? "",
    authors: p?.authors ?? "",
    year: p?.year ? String(p.year) : "",
    volume: p?.volume ?? "",
    issue: p?.issue ?? "",
    pages: p?.pages ?? "",
    doi: p?.doi ?? "",
    url: p?.url ?? "",
    issnIsbn: p?.issnIsbn ?? "",
    pubStatus: p?.pubStatus ?? "PUBLISHED",
  };
}

/** FR-PUB-01 form with DOI lookup from Crossref (FR-PUB-10). Saves as a draft. */
export function PublicationForm({ publication, onClose, onSaved }: { publication?: Publication; onClose: () => void; onSaved: (p: Publication) => void }) {
  const [form, setForm] = useState<Form>(fromPublication(publication));
  const set = (k: keyof Form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const save = useAction(async () => {
    const body = { ...form, year: form.year ? Number(form.year) : null };
    return publication ? api.put<Publication>(`/publications/${publication.id}`, body) : api.post<Publication>("/publications", body);
  });
  const lookup = useAction(async () => {
    const m = await api.get<Record<string, string | number | null>>(`/publications/doi-lookup?doi=${encodeURIComponent(form.doi)}`);
    setForm((f) => ({
      ...f,
      title: (m.title as string) ?? f.title,
      authors: (m.authors as string) || f.authors,
      venueName: (m.venueName as string) ?? f.venueName,
      year: m.year ? String(m.year) : f.year,
      volume: (m.volume as string) ?? f.volume,
      issue: (m.issue as string) ?? f.issue,
      pages: (m.pages as string) ?? f.pages,
      issnIsbn: (m.issnIsbn as string) ?? f.issnIsbn,
      type: (m.type as string) ?? f.type,
      doi: (m.doi as string) ?? f.doi,
      url: (m.url as string) ?? f.url,
    }));
    return true;
  });

  return (
    <Dialog
      open
      wide
      onClose={onClose}
      title={publication ? "Edit publication" : "Add a publication"}
      footer={
        <>
          <Button onClick={onClose}>Cancel</Button>
          <Button
            variant="primary"
            loading={save.pending}
            disabled={!form.title || !form.authors}
            onClick={async () => {
              const saved = await save.run();
              if (saved) onSaved(saved);
            }}
          >
            Save draft
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="flex items-end gap-2 sm:col-span-2">
          <div className="flex-1">
            <TextField label="DOI" placeholder="10.1234/abcd" value={form.doi} onChange={set("doi")} hint="Look it up to fill the details from Crossref." />
          </div>
          <Button icon={<Search size={15} />} loading={lookup.pending} disabled={!form.doi} onClick={() => lookup.run()} className="mb-[22px]">
            Look up
          </Button>
        </div>
        {lookup.error && (
          <div className="sm:col-span-2">
            <ErrorBox error={lookup.error} />
          </div>
        )}
        <div className="sm:col-span-2">
          <TextField label="Title" value={form.title} onChange={set("title")} required />
        </div>
        <SelectField label="Type" value={form.type} onChange={set("type")} required>
          {PUB_TYPES.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </SelectField>
        <SelectField label="Publication status" value={form.pubStatus} onChange={set("pubStatus")} required>
          <option value="ACCEPTED">Accepted</option>
          <option value="PUBLISHED">Published</option>
        </SelectField>
        <div className="sm:col-span-2">
          <TextField label="Journal or conference" value={form.venueName} onChange={set("venueName")} />
        </div>
        <div className="sm:col-span-2">
          <TextField label="Authors" value={form.authors} onChange={set("authors")} hint="In the order they appear, separated by commas." required />
        </div>
        <TextField label="Year" inputMode="numeric" value={form.year} onChange={set("year")} />
        <TextField label="ISSN / ISBN" value={form.issnIsbn} onChange={set("issnIsbn")} />
        <TextField label="Volume" value={form.volume} onChange={set("volume")} />
        <TextField label="Issue" value={form.issue} onChange={set("issue")} />
        <TextField label="Pages" value={form.pages} onChange={set("pages")} />
        <TextField label="URL" type="url" value={form.url} onChange={set("url")} />
        <div className="sm:col-span-2">
          <ErrorBox error={save.error} />
        </div>
      </div>
    </Dialog>
  );
}
