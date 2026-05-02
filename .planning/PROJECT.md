# Gemma4Android — Project

**Type:** Android Mobile Application (On-Device LLM Inference)
**Created:** 2026-05-02
**Last updated:** 2026-05-02 after initialization

---

## What This Is

An Android chat app that runs **Gemma-4-E2B-IT** entirely on-device using LiteRT-LM GPU inference. Users can chat, use tools (calendar, maps, file workspace), transcribe audio, and convert PDFs to markdown. No cloud, no data leaves the device.

## Core Value

**On-device AI assistant** — fully offline, private, fast GPU-accelerated inference with tool-calling capability.

---

## Requirements

### Validated

- ✓ GPU-accelerated LLM inference via LiteRT-LM — existing
- ✓ Chat UI with message history — existing
- ✓ Audio recording + transcription — existing (WAV with `fact` chunk)
- ✓ PDF to Markdown conversion — existing
- ✓ File workspace (markdown/documents) — existing
- ✓ Tool calling (location, calendar, file read/save, device info) — existing
- ✓ Drawer navigation with settings — existing

### Active

- [ ] **Thinking mode display** — Show model's internal reasoning before response
- [ ] **Voice output** — TTS reading of model responses
- [ ] **Image attachment** — Send images as input to model
- [ ] **Improved audio transcription** — Fix WAV crash (error -10 MA_INVALID_FILE)
- [ ] **System prompt editing** — Persist user-customized system instruction
- [ ] **Edge-to-edge UI** — Proper inset handling for modern Android

### Out of Scope

- Cloud/API integration — all inference is on-device
- Multi-user/sync — single device, single user
- Notifications/push — not needed for on-device app

---

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| GPU-only model | Gemma-4-E2B-IT requires OpenCL GPU | App only works on GPU-capable devices |
| Singleton helpers | No DI framework needed for this scale | `LlmChatModelHelper`, `AgentTools` are objects |
| Single Activity | Simple app, drawer handles all navigation | No Navigation component needed |
| Prepend system prompt | `ConversationConfig.systemInstruction` ignored by Gemma | Workaround in `sendMessage()` |

---

## Context

**Stack:** Kotlin 2.3, Jetpack Compose BOM 2024.12, Material3, LiteRT-LM Android, PDFBox

**Entry point:** `MainActivity.kt` (~1618 lines) — full Compose UI with drawer, chat, mic, file picker

**Key helpers:**
- `LlmChatModelHelper` — LiteRT engine lifecycle, conversation, tool registration
- `AgentTools` — 5 tool implementations (context stored as instance field)
- `AudioTranscriber` — Mic recording + WAV `fact` chunk generation
- `WorkspaceManager` — File operations for markdown/documents

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

---

*Last updated: 2026-05-02 after initialization*