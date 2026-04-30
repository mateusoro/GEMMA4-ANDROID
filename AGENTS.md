# Agent Instructions

This project uses **bd** (beads) for issue tracking. Run `bd prime` for full workflow context.

## Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work atomically
bd close <id>         # Complete work
bd dolt push          # Push beads data to remote
```

## Non-Interactive Shell Commands

**ALWAYS use non-interactive flags** with file operations to avoid hanging on confirmation prompts.

Shell commands like `cp`, `mv`, and `rm` may be aliased to include `-i` (interactive) mode on some systems, causing the agent to hang indefinitely waiting for y/n input.

**Use these forms instead:**
```bash
# Force overwrite without prompting
cp -f source dest           # NOT: cp source dest
mv -f source dest           # NOT: mv source dest
rm -f file                  # NOT: rm file

# For recursive operations
rm -rf directory            # NOT: rm -r directory
cp -rf source dest          # NOT: cp -r source dest
```

**Other commands that may prompt:**
- `scp` - use `-o BatchMode=yes` for non-interactive
- `ssh` - use `-o BatchMode=yes` to fail instead of prompting
- `apt-get` - use `-y` flag
- `brew` - use `HOMEBREW_NO_AUTO_UPDATE=1` env var

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:ca08a54f -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

## Session Completion

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **PUSH TO REMOTE** - This is MANDATORY:
   ```bash
   git pull --rebase
   bd dolt push
   git push
   git status  # MUST show "up to date with origin"
   ```
5. **Clean up** - Clear stashes, prune remote branches
6. **Verify** - All changes committed AND pushed
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- Work is NOT complete until `git push` succeeds
- NEVER stop before pushing - that leaves work stranded locally
- NEVER say "ready to push when you are" - YOU must push
- If push fails, resolve and retry until it succeeds
<!-- END BEADS INTEGRATION -->

## Learned User Preferences

- **Verify BEFORE closing issues** — user explicitly called out closing without device verification as unacceptable; always confirm with device logs before closing
- User speaks Portuguese
- User uses `adb shell setprop log.tag <TAG> <LEVEL>` to configure logging (e.g., VERBOSE, INFO, ERROR)
- Device ADB WiFi disconnects when phone sleeps — may need `adb connect` after device wakes
- User requires beads issue for every bug — always create and claim before investigating
- User demands thorough logcat inspection for errors — never skip logcat check when debugging
- On Nubia/Android 15, logcat has a global silent filter `[log.tag]: [S]` that suppresses app tags even when per-tag props are set; use `adb logcat -v time --pid=<PID>` with `--pid` to bypass the global filter entirely

## Learned Workspace Facts

- Android app with LiteRT-LM GPU backend (Gemma-4-E2B-IT model, 2.46GB at /data/local/tmp/gemma-4-E2B-it.litertlm)
- Device: ADB at 192.168.0.17 but port changes frequently (39209, 45387, 42881) — always reconnect after sleep
- Compose UI with Kotlin; async callbacks from LiteRT-LM must not capture mutable `var` refs in closures — use `indexOfLast { !it.isUser }` to find bot message index dynamically
- Callbacks from LiteRT-LM may come from background threads — use `mainHandler.post {}` to post UI updates safely in Compose
- App-level log file: `gemma_startup.nlog` in app filesDir (use "Show Logs" button or `run-as com.gemma.gpuchat cat files/gemma_startup.nlog`)
- Screen crashes in Compose may not appear in logcat — always cross-check with app nlog file
- On Nubia/Android 15, app crashes may be written to Android `dropbox` instead of visible `logcat`; see `ADB_QUICKREF.md` section "Extrair crash via Dropbox" and use `adb shell dumpsys dropbox --print data_app_crash`
- Compose LazyColumn with `items(messages, key = { it.id })` crashes with `IllegalArgumentException: Key "" was already used` when manual user messages use empty string id; always let ChatMessage use default UUID — never assign `id = ""` or empty string to message keys
- Freeze/hang without FATAL in logcat is usually a Compose thread-safety issue, not a real crash — check nlog for token completion
- Use `safe_run_detached` for long-running operations (ADB push of 2.5GB files takes ~4-5 min over WiFi)
- Pre-built LiteRT-LM models for Android: check `litert-community` namespace on HuggingFace before attempting custom conversion
- Use helper function (`sendAutoMessage`) for chaining auto-messages in Compose to avoid deeply nested callback pyramids
- Native heap can reach 300MB+ with Gemma-4 model loaded — memory intensive but stable
- Gemma-4-E2B-IT `.litertlm` already uses mixed 2/4/8-bit per-layer quantization — no separate Q2_K download exists; file size 2.58 GB, with 0.79 GB decoder weights always in memory and 1.12 GB embeddings memory-mapped (fraction used per inference)
- Android GPU working memory: ~676 MB on S26 Ultra (Snapdragon 8 Elite); Android CPU working memory: ~1,733 MB via XNNPack; GPU VRAM allocation for weights estimated ~2.46 GB
- Context window scales KV cache memory dynamically; Gemma-4 E2B supports up to 32K tokens via Shared KV Cache architecture
- For memory investigation on Nubia: `adb shell dumpsys meminfo <pid>` after model load shows actual PSS/RSS split between GPU and CPU memory
