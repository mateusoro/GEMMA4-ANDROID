# Milestones: Gemma4Android

## v1.0 MVP
**Shipped:** 2026-05-03
**Phases:** 1-6 (12 plans, 22 requirements)
**Status:** ✅ Complete

### Accomplishments
1. Fully functional chat with tool calling — 5 tools (location, calendar, file workspace, device info)
2. Audio recording fixed (WAV with `fact` chunk 56 bytes, no MA_INVALID_FILE crash)
3. PDF processing via notification-based PDFBox approach
4. Settings with sliders (maxTokens/temp/topK/topP) + system prompt persistence via DataStore
5. Edge-to-edge UI with proper insets and IME padding
6. TestHarnessActivity formalized as project standard (76/76 tests passing)

### Key Technical Decisions
- `jvmToolchain(17)` + multiDex for Nubia Flip 5G ART compatibility
- Gemma-4-E2B-IT ignores `ConversationConfig.systemInstruction` → prepend in `sendMessage()`
- `LlmChatModelHelper.reload()` (not `updateParams()`) for settings changes
- TestHarnessActivity in `app/src/main/` due to AGP JDK classpath issue

### Tech Debt
- REQUIREMENTS.md traceability never updated (process gap)
- No VERIFICATION.md files created during phases (GSD gates skipped)

---

*Milestones created: 2026-05-03*