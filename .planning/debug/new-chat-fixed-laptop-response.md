---
slug: new-chat-fixed-laptop-response
status: resolved
trigger: "quando inicio uma conversa pelo novo chat ele nao esta funcionando como quando o app inicia na primeira tela. ele esta respondendo fixo laptop. deve ter alguma trava de teste esquecida la"
created: 2026-05-03
updated: 2026-05-03
root_cause: "'Nova conversa' cleared messages and switched conversation ID but never called LlmChatModelHelper.resetConversation(). The stale Conversation object (with accumulated context from the auto 'oi' test at startup) continued to be reused. Gemma-4-E2B-IT has no reset API — only conversation.close() + engine.createConversation() gives a clean slate."
fix: "Added LlmChatModelHelper.resetConversation() in the 'Nova conversa' click handler after ChatHistoryManager.createNewConversation()"
verification: "74/74 tests pass, APK installed and pushed"
files_changed:
  - app/src/main/java/com/gemma/gpuchat/MainActivity.kt
---

## Current Focus

**Hypothesis:** Unknown — investigating
**Next action:** gather initial evidence (search for hardcoded test responses in new chat implementation)
**Test:**
**Expecting:**

---

## Symptoms

1. **Expected behavior:** New chat screen should behave identically to the first screen when app starts — normal model response
2. **Actual behavior:** New chat responds with "fixo laptop" / "fixed laptop" text — appears to be a hardcoded debug/test response
3. **Error messages:** None — just a fixed incorrect response
4. **Timeline:** Started recently — new chat screen was likely modified
5. **Reproduction:** Start new chat, send any message → receives "fixed laptop" response

---

## Evidence

(no entries yet)

---

## Eliminated

(no entries yet)