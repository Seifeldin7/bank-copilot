import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, CircleCheck, CircleDashed, CircleX, FileText, FileType2, ListTree, Search, ShieldAlert, Upload } from 'lucide-react'
import { documentsApi } from '@/api/endpoints'
import type { DocumentStatus, DocumentSummary, IngestionReport, StageLog } from '@/api/types'
import { useAuth } from '@/auth/context'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { Dialog } from '@/components/ui/Dialog'
import { FormField, Input, Select } from '@/components/ui/Field'
import { PageHeader, Stat, Table, TBody, Td, Th, THead, Tr } from '@/components/ui/Layout'
import { Alert, EmptyState, ErrorState, Spinner } from '@/components/ui/States'
import { useToast } from '@/components/ui/toast-context'
import { formatDate, formatDateTime, formatDuration, formatNumber } from '@/lib/format'
import { documentStatusTones, outcomeLabels, outcomeTones } from '@/lib/labels'

const MAX_BYTES = 10 * 1024 * 1024

export function DocumentsPage() {
  const { hasRole } = useAuth()
  const supervisor = hasRole('SUPERVISOR')
  const query = useQuery({ queryKey: ['documents'], queryFn: documentsApi.list })
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<DocumentStatus | 'ALL'>('ALL')
  const [uploadOpen, setUploadOpen] = useState(false)
  const [logFor, setLogFor] = useState<DocumentSummary | null>(null)
  const [retireFor, setRetireFor] = useState<DocumentSummary | null>(null)

  const documents = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (query.data ?? []).filter(
      (d) => (status === 'ALL' || d.status === status) && (!term || `${d.title} ${d.fileName} ${d.category}`.toLowerCase().includes(term)),
    )
  }, [query.data, search, status])

  const active = (query.data ?? []).filter((d) => d.status === 'ACTIVE')

  return (
    <>
      <PageHeader
        title="Knowledge base"
        description="Approved, synthetic training documents the copilot may cite. Superseded and retired versions are kept for audit but excluded from retrieval."
        actions={
          supervisor && (
            <Button icon={<Upload className="size-4" />} onClick={() => setUploadOpen(true)}>
              Upload document
            </Button>
          )
        }
      />

      {query.data && (
        <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <Stat label="Active documents" value={active.length} hint={`${query.data.length} including old versions`} />
          <Stat label="Pages indexed" value={formatNumber(active.reduce((s, d) => s + d.pageCount, 0))} />
          <Stat label="Chunks indexed" value={formatNumber(active.reduce((s, d) => s + d.chunkCount, 0))} hint="Vector + keyword indexes" />
          <Stat label="Flagged chunks" value={active.reduce((s, d) => s + d.suspiciousChunks, 0)} hint="Possible prompt injection, excluded" />
        </div>
      )}

      <Card>
        <div className="flex flex-wrap gap-3 border-b border-slate-100 px-5 py-4">
          <div className="relative min-w-60 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input className="pl-9" placeholder="Search title, file or category" value={search} onChange={(e) => setSearch(e.target.value)} />
          </div>
          <Select className="w-44" value={status} onChange={(e) => setStatus(e.target.value as DocumentStatus | 'ALL')} aria-label="Filter by status">
            <option value="ALL">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="SUPERSEDED">Superseded</option>
            <option value="RETIRED">Retired</option>
          </Select>
        </div>
        {query.isPending ? (
          <Spinner />
        ) : query.error ? (
          <div className="p-5">
            <ErrorState error={query.error} />
          </div>
        ) : documents.length === 0 ? (
          <EmptyState title="No documents match" />
        ) : (
          <Table>
            <THead>
              <tr>
                <Th>Document</Th>
                <Th>Category</Th>
                <Th>Version</Th>
                <Th>Pages</Th>
                <Th>Chunks</Th>
                <Th>Status</Th>
                <Th>Ingested</Th>
                {supervisor && <Th className="text-right">Actions</Th>}
              </tr>
            </THead>
            <TBody>
              {documents.map((doc) => (
                <Tr key={doc.id} className={doc.status !== 'ACTIVE' ? 'bg-slate-50/50' : undefined}>
                  <Td className="max-w-md">
                    <div className="flex items-start gap-2.5">
                      {doc.format === 'PDF' ? <FileType2 className="mt-0.5 size-4 shrink-0 text-red-500" /> : <FileText className="mt-0.5 size-4 shrink-0 text-sky-600" />}
                      <div className="min-w-0">
                        <p className={doc.status === 'ACTIVE' ? 'font-medium text-slate-900' : 'font-medium text-slate-500'}>{doc.title}</p>
                        <p className="truncate font-mono text-xs text-slate-400">{doc.fileName}</p>
                        {doc.suspiciousChunks > 0 && (
                          <Badge tone="amber" className="mt-1" icon={<ShieldAlert className="size-3" />}>
                            {doc.suspiciousChunks} chunk flagged
                          </Badge>
                        )}
                      </div>
                    </div>
                  </Td>
                  <Td className="whitespace-nowrap">{doc.category}</Td>
                  <Td>v{doc.version}</Td>
                  <Td className="tabular-nums">{doc.pageCount}</Td>
                  <Td className="tabular-nums">{doc.chunkCount}</Td>
                  <Td>
                    <Badge tone={documentStatusTones[doc.status]}>{doc.status.charAt(0) + doc.status.slice(1).toLowerCase()}</Badge>
                  </Td>
                  <Td className="whitespace-nowrap text-xs text-slate-500" title={formatDateTime(doc.ingestedAt)}>
                    {formatDate(doc.ingestedAt)}
                    <p>{doc.source === 'UPLOAD' ? `Uploaded by ${doc.ingestedBy?.fullName ?? 'supervisor'}` : 'Startup corpus sync'}</p>
                  </Td>
                  {supervisor && (
                    <Td className="whitespace-nowrap text-right">
                      <div className="flex justify-end gap-1">
                        <Button size="sm" variant="ghost" icon={<ListTree className="size-3.5" />} onClick={() => setLogFor(doc)}>
                          Log
                        </Button>
                        {doc.status !== 'RETIRED' && (
                          <Button size="sm" variant="ghost" icon={<Archive className="size-3.5" />} onClick={() => setRetireFor(doc)}>
                            Retire
                          </Button>
                        )}
                      </div>
                    </Td>
                  )}
                </Tr>
              ))}
            </TBody>
          </Table>
        )}
      </Card>

      {supervisor && <UploadDialog open={uploadOpen} onClose={() => setUploadOpen(false)} />}
      {logFor && <IngestionLogDialog document={logFor} onClose={() => setLogFor(null)} />}
      {retireFor && <RetireDialog document={retireFor} onClose={() => setRetireFor(null)} />}
    </>
  )
}

const stageIcon = {
  SUCCEEDED: <CircleCheck className="size-4 text-emerald-600" />,
  SKIPPED: <CircleDashed className="size-4 text-slate-400" />,
  FAILED: <CircleX className="size-4 text-red-600" />,
}

const stageNames: Record<StageLog['stage'], string> = {
  FINGERPRINT: '0 · Fingerprint',
  EXTRACT: '1 · Extract',
  CLEAN: '2 · Clean',
  CHUNK: '3 · Chunk',
  SCREEN: '3b · Screen',
  EMBED: '4 · Embed',
  INDEX: '5 · Index',
}

function StageList({ stages }: { stages: StageLog[] }) {
  return (
    <ol className="divide-y divide-slate-100 rounded-lg border border-slate-200">
      {stages.map((stage) => (
        <li key={stage.stage} className="flex items-start gap-3 px-3 py-2.5">
          <div className="pt-0.5">{stageIcon[stage.status]}</div>
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-slate-900">{stageNames[stage.stage]}</p>
            <p className="text-xs text-slate-500">{stage.detail}</p>
          </div>
          <span className="text-xs tabular-nums text-slate-400">{stage.status === 'SKIPPED' ? '—' : formatDuration(stage.durationMs)}</span>
        </li>
      ))}
    </ol>
  )
}

function ReportView({ report }: { report: IngestionReport }) {
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={outcomeTones[report.outcome]}>{outcomeLabels[report.outcome]}</Badge>
        <span className="text-sm font-medium text-slate-900">
          {report.title} · v{report.version}
        </span>
      </div>
      {report.outcome === 'DUPLICATE' && <Alert tone="warning">This exact file (same SHA-256 fingerprint) is already in the knowledge base, so nothing was re-processed.</Alert>}
      {report.outcome === 'NEW_VERSION' && <Alert tone="info">A document with this file name already existed. The previous version is now superseded and excluded from retrieval.</Alert>}
      {report.suspiciousChunks > 0 && (
        <Alert tone="warning" icon={<ShieldAlert className="size-4" />} title={`${report.suspiciousChunks} chunk flagged as possible prompt injection`}>
          Flagged chunks are stored for audit but are never used as evidence, and the copilot never follows instructions found inside documents.
        </Alert>
      )}
      <div className="grid grid-cols-3 gap-3">
        <Stat label="Pages / sections" value={report.pages} />
        <Stat label="Chunks" value={report.chunks} />
        <Stat label="Flagged" value={report.suspiciousChunks} />
      </div>
      <StageList stages={report.stages} />
    </div>
  )
}

function UploadDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [title, setTitle] = useState('')
  const [category, setCategory] = useState('')
  const [clientError, setClientError] = useState<string | null>(null)

  const upload = useMutation({
    mutationFn: () => documentsApi.upload(file!, title.trim() || undefined, category.trim() || undefined),
    onSuccess: (report) => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      notify({ tone: report.outcome === 'DUPLICATE' ? 'warning' : 'success', title: outcomeLabels[report.outcome], description: report.title })
    },
  })

  const reset = () => {
    setFile(null)
    setTitle('')
    setCategory('')
    setClientError(null)
    upload.reset()
    if (inputRef.current) inputRef.current.value = ''
  }

  const close = () => {
    reset()
    onClose()
  }

  const choose = (selected: File | null) => {
    setClientError(null)
    upload.reset()
    if (selected && !/\.(pdf|md)$/i.test(selected.name)) setClientError('Only .pdf and .md files are accepted.')
    else if (selected && selected.size > MAX_BYTES) setClientError('Files must be 10 MB or smaller.')
    setFile(selected)
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      size="lg"
      title="Upload a document"
      description="The file goes through the ingestion pipeline: fingerprint, extract, clean, chunk, screen, embed and index."
      footer={
        upload.data ? (
          <>
            <Button variant="secondary" onClick={reset}>
              Upload another
            </Button>
            <Button onClick={close}>Done</Button>
          </>
        ) : (
          <>
            <Button variant="secondary" onClick={close}>
              Cancel
            </Button>
            <Button icon={<Upload className="size-4" />} onClick={() => upload.mutate()} disabled={!file || !!clientError} loading={upload.isPending}>
              Ingest document
            </Button>
          </>
        )
      }
    >
      {upload.data ? (
        <ReportView report={upload.data} />
      ) : (
        <div className="space-y-4">
          <FormField label="File" htmlFor="file" required error={clientError ?? undefined} hint="PDF or Markdown, up to 10 MB. The type is checked by extension and by content.">
            <input
              ref={inputRef}
              id="file"
              type="file"
              accept=".pdf,.md,application/pdf,text/markdown"
              onChange={(e) => choose(e.target.files?.[0] ?? null)}
              className="block w-full text-sm text-slate-600 file:mr-3 file:rounded-lg file:border-0 file:bg-brand-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-brand-700 hover:file:bg-brand-100"
            />
          </FormField>
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Title" htmlFor="docTitle" hint="Defaults to the file name or the previous version's title">
              <Input id="docTitle" value={title} onChange={(e) => setTitle(e.target.value)} />
            </FormField>
            <FormField label="Category" htmlFor="docCategory" hint="e.g. Cards, Accounts, Compliance">
              <Input id="docCategory" value={category} onChange={(e) => setCategory(e.target.value)} />
            </FormField>
          </div>
          {upload.isPending && <Spinner label="Running the ingestion pipeline…" className="py-4" />}
          {upload.error && <ErrorState error={upload.error} />}
        </div>
      )}
    </Dialog>
  )
}

function IngestionLogDialog({ document, onClose }: { document: DocumentSummary; onClose: () => void }) {
  const query = useQuery({ queryKey: ['documents', document.id, 'log'], queryFn: () => documentsApi.ingestionLog(document.id) })
  return (
    <Dialog open onClose={onClose} size="lg" title="Ingestion log" description={`${document.title} · v${document.version} · ${document.fileName}`}>
      <div className="space-y-3">
        <p className="font-mono text-[11px] break-all text-slate-400">sha256 {document.contentSha256}</p>
        {query.isPending ? <Spinner /> : query.error ? <ErrorState error={query.error} /> : <StageList stages={query.data} />}
      </div>
    </Dialog>
  )
}

function RetireDialog({ document, onClose }: { document: DocumentSummary; onClose: () => void }) {
  const queryClient = useQueryClient()
  const { notify } = useToast()
  const retire = useMutation({
    mutationFn: () => documentsApi.retire(document.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] })
      notify({ tone: 'success', title: 'Document retired', description: `${document.title} v${document.version} is no longer used for answers.` })
      onClose()
    },
  })
  return (
    <Dialog
      open
      onClose={onClose}
      title="Retire document?"
      description={`${document.title} · v${document.version}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="danger" icon={<Archive className="size-4" />} onClick={() => retire.mutate()} loading={retire.isPending}>
            Retire
          </Button>
        </>
      }
    >
      <p className="text-sm text-slate-600">The copilot will stop citing this document immediately. It is not deleted: its metadata and chunks remain available for audit.</p>
      {retire.error && <ErrorState error={retire.error} className="mt-3" />}
    </Dialog>
  )
}
