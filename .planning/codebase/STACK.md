# Technology Stack

**Created:** 2026-05-02
**Focus:** tech

## Languages & Runtime

| Category | Value |
|----------|-------|
| **Primary Language** | Kotlin 2.3.0 |
| **Min SDK** | 29 (Android 10) |
| **Target SDK** | 35 (Android 15) |
| **JVM Target** | Java 17 |
| **JVM Toolchain** | Kotlin 21 → Java 17 (jvmToolchain(17)) |

## Framework & UI

| Component | Version |
|-----------|---------|
| **Jetpack Compose BOM** | 2024.12.01 |
| **Compose UI** | from BOM |
| **Compose Material3** | from BOM |
| **Material Icons Extended** | from BOM |
| **Activity Compose** | 1.9.3 |
| **Lifecycle Runtime Compose** | 2.8.7 |

## AI/ML

| Component | Purpose |
|-----------|---------|
| **LiteRT-LM Android** (`com.google.ai.edge.litertlm:litertlm-android`) | GPU-accelerated LLM inference using Gemma-4-E2B-IT model |
| **Backend** | GPU (OpenCL via `libOpenCL.so`) |
| **Model Path** | `/data/local/tmp/gemma-4-E2B-it.litertlm` (2.58 GB) |

## Android Architecture

| Component | Version/Details |
|-----------|-----------------|
| **Android Gradle Plugin** | 8.7.0 |
| **Gradle** | Foojay toolchain resolver 0.8.0 |
| **DataStore Preferences** | 1.1.1 |
| **Material Components** | 1.12.0 |

## Build Configuration

- **Namespace:** `com.gemma.gpuchat`
- **ABI Filter:** `arm64-v8a` only
- **NDK:** Used for GPU native libraries
- **Minify:** Disabled (debug builds)

## Key Dependencies Summary

```
com.google.ai.edge.litertlm:litertlm-android:latest.release
androidx.compose:compose-bom:2024.12.01
androidx.compose.material3:material3
com.tom-roush:pdfbox-android:2.0.27.0
androidx.datastore:datastore-preferences:1.1.1
androidx.activity:activity-compose:1.9.3
androidx.lifecycle:lifecycle-runtime-compose:2.8.7
```

---

*Document: STACK.md — Part of .planning/codebase/*