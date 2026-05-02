# Phase 6: Testing Standard — SPEC

**Phase:** 06-testing-standard
**Created:** 2026-05-02
**Status:** Ready for planning

---

## 1. Problem Statement

The AGP 8.7.0 test runner (`testDebugUnitTest`) fails for this project due to a JDK 24/21 toolchain mismatch — classes compiled by Kotlin on JDK 24 cannot be loaded by the forked test worker JVM running on JDK 21. All standard Gradle test approaches (`useJUnitPlatform()`, JUnit vintage engine, JVM Test Suite plugin) fail to resolve this.

Additionally, the standard `connectedDebugAndroidTest` instrumented test pipeline has not been reliably exercised.

**A reliable workaround exists:** the **TestHarnessActivity** in-app test runner. Tests run inside the Android app process (where the classpath is correct), triggered via ADB, with results written to `files/test_results/test_results.txt`.

---

## 2. Solution: TestHarnessActivity

### Architecture

- **Location:** `app/src/main/java/com/gemma/gpuchat/TestHarnessActivity.kt`
- **Type:** Android Activity (exported in `AndroidManifest.xml`)
- **Test suites:** WavUtils, WorkspaceManager, PdfToMarkdown — 58 tests total, all passing
- **Trigger:** `adb shell am start -n com.gemma.gpuchat/.TestHarnessActivity`
- **Results:** `adb shell "run-as com.gemma.gpuchat cat files/test_results/test_results.txt"`

### Why not `src/test/`?

The `src/test/` directory contains valid JUnit 4 test classes with proper annotations (`@Test`, `assertTrue`, `assertEquals`, etc.). These represent the canonical test logic. However, they **cannot be executed via `testDebugUnitTest`** due to the JDK toolchain classpath issue. The TestHarnessActivity re-implements the same test logic as inline functions so it can run inside the app process.

### Why not instrumented tests (`src/androidTest/`)?

Instrumented tests would run on the device via `connectedDebugAndroidTest`. However:
- They require a device/emulator with the app installed
- The test APK needs to be built separately
- For pure logic tests (WavUtils, WorkspaceManager, PdfToMarkdown conversion), an in-process harness is faster and more hermetic

The instrumented test stubs (`WavUtilsInstrumentedTest.kt`, etc.) exist as placeholders but are not the primary test vehicle.

---

## 3. Test Format Standard

Every future phase must add tests to `TestHarnessActivity.kt` following this pattern:

```kotlin
// [Feature] tests
run {
    // helper functions as local vals
    fun myFunctionLogic(input: String): String { ... }

    // individual assertions
    assertTrue("description", myFunctionLogic("input") == "expected")
    assertFalse("description", myFunctionLogic("input").contains("bad"))
}
```

**Rules:**
1. Tests go in a `run { ... }` block with a descriptive comment
2. Helper logic is duplicated as a local `fun` or `val` inside the block (not extracted to top level — keeps tests self-contained)
3. Use `assertTrue`, `assertFalse`, `assertEquals` — same as JUnit
4. Results written via `writeResultsToFile()` which appends ✅ or ❌ per assertion
5. All 58 current tests must continue to pass

---

## 4. Test Coverage Requirements

| Suite | File | Tests | Status |
|-------|------|-------|--------|
| WavUtils | TestHarnessActivity.kt | 19 | ✅ Passing |
| WorkspaceManager | TestHarnessActivity.kt | 20 | ✅ Passing |
| PdfToMarkdownConverter | TestHarnessActivity.kt | 19 | ✅ Passing |
| **Total** | | **58** | **✅ All passing** |

---

## 5. Root Cause: AGP ClassPath Issue

```
AGP 8.7.0 → jvmToolchain(21) → forks test worker on JDK 21
Kotlin compiler → compiles on JDK 24
Result: BuiltinClassLoader.loadClass() in JDK 21 cannot resolve classes compiled by JDK 24
```

**Attempted fixes that did NOT work:**
- `useJUnitPlatform()` with JUnit 5
- JUnit vintage engine (junit-vintage)
- JVM Test Suite plugin
- Manual classpath manipulation with `gradle.taskGraph.whenReady`
- Adding `--add-opens` flags to `gradle.properties`

**`--add-opens` in gradle.properties applies to the Gradle daemon JVM, NOT the forked test worker JVM.**

---

## 6. Future Phases

When a new feature is added (e.g., Phase 4 Settings screen), a corresponding `runSettingsTests()` block must be added to TestHarnessActivity verifying:
- Settings values persist to DataStore
- Sliders update values correctly
- System prompt changes trigger `LlmChatModelHelper.reload()`

---

## 7. Acceptance Criteria

- [ ] TestHarnessActivity is the documented standard for logic/unit tests
- [ ] Every future phase adds test coverage to TestHarnessActivity
- [ ] 58/58 tests pass and remain passing
- [ ] Test execution documented in CLAUDE.md project instructions
- [ ] No attempt to use `testDebugUnitTest` (known broken)
