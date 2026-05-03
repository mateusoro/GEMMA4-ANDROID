---
slug: llm-settings-not-loaded-on-startup
status: resolved
trigger: "quando inicio o app esse primeiro load nao vem com as configuracoes do system prompt e outros parametros da llm. somente depois que clica no reload llm da tela de config ele aplica. precisa mudar para no inicio do app ele carregar o que ja tem nas configs e nao os defaults"
created: 2026-05-03
updated: 2026-05-03
root_cause: "onSettingsChange() chamava LlmChatModelHelper.updateParams() que apenas atualizava currentParams in-memory, mas NÃO recriava a Conversation/engine. A Conversation já havia sido criada com LlmParams defaults no startup — entao mudar currentParams não tinha efeito."
fix: "Alterado onSettingsChange() para chamar llmHelper.reload(params, sysInstr) ao invés de updateParams(). reload() chama release() (fecha engine + conversation) e entao re-inicializa com as novas settings — mesmo padrao do botao 'Reload LLM'."
verification: "Build successful (26s), APK installed, commit ba57a3e pushado. Verificacao runtime pendente no dispositivo."
files_changed:
  - app/src/main/java/com/gemma/gpuchat/MainActivity.kt
---

## Current Focus

**Hypothesis:** TBD
**Next action:** DONE
**Test:**
**Expecting:**

---

## Evidence

(none yet)

---

## Eliminated

(none yet)
