# Gemma4Android — Project

**Type:** Android Mobile Application (On-Device LLM Inference)
**Created:** 2026-05-02
**Last updated:** 2026-05-03 after v1.0 MVP shipped

---

## Current State — v1.0 MVP Shipped ✅

**Version:** v1.0 MVP (shipped 2026-05-03)
**Device:** Nubia Flip 5G (Android 15, SDK 35)
**Model:** Gemma-4-E2B-IT @ /data/local/tmp/gemma-4-E2B-it.litertlm (2.58GB)
**Tests:** 76/76 passing via TestHarnessActivity
**Commits:** 18 since 2026-05-01

---

## What This Is

An Android chat app that runs **Gemma-4-E2B-IT** entirely on-device using LiteRT-LM GPU inference. Users can chat, use tools (calendar, maps, file workspace), transcribe audio, and convert PDFs to markdown. No cloud, no data leaves the device.

---

## Core Value

**On-device AI assistant** — fully offline, private, fast GPU-accelerated inference with tool-calling capability.

---

## Requirements

### Validated (v1.0 MVP)

- ✓ GPU-accelerated LLM inference via LiteRT-LM — v1.0
- ✓ Chat UI with message history — v1.0
- ✓ Audio recording + transcription (WAV with `fact` chunk 56 bytes) — v1.0
- ✓ PDF to Markdown conversion — v1.0
- ✓ File workspace (markdown/documents) — v1.0
- ✓ Tool calling (location, calendar, file read/save, device info) — v1.0
- ✓ Drawer navigation with settings — v1.0
- ✓ Thinking mode display (channel "thought") — v1.0
- ✓ Edge-to-edge UI with proper insets — v1.0
- ✓ TestHarnessActivity as project standard (76/76 tests) — v1.0

### Active (v1.1 — TBD)

Requirements for next milestone not yet defined. Start with `/gsd-new-milestone`.

### Out of Scope

- Cloud/API integration — all inference is on-device
- Multi-user/sync — single device, single user
- Notifications/push — not needed for on-device app
- Video input — not in scope for v1
- OAuth login — no auth needed — private on-device app

---

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| GPU-only model | Gemma-4-E2B-IT requires OpenCL GPU | App only works on GPU-capable devices |
| Singleton helpers | No DI framework needed for this scale | `LlmChatModelHelper`, `AgentTools` are objects |
| Single Activity | Simple app, drawer handles all navigation | No Navigation component needed |
| Prepend system prompt | `ConversationConfig.systemInstruction` ignored by Gemma | Workaround in `sendMessage()` |
| jvmToolchain(17) + multiDex | Nubia Flip 5G ART required Java 17, MainActivity in primary DEX | Fixed ClassNotFoundException |
| TestHarnessActivity in app/src/main/ | AGP JDK 24/21 classpath mismatch | Bypassed, 76/76 tests in-process |
| reload() not updateParams() | Settings changes must recreate engine | Fixed settings not applied on startup |

---

## Context

**Stack:** Kotlin, Jetpack Compose, Material3, LiteRT-LM Android, PDFBox

**Entry point:** `MainActivity.kt` (~1600 lines) — full Compose UI with drawer, chat, mic, file picker

**Key helpers:**
- `LlmChatModelHelper` — LiteRT engine lifecycle, conversation, tool registration
- `AgentTools` — 5 tool implementations (context stored as instance field via `AgentTools.create(context)`)
- `AudioTranscriber` — Mic recording + WAV `fact` chunk generation
- `WorkspaceManager` — File operations for markdown/documents
- `LlmPreferences` — DataStore-backed settings persistence

**Device:** Nubia Flip 5G (Android 15, SDK 35) — GPU backend (OpenCL), 8GB RAM

**Defaults:** temperature=1.0, topK=64, topP=0.95 (Gemma-4-E2B-IT recommended)

---

## Next Milestone Goals

**v1.1** — Requirements TBD. Start with `/gsd-new-milestone` to define next set of features.

---

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state
5. Archive completed phases to milestones/

---

*Last updated: 2026-05-03 after v1.0 MVP shipped*