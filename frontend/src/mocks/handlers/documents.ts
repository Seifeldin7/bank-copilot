import { http } from 'msw'
import type { DocumentSummary, IngestionReport, StageLog } from '@/api/types'
import { db, newEvent, persist, ref, stageLogFor, type MockDocument } from '../db'
import type { Passage } from '../knowledge'
import { authenticate, badRequest, conflict, handle, notFound, param } from '../http'

const MAX_BYTES = 10 * 1024 * 1024
const injectionPattern = /(ignore (all )?(the )?(previous|prior|above) (instructions|rules)|system override|system prompt|you are now|disregard (your|the) (rules|instructions))/gi

const toSummary = ({ stageLog: _stageLog, requestCategory: _requestCategory, ...doc }: MockDocument): DocumentSummary => doc

async function sha256(buffer: ArrayBuffer) {
  const digest = await crypto.subtle.digest('SHA-256', buffer)
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

function titleFromFileName(fileName: string) {
  return fileName
    .replace(/\.(pdf|md)$/i, '')
    .split(/[-_\s]+/)
    .filter(Boolean)
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ')
}

function markdownSections(text: string) {
  const sections: { heading: string; body: string }[] = []
  let current = { heading: 'Introduction', body: '' }
  for (const line of text.split(/\r?\n/)) {
    const heading = /^#{1,6}\s+(.*)$/.exec(line)
    if (heading) {
      if (current.body.trim()) sections.push(current)
      current = { heading: heading[1].trim(), body: '' }
    } else {
      current.body += `${line}\n`
    }
  }
  if (current.body.trim()) sections.push(current)
  return sections
}

function chunk(text: string, size = 800) {
  const sentences = text.replace(/\s+/g, ' ').trim().split(/(?<=[.!?])\s+/)
  const chunks: string[] = []
  let current = ''
  for (const sentence of sentences) {
    if ((current + ' ' + sentence).length > size && current) {
      chunks.push(current.trim())
      current = sentence
    } else {
      current = `${current} ${sentence}`
    }
  }
  if (current.trim()) chunks.push(current.trim())
  return chunks
}

export const documentHandlers = [
  http.get(
    '/api/documents',
    handle(({ request }) => {
      authenticate(request)
      return [...db.documents]
        .sort((a, b) => a.title.localeCompare(b.title) || b.version - a.version)
        .map(toSummary)
    }),
  ),

  http.get(
    '/api/documents/:id/ingestion-log',
    handle(({ request, params }) => {
      authenticate(request, 'SUPERVISOR')
      const doc = db.documents.find((d) => d.id === param(params, 'id'))
      if (!doc) throw notFound('Document not found.')
      return doc.stageLog
    }),
  ),

  http.delete(
    '/api/documents/:id',
    handle(({ request, params }) => {
      const actor = authenticate(request, 'SUPERVISOR')
      const doc = db.documents.find((d) => d.id === param(params, 'id'))
      if (!doc) throw notFound('Document not found.')
      if (doc.status === 'RETIRED') throw conflict('This document is already retired.')
      doc.status = 'RETIRED'
      db.events.unshift(newEvent('DOCUMENT_RETIRED', ref(actor), 'Document', doc.fileName, `${doc.title} v${doc.version} removed from retrieval`, new Date().toISOString()))
      persist()
      return toSummary(doc)
    }),
  ),

  http.post(
    '/api/documents',
    handle(async ({ request }) => {
      const actor = authenticate(request, 'SUPERVISOR')
      const form = await request.formData()
      const file = form.get('file')
      if (!(file instanceof File)) throw badRequest('A file is required.', [{ field: 'file', message: 'Choose a PDF or Markdown file.' }])
      const fileName = file.name
      const extension = fileName.split('.').pop()?.toLowerCase()
      if (extension !== 'pdf' && extension !== 'md')
        throw badRequest('Unsupported file type.', [{ field: 'file', message: 'Only .pdf and .md files are accepted.' }])
      if (file.size === 0) throw badRequest('The file is empty.', [{ field: 'file', message: 'The file is empty.' }])
      if (file.size > MAX_BYTES) throw badRequest('File too large.', [{ field: 'file', message: 'Files must be 10 MB or smaller.' }])

      const buffer = await file.arrayBuffer()
      const bytes = new Uint8Array(buffer)
      let text = ''
      if (extension === 'pdf') {
        const header = new TextDecoder('latin1').decode(bytes.slice(0, 5))
        if (header !== '%PDF-') throw badRequest('File content does not match its extension.', [{ field: 'file', message: 'This file is not a valid PDF.' }])
        text = new TextDecoder('latin1').decode(bytes)
      } else {
        try {
          text = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
        } catch {
          throw badRequest('File content does not match its extension.', [{ field: 'file', message: 'Markdown files must be UTF-8 text.' }])
        }
        if (text.includes('\u0000')) throw badRequest('File content does not match its extension.', [{ field: 'file', message: 'Markdown files must be plain text.' }])
      }

      const hash = await sha256(buffer)
      const fingerprintStart = performance.now()
      const existing = db.documents.find((d) => d.contentSha256 === hash)
      if (existing) {
        const stages: StageLog[] = [
          { stage: 'FINGERPRINT', status: 'SUCCEEDED', durationMs: Math.max(1, Math.round(performance.now() - fingerprintStart)), detail: `Identical content already stored as "${existing.title}" v${existing.version}` },
          ...(['EXTRACT', 'CLEAN', 'CHUNK', 'SCREEN', 'EMBED', 'INDEX'] as const).map((stage) => ({ stage, status: 'SKIPPED' as const, durationMs: 0, detail: 'Skipped: duplicate document' })),
        ]
        db.events.unshift(newEvent('DOCUMENT_DUPLICATE_SKIPPED', ref(actor), 'Document', fileName, `Duplicate of ${existing.title} v${existing.version}`, new Date().toISOString()))
        persist()
        const report: IngestionReport = {
          documentId: existing.id,
          title: existing.title,
          outcome: 'DUPLICATE',
          version: existing.version,
          pages: existing.pageCount,
          chunks: existing.chunkCount,
          suspiciousChunks: existing.suspiciousChunks,
          stages,
        }
        return report
      }

      const previous = db.documents.find((d) => d.fileName === fileName && d.status === 'ACTIVE')
      const format = extension === 'pdf' ? 'PDF' : 'MARKDOWN'
      const sections = format === 'MARKDOWN' ? markdownSections(text) : []
      const pages = format === 'PDF' ? Math.max(1, (text.match(/\/Type\s*\/Page(?!s)/g) ?? []).length) : Math.max(1, sections.length)
      const documentId = `doc-${crypto.randomUUID().slice(0, 6)}`
      const newPassages: Passage[] = []
      for (const section of sections) {
        for (const piece of chunk(section.body)) {
          injectionPattern.lastIndex = 0
          newPassages.push({ id: `chk-${crypto.randomUUID().slice(0, 6)}`, documentId, page: null, section: section.heading, text: piece, suspicious: injectionPattern.test(piece) })
        }
      }
      injectionPattern.lastIndex = 0
      const chunks = format === 'PDF' ? Math.max(1, Math.ceil(text.length / 4000)) : Math.max(1, newPassages.length)
      const suspicious = format === 'PDF' ? (text.match(injectionPattern) ?? []).length : newPassages.filter((p) => p.suspicious).length
      const heading = format === 'MARKDOWN' ? /^#\s+(.+)$/m.exec(text)?.[1]?.trim() : undefined
      const title = (form.get('title') as string | null)?.trim() || previous?.title || heading || titleFromFileName(fileName)
      const category = (form.get('category') as string | null)?.trim() || previous?.category || 'Uncategorised'

      if (previous) previous.status = 'SUPERSEDED'
      const stageLog = stageLogFor(pages, chunks, suspicious, format)
      stageLog[0] = {
        stage: 'FINGERPRINT',
        status: 'SUCCEEDED',
        durationMs: 3,
        detail: previous ? `New content for ${fileName}; previous v${previous.version} marked superseded` : 'SHA-256 computed; no existing document with this hash',
      }
      const doc: MockDocument = {
        id: documentId,
        title,
        fileName,
        format,
        category,
        version: previous ? previous.version + 1 : 1,
        pageCount: pages,
        chunkCount: chunks,
        suspiciousChunks: suspicious,
        status: 'ACTIVE',
        source: 'UPLOAD',
        contentSha256: hash,
        ingestedBy: ref(actor),
        ingestedAt: new Date().toISOString(),
        stageLog,
        requestCategory: previous?.requestCategory ?? null,
      }
      db.documents.push(doc)
      db.uploadedPassages.push(...newPassages)
      db.events.unshift(
        newEvent(previous ? 'DOCUMENT_NEW_VERSION' : 'DOCUMENT_INGESTED', ref(actor), 'Document', fileName, `${title} v${doc.version}: ${pages} pages, ${chunks} chunks, ${suspicious} suspicious`, doc.ingestedAt),
      )
      persist()
      const report: IngestionReport = {
        documentId,
        title,
        outcome: previous ? 'NEW_VERSION' : 'INGESTED',
        version: doc.version,
        pages,
        chunks,
        suspiciousChunks: suspicious,
        stages: stageLog,
      }
      return report
    }, [900, 1600]),
  ),
]
