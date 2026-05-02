# Phase 3: PDF Processing + Workspace — Context

**Gathered:** 2026-05-02
**Status:** Ready for planning
**Source:** Codebase analysis + design change

<domain>
## Phase Boundary

User can attach PDF files, convert to markdown, browse workspace. When PDF is processed, the model is notified via a short message (not full content injection) that the PDF was saved and can be read via workspace tool.

Not in scope: PDF content injection into user messages, PDF editing, multiple PDFs.
</domain>

<decisions>
## Implementation Decisions

### PDF Processing Flow (NEW — notification-based)
1. User taps attach (paperclip) icon in chat UI
2. `pdfPickerLauncher.launch(arrayOf("application/pdf"))` opens file picker
3. On result: `WorkspaceManager.savePdf(context, uri)` saves PDF to documents/
4. `PdfToMarkdownConverter(context).convert(pdfPath)` extracts text → markdown
5. Markdown saved to workspace via `WorkspaceManager.saveMarkdown(context, filename, markdown)`
6. **NEW:** Send model a SHORT notification: "PDF salvo como {filename}.md"
7. **NEW:** User sees confirmation: "📄 PDF convertido: {filename}"
8. Model reads file via `readWorkspaceFile` tool when needed — NOT automatically injected

### Why This Approach?
- Large PDFs can exceed context window if content is injected
- Model can read file on-demand when it actually needs the content
- Keeps messages short and efficient
- Works better with long documents

### Workspace Structure
```
app.filesDir/workspace/
├── documents/   — original PDFs
└── markdown/      — converted .md files
```

### Model Notification Example
Old approach (bad for large PDFs):
```
User message: "Here is the PDF content: [5000 characters of markdown]..."
```

New approach (efficient):
```
System notification: "PDF salvo como relatorio2024.md — leia quando precisar"
User sees: "📄 PDF convertido: relatorio2024.md"
Model reads via tool when needed: readWorkspaceFile("relatorio2024.md")
```

### File Tools
- `listWorkspace()` — returns all files in both dirs
- `listMarkdown()` — returns only markdown files
- `readWorkspaceFile()` — strips prefix, reads from markdown/ dir
- `saveMarkdownFile()` — saves to markdown/ dir (used by PDF flow)

### PdfToMarkdownConverter Features
- Multi-page support (## Página N header)
- Heading detection (ALL CAPS, title case, short lines)
- Bold/italic conversion (**text**, _text_)
- List detection (-, *, 1., etc.)
- Table detection (| separators)
- Whitespace cleanup
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before implementing.**

- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` — pdfPickerLauncher (line 329), PDF result handling (line 351), attach button (line 1120)
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt` — saveMarkdown, listWorkspace
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` — readWorkspaceFile (path prefix stripping)
- `app/src/main/java/com/gemma/gpuchat/PdfToMarkdownConverter.kt` — conversion logic

No external specs — requirements fully captured in decisions above.
</canonical_refs>

<specifics>
## Specific Ideas

- PDF saved to: `{context.filesDir}/workspace/documents/{filename}.pdf`
- Markdown saved to: `{context.filesDir}/workspace/markdown/{filename}.md`
- Model notification: "PDF salvo como {nome}.md — você pode ler quando quiser"
- User confirmation: "📄 PDF convertido: {nome}.md"
- Filename sanitization: `baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(100)`
</specifics>

<deferred>
## Deferred Ideas

None — Phase 3 scope is fully specified.
</deferred>

---
*Phase: 03-pdf-processing-workspace*
*Context gathered: 2026-05-02 via codebase analysis + design change*
*Design change: notification-based instead of content injection*