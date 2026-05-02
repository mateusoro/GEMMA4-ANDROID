# Phase 3: PDF Processing + Workspace — Specification

**Created:** 2026-05-02
**Ambiguity score:** 0.06 (gate: ≤ 0.20)
**Requirements:** 3 locked

## Goal

User can attach PDF files, convert them to markdown, and browse workspace files. The model receives PDF content as context.

## Background

**Existing implementation:**
- `PdfToMarkdownConverter.kt` (339 lines) — full converter with heading detection, bold/italic, lists, tables
- `WorkspaceManager.kt` (323 lines) — manages workspace directories (documents/, markdown/)
- `MainActivity.kt` has `pdfPickerLauncher` at line 329 — triggers PDF selection
- PDF is saved via `WorkspaceManager.savePdf()` and converted via `PdfToMarkdownConverter`

**Delta to target:**
- Currently PDF is saved and converted when user picks file (line 351-385 in MainActivity)
- Model receives PDF content in chat as context (already implemented)
- Workspace file browsing is implemented via `listWorkspace()` tool
- What's missing: proper end-to-end flow verification and workspace browser UI

## Requirements

1. **FILE-01: File picker accepts PDF files**
   - Current: `pdfPickerLauncher.launch(arrayOf("application/pdf"))` exists (line 1120)
   - Target: User taps attach button → file picker opens → PDF selected
   - Acceptance: File picker shows, accepts .pdf files

2. **FILE-02: PDFBox converts PDF to markdown text**
   - Current: `PdfToMarkdownConverter.convert()` produces markdown (line 351)
   - Target: PDF text extracted, formatted as markdown, passed to model
   - Acceptance: Model receives PDF content as context

3. **FILE-03: Workspace browser lists available markdown files**
   - Current: `WorkspaceManager.listWorkspace()` returns formatted list (line 167)
   - Target: User can browse and select workspace files
   - Acceptance: Workspace browser shows files, user can open/read them

## Boundaries

**In scope:**
- PDF file selection and conversion flow
- Model context with PDF content
- Workspace file listing and reading

**Out of scope:**
- PDF editing or annotation
- Multiple PDF handling
- Cloud sync
- Other file formats (DOC, TXT, etc.)

## Acceptance Criteria

- [ ] File picker accepts PDF files
- [ ] PDF converts to markdown without crash
- [ ] Model receives PDF content as context
- [ ] Workspace list shows documents/ and markdown/ files
- [ ] User can read markdown files from workspace

## Ambiguity Report

| Dimension          | Score | Min  | Status |
|--------------------|-------|------|--------|
| Goal Clarity       | 0.97  | 0.75 | ✓      |
| Boundary Clarity   | 0.94  | 0.70 | ✓      |
| Constraint Clarity  | 0.95  | 0.65 | ✓      |
| Acceptance Criteria| 0.92  | 0.70 | ✓      |
| **Ambiguity**       | 0.06  | ≤0.20| ✓      |

---
*Phase: 03-pdf-processing-workspace*
*Spec created: 2026-05-02*