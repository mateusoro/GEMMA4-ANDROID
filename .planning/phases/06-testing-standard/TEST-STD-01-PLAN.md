---
phase: 6
plan_id: TEST-STD-01
wave: 1
depends_on: []
autonomous: false
files_modified:
  - CLAUDE.md
  - .planning/ROADMAP.md
  - .planning/STATE.md
---

## Plan: Testing Standard — Document TestHarnessActivity as Project Standard

### Goal

Formalize the TestHarnessActivity in-app testing approach as the mandatory testing standard for all future GSD phases in this project.

---

## Task: Document test standard in CLAUDE.md

**Details:**
Update the project's `CLAUDE.md` to include the testing approach as a permanent convention.

**read_first:**
- `CLAUDE.md` — current project instructions
- `app/src/main/java/com/gemma/gpuchat/TestHarnessActivity.kt` — the test harness implementation
- `.planning/phases/06-testing-standard/06-SPEC.md` — this phase's spec

**action:**
Add a `## Testing Standard` section to `CLAUDE.md` after the existing conventions, with this content:

```markdown
## Testing Standard

**AGP test runner (`testDebugUnitTest`) is broken** — JDK 24/21 toolchain classpath mismatch.
**Use TestHarnessActivity instead.**

### Running tests
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity
adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"
```

### Adding tests for a new feature
Add a `runXxxTests()` block to `TestHarnessActivity.kt`:

```kotlin
// [Feature] tests
run {
    fun myFunctionLogic(input: String): String { /* copy of logic */ }
    assertTrue("description", myFunctionLogic("input") == "expected")
}
```

### Why not src/test/?
- `src/test/` contains canonical JUnit 4 test classes (correct annotations, correct logic)
- But `testDebugUnitTest` always fails — classpath issue between JDK 24 and JDK 21
- TestHarnessActivity duplicates test logic as inline functions inside `run {}` blocks
- Both should be kept in sync — `src/test/` is the source of truth for test logic

### Why not instrumented tests (src/androidTest/)?
- Instrumented tests require separate test APK build + `connectedDebugAndroidTest`
- In-process harness is faster for pure logic tests
- Stubs exist at `app/src/androidTest/` but are not the primary vehicle

### Current coverage (58/58 passing)
| Suite | Tests |
|-------|-------|
| WavUtils | 19 |
| WorkspaceManager | 20 |
| PdfToMarkdownConverter | 19 |
```

**acceptance_criteria:**
- `CLAUDE.md` contains a `## Testing Standard` section
- Section documents the 3-step test execution command
- Section explains why `testDebugUnitTest` is broken
- Section documents how to add new tests
- Section lists current 58/58 passing tests
