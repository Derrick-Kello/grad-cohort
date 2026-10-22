"use client";

import { useState } from "react";
import { Lock } from "lucide-react";
import { FileInput } from "@/components/file-input";
import { IN_PIPELINE, ThesisVersion } from "@/components/thesis";
import { useToast } from "@/components/toast";
import { Banner, Button, Card, ErrorBox, Loading, PageHeader, TextField, TextareaField } from "@/components/ui";
import { api } from "@/lib/api";
import { useAction, useApi } from "@/lib/hooks";
import type { Thesis, ThesisOverview } from "@/lib/types";

export default function ThesisPage() {
  const { data, error, mutate } = useApi<ThesisOverview>("/thesis/me", {
    // Poll while a version is moving through the format and screening gates.
    refreshInterval: (d) => (d?.versions[0] && IN_PIPELINE.includes(d.versions[0].status) ? 2500 : 0),
  });

  return (
    <>
      <PageHeader
        title="Thesis"
        description="Every version is checked for format first, then screened for similarity and AI-generated content. A score above the limit is rejected; revise and upload a new version."
      />
      <ErrorBox error={error} onRetry={() => mutate()} />
      {!data && !error && <Loading />}
      {data?.locked && (
        <Card>
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <span className="flex h-12 w-12 items-center justify-center rounded-full bg-grey-bg text-grey">
              <Lock size={22} aria-hidden />
            </span>
            <p className="font-semibold">Thesis submission is locked</p>
            <p className="max-w-md text-sm text-muted">{data.lockedReason}</p>
          </div>
        </Card>
      )}
      {data && !data.locked && (
        <div className="flex flex-col gap-6">
          {data.canSubmit ? (
            <UploadForm overview={data} onDone={() => mutate()} />
          ) : (
            data.blockedReason && <Banner tone={data.versions[0]?.status === "ACCEPTED_FOR_EXAMINATION" ? "ok" : "info"}>{data.blockedReason}</Banner>
          )}
          {data.versions.map((t: Thesis, i: number) => (
            <ThesisVersion key={t.id} t={t} defaultOpen={i === 0} />
          ))}
        </div>
      )}
    </>
  );
}

function UploadForm({ overview, onDone }: { overview: ThesisOverview; onDone: () => void }) {
  const latest = overview.versions[0];
  const [title, setTitle] = useState(latest?.title ?? "");
  const [abstract, setAbstract] = useState(latest?.abstractText ?? "");
  const [keywords, setKeywords] = useState(latest?.keywords ?? "");
  const [file, setFile] = useState<File | null>(null);
  const toast = useToast();
  const { run, pending, error } = useAction(async () => {
    const form = new FormData();
    form.append("file", file!);
    form.append("title", title);
    form.append("abstract", abstract);
    form.append("keywords", keywords);
    return api.upload<Thesis>("/thesis", form);
  });
  return (
    <Card title={latest ? `Upload version ${latest.versionNo + 1}` : "Submit your thesis"} description={`Limits: similarity ${overview.similarityLimit}%, AI content ${overview.aiLimit}%.`}>
      <form
        className="flex flex-col gap-4"
        onSubmit={async (e) => {
          e.preventDefault();
          if (await run()) {
            toast("Thesis uploaded. The format check has started.");
            setFile(null);
            onDone();
          }
        }}
      >
        <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required />
        <TextareaField label="Abstract" value={abstract} onChange={(e) => setAbstract(e.target.value)} rows={5} required hint="300 words maximum." />
        <TextField label="Keywords" value={keywords} onChange={(e) => setKeywords(e.target.value)} required hint="Separate with commas." />
        <FileInput
          label="Thesis file"
          accept=".docx,.pdf"
          maxMb={overview.maxMegabytes}
          file={file}
          onChange={setFile}
          hint={`DOCX is preferred because it allows the full format check. PDF also accepted. Up to ${overview.maxMegabytes} MB.`}
        />
        <ErrorBox error={error} />
        <div>
          <Button type="submit" variant="primary" loading={pending} disabled={!file || !title || !abstract || !keywords}>
            Upload and start checks
          </Button>
        </div>
      </form>
    </Card>
  );
}
