# External Integrations

**Created:** 2026-05-02
**Focus:** tech

## LiteRT-LM Service

| Aspect | Detail |
|--------|--------|
| **Provider** | Google AI Edge (on-device) |
| **Model** | Gemma-4-E2B-IT |
| **Size** | ~2.58 GB |
| **Location** | `/data/local/tmp/gemma-4-E2B-it.litertlm` |
| **Backend** | GPU (OpenCL) — NOT CPU |
| **Features** | Automatic tool calling, conversation history, thinking mode |

## Android System Services

| Service | Usage |
|---------|-------|
| **ActivityManager** | Memory info detection |
| **PackageManager** | Permission checking |
| **MediaRecorder** | Audio recording for transcription |
| **CalendarContract** | Tool: create calendar events |
| **Intent.ACTION_VIEW (geo:)** | Tool: show location on map |

## File Storage

| Directory | Purpose |
|-----------|---------|
| **Workspace (internal)** | Markdown documents, PDF files |
| **DataStore Preferences** | User settings (LLM params, theme, system prompt) |
| **Cache** | Temporary files |

## Document Processing

| Library | Purpose |
|---------|---------|
| **PDFBox Android 2.0.27.0** | PDF to Markdown conversion |
| **Built-in file I/O** | Workspace file read/write |

## No External API Integrations

This app does NOT call external APIs. All AI inference is on-device using LiteRT-LM. Network permission is for future use only.

---

*Document: INTEGRATIONS.md — Part of .planning/codebase/*