# Phase 3: PDF Processing + Workspace — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Source:** Codebase analysis

<domain>
## Phase Boundary

User can attach PDF files, convert to markdown, browse workspace. Model receives PDF content as context. Workspace file browser shows documents/ and markdown/ directories.

Not in scope: PDF editing, multiple PDFs, cloud sync, other file formats.
</domain>

<decisions>
## Implementation Decisions

### PDF Processing Flow
1. User taps attach (paperclip) icon in chat UI
2. `pdfPickerLauncher.launch(arrayOf("application/pdf"))` opens file picker
3. On result: `WorkspaceManager.savePdf(context, uri)` saves PDF to documents/
4. `PdfToMarkdownConverter(context).convert(pdfPath)` extracts text → markdown
5. Markdown added to chat as user message → sent to model via `sendMessage()`
6. Model receives PDF content in context and can reference it

### Workspace Structure
```
app.filesDir/workspace/
├── documents/   — original PDFs
└── markdown/      — converted .md files
```

### File Tools
- `listWorkspace()` — returns all files in both dirs
- `listMarkdown()` — returns only .md files
- `readWorkspaceFile()` — strips prefix, reads from correct dir
- `saveMarkdownFile()` — saves to markdown/ dir

### PdfToMarkdownConverter Features
- Multi-page support (## Página N header)
- Heading detection (ALL CAPS, title case, short lines)
- Bold/italic conversion (**text**, _text_)
- List detection (-, *, 1., etc.)
- Table detection (| separators)
- Whitespace cleanup

### AGENTS.md Learned Patterns
- Context stored as instance field via `WorkspaceManager.init(context)` at LaunchedEffect
- File paths use `context.filesDir` not external storage
- `fileExists()` checks before read operations
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before implementing.**

- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` — pdfPickerLauncher (line 329), PDF result handling (line 351), attach button (line 1120)
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt` — all file operations, directory structure
- `app/src/main/java/com/gemma/gpuchat/PdfToMarkdownConverter.kt` — conversion logic, markdown formatting

No external specs — requirements fully captured in decisions above.
</canonical_refs>

<specifics>
## Specific Ideas

- PDF saved to: `{context.filesDir}/workspace/documents/{filename}.pdf`
- Markdown saved to: `{context.filesDir}/workspace/markdown/{filename}.md`
- ListWorkspace returns formatted string with emojis: 📁 for documents, 📝 for markdown
- PDF conversion adds "## Página N\n\n" headers between pages
</specifics>

<deferred>
## Deferred Ideas

None — Phase 3 scope is fully specified.
</deferred>

---
*Phase: 03-pdf-processing-workspace*
*Context gathered: 2026-05-02 via codebase analysis*