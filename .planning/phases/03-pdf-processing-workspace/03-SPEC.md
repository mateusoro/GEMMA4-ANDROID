# Phase 3: PDF Processing + Workspace — Specification

**Created:** 2026-05-02
**Ambiguity score:** 0.06 (gate: ≤ 0.20)
**Requirements:** 3 locked

## Goal

User can attach PDF files, convert to markdown, and browse workspace. When PDF is processed, the model is notified via a system message that the PDF was saved and can be read via the workspace tool — instead of injecting the full content into the user message.

## Background

**Existing implementation:**
- `PdfToMarkdownConverter.kt` (339 lines) — full converter with heading detection, bold/italic, lists, tables
- `WorkspaceManager.kt` (323 lines) — manages workspace directories (documents/, markdown/)
- `MainActivity.kt` has `pdfPickerLauncher` at line 329 — triggers PDF selection

**Current behavior (problematic):**
- PDF is converted and content is injected directly into user message as "Here is the PDF content: [long text]..."
- This can exceed context limits for large PDFs

**New behavior (desired):**
1. User selects PDF → saved to documents/
2. Converted to markdown → saved to markdown/{filename}.md
3. System message sent to model: "PDF saved as {filename}.md — use readWorkspaceFile tool to read it when needed"
4. Model can read the file via its tool when it needs the content

## Requirements

1. **FILE-01: File picker accepts PDF files**
   - Target: User taps attach button → file picker opens → PDF selected
   - Acceptance: File picker shows, accepts .pdf files

2. **FILE-02: PDFBox converts PDF to markdown text**
   - Target: PDF converted and saved to markdown/ directory
   - Notification sent to model instead of full content injection
   - Acceptance: Model receives notification with filename, can read via tool

3. **FILE-03: Workspace browser lists available markdown files**
   - Target: User can browse workspace files
   - Model can read markdown files via `readWorkspaceFile` tool
   - Acceptance: Workspace list shows documents/ and markdown/ files

## Boundaries

**In scope:**
- PDF file selection and conversion flow
- Model notification (not content injection)
- Workspace file listing and reading via tools

**Out of scope:**
- PDF content injection into user messages
- PDF editing or annotation
- Multiple PDF handling

## Key Change from Original Plan

**Old approach:** Inject full PDF markdown into user message → bloats context
**New approach:**
1. Save PDF → convert → save markdown to workspace
2. Send model a notification: "PDF saved as {name}.md — read it via workspace when needed"
3. Model uses `readWorkspaceFile` tool to access content

This keeps messages short and lets model read content on-demand.

## Acceptance Criteria

- [ ] File picker accepts PDF files
- [ ] PDF saves to documents/ and converts to markdown in markdown/
- [ ] Model receives notification message (not full content)
- [ ] Model can read markdown file via `readWorkspaceFile` tool
- [ ] Workspace browser shows documents/ and markdown/ files

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
*Updated: 2026-05-02 — model notification instead of content injection*