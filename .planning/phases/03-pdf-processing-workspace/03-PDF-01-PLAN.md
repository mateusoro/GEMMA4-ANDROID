---
phase: 03
plan: PDF-01
wave: 1
depends_on: []
requirements_addressed: [FILE-01, FILE-02, FILE-03]
autonomous: false
---

# Plan: PDF Processing + Workspace — Verify End-to-End Flow

## Objective
Verify the PDF processing and workspace functionality is fully implemented and working. The core logic exists — this plan focuses on verification and any missing pieces.

## Files Modified
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (PDF picker integration)
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt` (file listing)
- `app/src/main/java/com/gemma/gpuchat/PdfToMarkdownConverter.kt` (conversion)

## must_haves
- PDF file picker accepts .pdf files
- PDF converts to markdown and is sent to model
- Workspace browser shows documents/ and markdown/ files

---

## Task 1: Review PDF Picker Integration in MainActivity

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (pdfPickerLauncher, lines 329-390)
</read_first>

<acceptance_criteria>
- `pdfPickerLauncher` is defined with `ActivityResultContracts.OpenDocument()`
- MIME type filter set to `"application/pdf"`
- On result: saves PDF via `WorkspaceManager.savePdf()` and converts via `PdfToMarkdownConverter`
- Resulting markdown is added to chat as context or sent to model
</acceptance_criteria>

<action>
Read the PDF picker section in MainActivity. Verify:
1. The picker is properly configured
2. On file selection, PDF is saved and converted
3. Conversion result is added to chat or model context

If any step is missing, add it.
</action>

---

## Task 2: Verify PdfToMarkdownConverter Works

<read_first>
- `app/src/main/java/com/gemma/gpuchat/PdfToMarkdownConverter.kt` (convert method)
</read_first>

<acceptance_criteria>
- `convert(pdfPath: String)` returns markdown String
- Handles multi-page PDFs
- Creates markdown with headings, lists, tables
- Closes document properly in finally block
</acceptance_criteria>

<action>
Review the PdfToMarkdownConverter. Test with a sample PDF if possible. If issues found, fix them.
</action>

---

## Task 3: Verify Workspace File Browsing

<read_first>
- `app/src/main/java/com/gemma/gpuchat/WorkspaceManager.kt` (listWorkspace, listMarkdown)
</read_first>

<acceptance_criteria>
- `listWorkspace(context)` returns formatted string with documents/ and markdown/ files
- `listMarkdown(context)` returns only markdown files
- Files are sorted by name
- Empty directories show "(vazio)"
</acceptance_criteria>

<action>
Review WorkspaceManager file listing. Verify:
1. Both `listWorkspace` and `listMarkdown` work correctly
2. File sizes are formatted human-readable
3. Empty directories handled gracefully

Test by creating a test markdown file and verifying it appears in list.
</action>

---

## Task 4: Verify Model Receives PDF Content

<read_first>
- `app/src/main/java/com/gemma/gpuchat/MainActivity.kt` (PDF result handling, around line 351)
</read_first>

<acceptance_criteria>
- After PDF conversion, content is sent to model as context
- Content appears in chat as user message or system context
- Model can reference the PDF content in its response
</acceptance_criteria>

<action>
Review the PDF result handling. Verify:
1. After conversion, markdown is appended to a user message
2. The message is sent to model via `LlmChatModelHelper.sendMessage()`
3. Model receives the PDF content in its context

If the flow is incomplete, complete it.
</action>

---

## Task 5: Build and Verify

<read_first>
- `app/build.gradle.kts`
</read_first>

<acceptance_criteria>
- `./gradlew assembleDebug` exits with code 0
- APK generated
- PDF picker works on device
</acceptance_criteria>

<action>
1. Run build: `.\gradlew.bat assembleDebug`
2. Deploy to device
3. Test PDF selection → conversion → model context flow
</action>

---

## Verification
1. File picker opens for PDF files
2. Selected PDF is saved to documents/ workspace
3. PDF converts to markdown without error
4. Markdown content appears in chat context
5. Model responds referencing PDF content
6. Workspace browser shows PDF and markdown files