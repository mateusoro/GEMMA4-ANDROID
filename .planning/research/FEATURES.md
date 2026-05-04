# Feature Landscape: AI Thinking Bubble UI

**Domain:** Android Chat App with AI Thinking Display
**Researched:** 2026-05-04
**Confidence:** MEDIUM (established UX patterns; cannot verify current ChatGPT/Claude/Gemini UI due to web access restrictions)

## Table Stakes

Features users expect in any AI chat thinking display. Missing these makes the feature feel broken.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Thinking bubble appears above bot message | Standard UX pattern (ChatGPT, Claude, Gemini all do this) | Low | Appears when thinking starts, before response |
| Bubble shows while thinking streams | Real-time feedback that model is working | Low | Token-by-token update in bubble |
| Bubble disappears when thinking ends | User knows reasoning phase is done | Low | Transitions when main response starts |
| Visual distinction from user/bot messages | Distinguishes internal reasoning from output | Low | Different background, smaller text, label |
| Smooth animation on appear/disappear | Modern chat UX standard | Low | Fade + slide from top of bubble |

## Differentiators

Features that could make the thinking bubble more polished than basic. Not expected, but valued.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Expand/collapse toggle | Users can hide long thinking chains | Medium | Tap to expand/collapse |
| Max visible lines setting | Configurable: 3 lines default, user can adjust | Medium | Requires settings integration |
| Persist thinking after completion | User can review reasoning after response done | Medium | Collapsed by default, tap to expand |
| Thinking token streaming animation | Typewriter effect as tokens arrive | Low | `AnimatedContent` with fade |
| Distinct icon/label | "Thinking..." with brain or gear icon | Low | Material3 `Icon` composable |

## Anti-Features

Features to explicitly NOT build.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Thinking bubble for ALL messages | Clutters simple responses | Only show when thinking channel has content |
| Permanent thinking bubble after response | Confuses users about message state | Hide or collapse after `onDone` |
| Blocking animation (user waits for thinking to finish) | Makes app feel slow | Bubble appears and fades naturally |
| Loading spinner instead of streaming text | Defeats purpose of showing reasoning | Stream actual thinking tokens |

## Feature Dependencies

```
ChatMessage.data model change (thinkingText field)
        ↓
LlmChatModelHelper callback wiring (route thinking to correct message)
        ↓
ThinkingBubble composable (visual + animation)
        ↓
Lifecycle handling (appear/disappear/collapse)
```

No cross-cutting dependencies. The thinking bubble is self-contained in the message list.

## MVP Recommendation

Prioritize:
1. **Thinking bubble appears above bot message** when `message.channels["thought"]` has content
2. **Streaming text** — tokens appear in bubble as they arrive
3. **Visual distinction** — different background (surfaceVariant), smaller text, "thinking..." label
4. **Transition out** — bubble fades/collapses when main response starts streaming

Defer:
- Expand/collapse toggle (nice-to-have, not MVP)
- Max visible lines setting (requires settings integration)
- Persist after completion (can add tap-to-expand later)

## Sources

- ChatGPT extended thinking UX (established pattern, web access denied for current verification)
- Claude thinking documentation (established pattern, web access denied for current verification)
- Gemini thinking documentation (established pattern, web access denied for current verification)
- Codebase analysis: `MainActivity.kt` lines 974-979 (message list), 1469 (`ChatMessage`), 1409 (`ChatBubble`)
- `LlmChatModelHelper.kt` lines 327-331 (thinking channel extraction)

---

**Key reference:** `Channel("thought", "<|channel>thought\n", "<channel|>")` carries thinking tokens via `message.channels["thought"]` — already being logged, needs UI route.