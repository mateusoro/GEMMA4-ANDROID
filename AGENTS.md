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

## Learned Workspace Facts

- Android app with LiteRT-LM GPU backend (Gemma-4-E2B-IT model, 2.46GB at /data/local/tmp/gemma-4-E2B-it.litertlm)
- Device: ADB at 192.168.0.17:39209 (Nubia/ZTE RedMagic gaming phone)
- Compose UI with Kotlin; async callbacks from LiteRT-LM must not capture mutable `var` refs in closures — use `indexOfLast { !it.isUser }` to find bot message index dynamically
- App-level log file: `gemma_startup.nlog` in app filesDir (use "Show Logs" button or `run-as com.gemma.gpuchat cat files/gemma_startup.nlog`)
- Screen crashes in Compose may not appear in logcat — always cross-check with app nlog file
- Use `safe_run_detached` for long-running operations (ADB push of 2.5GB files takes ~4-5 min over WiFi)
- Pre-built LiteRT-LM models for Android: check `litert-community` namespace on HuggingFace before attempting custom conversion
- Use helper function (`sendAutoMessage`) for chaining auto-messages in Compose to avoid deeply nested callback pyramids
