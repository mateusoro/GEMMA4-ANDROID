# Requirements: Gemma4Android

**Defined:** 2026-05-02
**Core Value:** On-device AI assistant — fully offline, private, fast GPU-accelerated inference with tool-calling capability.

## v1 Requirements

### Chat & Messaging

- [ ] **CHAT-01**: User can send text messages and receive model responses
- [ ] **CHAT-02**: Message history persists across app restarts
- [ ] **CHAT-03**: User can delete individual messages or clear history
- [ ] **CHAT-04**: Thinking mode internal reasoning displays in separate bubble before response

### Audio & Transcription

- [ ] **AUDIO-01**: User can record audio via microphone button
- [ ] **AUDIO-02**: Audio recordings transcribe to text for model input
- [ ] **AUDIO-03**: Audio input uses WAV container with `fact` chunk (56 bytes) — no crash (error -10)
- [ ] **AUDIO-04**: User can stop recording mid-session

### Tool Calling

- [ ] **TOOL-01**: User can show locations on map via tool call
- [ ] **TOOL-02**: User can create calendar events via tool call
- [ ] **TOOL-03**: User can list and read workspace files (markdown/documents)
- [ ] **TOOL-04**: User can save markdown files to workspace
- [ ] **TOOL-05**: User can view device info (time, memory, model size)

### File & Document

- [ ] **FILE-01**: User can attach PDF files for model to read
- [ ] **FILE-02**: PDF content converts to markdown for model context
- [ ] **FILE-03**: User can browse workspace markdown files

### Settings & Preferences

- [ ] **SETP-01**: User can adjust LLM parameters (max tokens, temperature, topK, topP)
- [ ] **SETP-02**: User can edit and persist system instruction prompt
- [ ] **SETP-03**: Settings persist via DataStore across app restarts

### UI/UX

- [ ] **UIUX-01**: Drawer navigation with chat, workspace, settings sections
- [ ] **UIUX-02**: Edge-to-edge display — proper inset handling for modern Android
- [ ] **UIUX-03**: App builds and runs without crash on GPU-capable devices

## v1.1 Requirements (Thinking Bubble UI)

### UI Components

- [ ] **THINK-01**: ThinkingBubble composable com streaming de texto em tempo real
- [ ] **THINK-02**: Max 4 linhas visíveis com `TextOverflow.Ellipsis`
- [ ] **THINK-03**: Toggle expand/collapse com `AnimatedVisibility`
- [ ] **THINK-04**: Estilo muted (surfaceVariant, texto menor, label "thinking...")

### Data Model

- [ ] **THINK-05**: Campo `thinkingText: String = ""` em ChatMessage
- [ ] **THINK-06**: Callback `onThinking: ((String) -> Unit)?` em sendMessage()

### Integration

- [ ] **THINK-07**: Routing de `channels["thought"]` para UI via callback
- [ ] **THINK-08**: LazyColumn rendering — ThinkingBubble acima da ChatBubble
- [ ] **THINK-09**: Fade-out animado quando resposta inicia

### Polish

- [ ] **THINK-10**: Threading correto com `mainHandler.post{}`
- [ ] **THINK-11**: Scroll anchoring para evitar jumps
- [ ] **THINK-12**: Limpeza de estado em onError/onDone

### Out of Scope

- Pensamento persistente entre mensagens (apaga após resposta)
- Configurable max lines via settings
- Accessibility (TalkBack/LiveRegion)
- Histórico de thinking por mensagem

## v2 Requirements

### Voice Output

- **VOICE-01**: Model responses read aloud via TTS
- **VOICE-02**: User can toggle TTS on/off

### Image Input

- **IMG-01**: User can attach images to chat messages
- **IMG-02**: Model processes images as input context

### Advanced Features

- **ADVN-01**: Model supports configurable context window size
- **ADVN-02**: Conversation export to file
- **ADVN-03**: Multiple conversation threads

## Out of Scope

| Feature | Reason |
|---------|--------|
| Cloud/API integration | All inference is on-device — no network calls |
| Multi-user/sync | Single device, single user, local storage |
| Push notifications | Not needed for on-device assistant |
| Video input | Not in scope for v1 |
| OAuth login | No auth needed — private on-device app |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| THINK-01 | Phase 7 | Pending |
| THINK-02 | Phase 7 | Pending |
| THINK-03 | Phase 7 | Pending |
| THINK-04 | Phase 7 | Pending |
| THINK-05 | Phase 7 | Pending |
| THINK-06 | Phase 7 | Pending |
| THINK-07 | Phase 8 | Pending |
| THINK-08 | Phase 8 | Pending |
| THINK-09 | Phase 8 | Pending |
| THINK-10 | Phase 9 | Pending |
| THINK-11 | Phase 9 | Pending |
| THINK-12 | Phase 9 | Pending |
| CHAT-01 | Phase 1 | Pending |
| CHAT-02 | Phase 1 | Pending |
| CHAT-03 | Phase 1 | Pending |
| CHAT-04 | Phase 1 | Pending |
| AUDIO-01 | Phase 2 | Pending |
| AUDIO-02 | Phase 2 | Pending |
| AUDIO-03 | Phase 2 | Pending |
| AUDIO-04 | Phase 2 | Pending |
| TOOL-01 | Phase 1 | Pending |
| TOOL-02 | Phase 1 | Pending |
| TOOL-03 | Phase 1 | Pending |
| TOOL-04 | Phase 1 | Pending |
| TOOL-05 | Phase 1 | Pending |
| FILE-01 | Phase 3 | Pending |
| FILE-02 | Phase 3 | Pending |
| FILE-03 | Phase 3 | Pending |
| SETP-01 | Phase 4 | Pending |
| SETP-02 | Phase 4 | Pending |
| SETP-03 | Phase 4 | Pending |
| UIUX-01 | Phase 1 | Pending |
| UIUX-02 | Phase 5 | Pending |
| UIUX-03 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 22 total — mapped to phases 1-6
- v1.1 requirements: 12 total — mapped to phases 7-9
- Total: 34/34 requirements mapped ✓

---
*Requirements defined: 2026-05-02*
*Last updated: 2026-05-04 for v1.1 milestone*