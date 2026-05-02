---
phase: 03
plan: PDF-01
wave: 1
depends_on: []
requirements_addressed: [FILE-01, FILE-02, FILE-03]
autonomous: false
---

# Plan: PDF Processing + Workspace — Notification-Based Approach

## Objective
Change PDF processing to use model notification instead of content injection:
- Old: Send full PDF markdown in user message (bloats context)
- New: Save PDF → convert → notify model "PDF saved as X.md, read via workspace tool"

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (PDF result handling)

## must_haves
- PDF file picker accepts .pdf files
- PDF saves to documents/ and markdown is saved to markdown/
- Model receives notification message with filename (NOT full content)
- Workspace tools can read the saved markdown file

---

## Task 1: Review Current PDF Handling and Change to Notification

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (pdfPickerLauncher result, lines 329-390)
</read_first>

<acceptance_criteria>
- PDF saved to `documents/` via `WorkspaceManager.savePdf()`
- Markdown saved to `markdown/` via `WorkspaceManager.saveMarkdown()`
- Model receives: "PDF salvo como {nome}.md — você pode ler quando quiser usando a ferramenta readWorkspaceFile"
- User sees confirmation message in chat
</acceptance_criteria>

<action>
In MainActivity, find the PDF result handling block (around line 351).

**Current code does:**
1. Save PDF to documents/
2. Convert to markdown
3. Add full markdown content to user message → send to model

**Change to:**
1. Save PDF to documents/
2. Convert to markdown
3. Save markdown file to workspace (markdown/)
4. Send model a SHORT notification message: "PDF salvo como {nome}.md"
5. Show user a confirmation in chat with filename

The model will use `readWorkspaceFile("nome.md")` when it needs the content.

Example new flow:
```kotlin
// Save PDF
val pdfPath = WorkspaceManager.savePdf(context, uri)

// Convert to markdown
val converter = PdfToMarkdownConverter(context)
val markdown = converter.convert(pdfPath)

// Save markdown to workspace
val mdFileName = pdfFileNameWithoutExt + ".md"
WorkspaceManager.saveMarkdown(context, mdFileName.removeSuffix(".pdf"), markdown)

// Send notification to model (NOT full content)
val notification = "PDF salvo como: $mdFileName — você pode ler quando quiser"
sendMessageToModel(notification)

// Show user confirmation
messages = messages + ChatMessage(text = "📄 PDF convertido: $mdFileName", isUser = false)
```
</action>

---

## Task 2: Verify Workspace Tools Can Read Saved Files

<read_first>
- `app/src/main/java/com/gemma/gpuchat/AgentTools.kt` (readWorkspaceFile)
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt` (saveMarkdown)
</read_first>

<acceptance_criteria>
- `readWorkspaceFile("nome.md")` returns file content
- `WorkspaceManager.saveMarkdown()` creates file in markdown/ directory
- Path prefix stripping works (removes "markdown/" and "documents/" from filenames)
</acceptance_criteria>

<action>
Verify the readWorkspaceFile tool works correctly with the saved markdown files:
1. When model calls `readWorkspaceFile("teste.md")`
2. AgentTools strips any prefix and reads from markdown/ dir
3. Returns file content to model

Review the path stripping logic in AgentTools.readWorkspaceFile.
</action>

---

## Task 3: Build and Verify

<read_first>
- `app/build.gradle.kts`
</read_first>

<acceptance_criteria>
- `./gradlew assembleDebug` exits with code 0
- APK generated
- PDF selection → save → convert → notify flow works
</acceptance_criteria>

<action>
1. Run build: `.\gradlew.bat assembleDebug`
2. Deploy to device
3. Test: select PDF → model receives notification (not full content) → model can read file via tool
</action>

---

## Verification
1. File picker opens for PDF files
2. PDF saves to documents/
3. Markdown saves to markdown/
4. Model receives: "PDF salvo como X.md" (short message, not full content)
5. User sees confirmation message in chat
6. Model can read file via `readWorkspaceFile` tool when needed