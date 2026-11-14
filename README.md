# WAFA AI — Personal Multimodal AI Operating System

> **Your Personal AI. Your Digital Intelligence. Your Workspace.**

WAFA AI is a native Android application built with **Kotlin, Jetpack Compose, Material 3, Room, DataStore, OkHttp SSE, CameraX, Android Keystore, and Speech APIs**. It is designed as a Bring-Your-Own-Key (BYOK) personal AI operating system: install the APK on your Android phone and configure your own API keys directly inside the **API Manager** after installation—no hardcoded keys or build-time secrets required.

---

## Core Architecture & Features

- **12 First-Class AI Providers + 75 External API Registry**:
  - **XPiKi** (`https://api.xpiki.com/v1`, docs: `https://docs.xpiki.com/`) — chat completions, streaming, model discovery (`/models`), and image endpoints
  - **Google Gemini**, **OpenRouter**, **Groq** (LLM + Whisper STT), **Mistral**, **Cohere**, **Cloudflare Workers AI**, **SambaNova**, **NVIDIA NIM**, **Hugging Face**, **Custom OpenAI-Compatible / Proxy**, and **Local AI (Ollama / LM Studio)**
  - Searchable **75-API Registry** across 19 domains (LLM, Coding, Search, Research, Weather, Location, Maps, Translation, OCR, Image, Video, Audio, Speech, Documents, Data, Finance, Developer Tools, Messaging, Utilities)
- **Automatic Capability Model Router**: Selects models dynamically for Coding, Long-Context Document Analysis, Vision, Image Generation, Speech/Audio, or Research, with Auto/Manual mode and automatic provider fallback.
- **Android Keystore AES-256-GCM Vault**: Encrypts personal API keys locally, masks credentials in UI, redacts secrets from logs, and strips secrets during config export.
- **Full Multimodal Studios**:
  - **AI Chat**: Streaming responses, Markdown + copyable code blocks, image/document attachments, edit/regenerate/stop, EN/বাংলা support, export to Markdown.
  - **Coding Studio**: 12 languages, 9 engineering actions, multi-file explorer, and genuine `.zip` project export.
  - **Writing Studio**: 15 writing formats (EN & বাংলা), tone controls, and DOCX/PDF/MD export.
  - **Document Studio**: Real ECMA-376 `.docx` and native Android `PdfDocument` `.pdf` creation (with `PdfRenderer` page preview), TXT, MD, RTF, SAF reading, and AI comparison.
  - **Excel & Data Studio**: Multi-sheet `.xlsx` generation with Excel formulas (`=SUM`, `=AVERAGE`, `=MIN`, `=MAX`, `=COUNT`), live grid evaluation, and CSV export.
  - **Presentation Studio**: Real `.pptx` slide deck creation and `.pdf` export.
  - **Image, Audio & Video Intelligence**: Photo Picker Vision/OCR, image generation/compression, live Android `SpeechRecognizer` + `TextToSpeech` (EN/BN), `MediaRecorder` + Groq Whisper transcription, and `MediaMetadataRetriever` video keyframe extraction.
  - **Research Engine**: Live queries to Wikipedia, DuckDuckGo, Crossref Academic, arXiv, and Jina URL Reader with explicit separation of verified sources and AI conclusions.
  - **JARVIS Voice HUD, 10 Specialist Agents (with User Approval Gate), Personal Memory, Tasks & Personal Tools** (ZXing QR generator/scanner, Open-Meteo Weather, Frankfurter ECB FX, Calculator, Unit Converter, JSON/Regex/Base64/URL tools, Timezone converter).

---

## Requirements & Local Build Instructions

- **JDK**: 17+
- **Android Gradle Plugin**: 9.1.1 (`compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`)
- **Gradle**: 9.3.1

### Build & Test Commands
```bash
# Run unit and Robolectric tests
gradle :app:testDebugUnitTest

# Run Android Lint
gradle :app:lintDebug

# Build installable Debug APK
gradle :app:assembleDebug
```

Generated APK path:
- `app/build/outputs/apk/debug/app-debug.apk`
- Verified CI copies: `APK_DOWNLOAD/app-debug.apk` and `.build-outputs/app-debug.apk`

---

## GitHub Actions Automated APK Pipeline

The workflow at `.github/workflows/android-build.yml` runs on every push and manual dispatch to:
1. Set up JDK 17, Android SDK, and Gradle 9.3.1.
2. Execute `:app:testDebugUnitTest` and `:app:lintDebug`.
3. Assemble `app-debug.apk` via `gradle :app:assembleDebug`.
4. Verify non-zero size, ZIP/APK archive integrity (`unzip -t`), and identical `sha256sum` across `.build-outputs/app-debug.apk` and `APK_DOWNLOAD/app-debug.apk`.
5. Upload the `app-debug-apk` workflow artifact and commit the verified APK with `[skip ci]`.

---

## Post-Installation API Configuration Guide

1. Install `app-debug.apk` on your Android device (enable *Install unknown apps* for your file manager/browser if prompted).
2. Open **WAFA AI** -> tap **API & Core** (or **Configure APIs** on Home).
3. Select **XPiKi**, **Google Gemini**, **Groq**, **OpenRouter**, or any provider -> tap **Configure Key & Model** -> paste your API key -> **Encrypt & Save**.
4. Tap **Test Connection** to verify live connectivity and discover available models.

---

## Known Limitations & Security Notes

- **BYOK Local Storage**: Personal API keys stored on a device can be read by the app process while unlocked. For multi-user or production-sensitive deployments, route requests through your own backend proxy configured in **Settings**.
- **Video & Wake-Word Boundaries**: Video analysis extracts metadata and keyframe thumbnails safely within Android memory limits rather than uploading giant raw streams blindly; JARVIS voice wake-listening operates in the foreground upon explicit microphone permission.
