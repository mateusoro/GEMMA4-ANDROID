# Roadmap: Gemma4Android

**Created:** 2026-05-02
**Updated:** 2026-05-04 for v1.1 (Thinking Bubble UI)

---
## Milestones

- ✅ **v1.0 MVP** — Phases 1-6 (shipped 2026-05-03) — 22/22 requirements satisfied, 76/76 tests passing
- 🚧 **v1.1** — Thinking Bubble UI — Phases 7-9 (in progress)

---

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-6) — SHIPPED 2026-05-03</summary>

- [x] Phase 1: Chat Core + Tool Integration (4/4 plans) — completed 2026-05-02
- [x] Phase 2: Audio Recording + Transcription Fix (1/1 plan) — completed 2026-05-02
- [x] Phase 3: PDF Processing + Workspace (1/1 plan) — completed 2026-05-02
- [x] Phase 4: Settings + System Prompt (1/1 plan) — completed 2026-05-02
- [x] Phase 5: Edge-to-Edge + Polish (1/1 plan) — completed 2026-05-02
- [x] Phase 6: Testing Standard (1/1 plan) — completed 2026-05-02

**Archive:** `.planning/milestones/v1.0-ROADMAP.md`
**Requirements:** `.planning/milestones/v1.0-REQUIREMENTS.md`

</details>

### 🚧 v1.1 — Thinking Bubble UI

**Goal:** Streaming thought display above bot messages with expand/collapse

---

## Phase Details

### Phase 7: Foundation (Data Model + Callback Wiring)

**Goal:** ChatMessage has thinkingText field; sendMessage() accepts onThinking callback.

**Depends on:** None (first phase of v1.1)

**Requirements:** THINK-01, THINK-02, THINK-03, THINK-04, THINK-05, THINK-06

**Success Criteria** (what must be TRUE):

1. `ChatMessage` data class has `thinkingText: String = ""` field (backward compatible)
2. `sendMessage()` accepts nullable `onThinking: ((String) -> Unit)?` parameter
3. Thinking tokens from `channels["thought"]` route through `onThinking` callback (not mixed into response text)
4. Background thread tokens delivered to callback without blocking UI thread
5. Existing callers to `sendMessage()` continue working without specifying `onThinking`

**Plans:** 1 plan

Plans:
- [ ] 07-01-PLAN.md — ChatMessage.thinkingText + sendMessage.onThinking callback wiring

---

### Phase 8: UI Implementation (ThinkingBubble + Integration)

**Goal:** ThinkingBubble composable renders above ChatBubble with streaming text and expand/collapse.

**Depends on:** Phase 7

**Requirements:** THINK-07, THINK-08, THINK-09

**Success Criteria** (what must be TRUE):

1. `ThinkingBubble` composable renders with muted styling (surfaceVariant background, smaller text, "thinking..." label)
2. `ThinkingBubble` appears above `ChatBubble` when `!isUser && thinkingText.isNotEmpty()`
3. Text streaming updates in real-time as tokens arrive via `onThinking` callback
4. Expand/collapse toggle works with 4-line clamp and `TextOverflow.Ellipsis`
5. `AnimatedVisibility` handles show/hide transitions

**Plans:** 1 plan

Plans:
- [x] 08-01-PLAN.md — ThinkingBubble composable + LazyColumn integration + onThinking callback wiring

**UI hint:** yes

---

### Phase 9: Polish (Animation + Edge Cases)

**Goal:** Smooth animations, correct threading, scroll stability, error handling.

**Depends on:** Phase 8

**Requirements:** THINK-10, THINK-11, THINK-12

**Success Criteria** (what must be TRUE):

1. Fade-out animation on `ThinkingBubble` when response starts arriving
2. `mainHandler.post {}` used for all UI updates from LiteRT-LM background threads
3. Scroll position stable when thinking bubble appears/disappears (no jump)
4. `onError` and `onDone` clear thinking state (no orphaned bubble on error)
5. Memory leak prevented — callbacks cleared in `DisposableEffect`

**Plans:** TBD

---

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Chat Core + Tools | v1.0 | 4/4 | Complete | 2026-05-02 |
| 2. Audio + Transcription | v1.0 | 1/1 | Complete | 2026-05-02 |
| 3. PDF + Workspace | v1.0 | 1/1 | Complete | 2026-05-02 |
| 4. Settings + System Prompt | v1.0 | 1/1 | Complete | 2026-05-02 |
| 5. Edge-to-Edge UI | v1.0 | 1/1 | Complete | 2026-05-02 |
| 6. Testing Standard | v1.0 | 1/1 | Complete | 2026-05-02 |
| 7. Foundation | v1.1 | 1/1 | Complete | 2026-05-04 |
| 8. UI Implementation | v1.1 | 1/1 | Complete | 2026-05-04 |
| 9. Polish | v1.1 | 0/TBD | Not started | - |

---

## Summary

| # | Phase | Goal | Requirements | Success Criteria |
|---|-------|------|--------------|------------------|
| 1 | Chat Core + Tools | Chat with tool calling | 10 reqs | 10 criteria |
| 2 | Audio + Transcription | Stable audio recording | 4 reqs | 5 criteria |
| 3 | PDF + Workspace | PDF processing + files | 3 reqs | 5 criteria |
| 4 | Settings + System Prompt | Persist user preferences | 3 reqs | 7 criteria |
| 5 | Edge-to-Edge UI | Modern Android insets | 1 req | 4 criteria |
| 6 | Testing Standard | TestHarnessActivity standard | 1 req | 5 criteria |
| 7 | Foundation | Data model + callback wiring | 6 reqs | 5 criteria |
| 8 | UI Implementation | ThinkingBubble + integration | 3 reqs | 5 criteria |
| 9 | Polish | Animation + edge cases | 3 reqs | 5 criteria |

**All v1 requirements covered:** 22/22 v1.0 + 12/12 v1.1 = 34/34 total ✓

---

*Roadmap created: 2026-05-02*
*Last updated: 2026-05-04 for v1.1 milestone*
