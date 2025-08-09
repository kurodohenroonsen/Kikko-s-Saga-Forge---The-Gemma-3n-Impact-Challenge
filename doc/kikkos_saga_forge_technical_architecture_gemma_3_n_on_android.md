# Kikko’s Saga Forge — Technical Architecture (Gemma 3n & Gemini Nano on Android)

> **Version:** 1.1 · **Last updated:** 2024-08-08
> **Scope:** How Kikko uses Google's on-device AI suite (MediaPipe for Gemma 3n, AICore/ML Kit for Gemini Nano) to power its "Knowledge Forging" gameplay, ensuring efficiency and privacy.

---

## 1) Executive summary
**Kikko’s Saga Forge** turns **live camera input** into **verifiable knowledge cards**, fully **offline**. We use **Gemma 3n** (via MediaPipe) for complex multimodal reasoning and **Gemini Nano** (via AICore/ML Kit) for specialized, fast tasks. Both are orchestrated through **WorkManager** with a local database queue. This hybrid approach delivers sub-second local reasoning, zero cloud cost, and strict data locality.

**On-Device Models:** This application serves as a benchmark, allowing users to switch between **Gemma 3n** `.task` models and the system-provided **Gemini Nano** to compare their performance and characteristics in real-world scenarios.

---

## 2) Most compelling use case (story + spec)
**“Field‑to‑Knowledge, offline.”** A user points their camera at a product label or a plant. Kikko captures **raw pollen** (OCR text, barcode, objects), then **forges** a **card**:
1. **Describe (Nano):** Instantly generate a spoken description of the image.
2. **Identify (Gemma 3n):** Synthesize all sensor data to determine the subject (vision+text).
3. **Describe & Structure (Gemma 3n):** Write a detailed description and extract facts into a strict JSON schema.
4. **Enrich (Gemma 3n):** Generate quiz questions and translations.
5. **Store & Share:** Save the card with its full "Thread of Provenance" for verification.

**Why this is compelling on Google AI Edge:**
- **Latency & Privacy:** Zero network dependency ensures instant feedback and total data privacy.
- **Hybrid Power:** Use the right model for the job—Nano for speed, Gemma 3n for power.
- **Cost-Free Scalability:** No server bills, regardless of user numbers.
- **Extensible:** The architecture supports on-device RAG for custom knowledge bases and function calling for local tool integration.

---

## 3) High-level architecture
CameraX → ML Kit (OCR, Barcode, Object) → Pollen (RAW)
│ │
└─> Provenance (JSON) ───────┤
Room (Database queue) ── WorkManager (Orchestrator)
│
├─> LLM Stage 1: LIVE DESCRIBE (Gemini Nano via ML Kit)
├─> LLM Stage 2: IDENTIFY (Gemma 3n, text+image)
├─> LLM Stage 3: DESCRIPTION (Gemma 3n → text)
├─> LLM Stage 4: FACTS (Gemma 3n → JSON schema)
├─> LLM Stage 5: QUIZ (Gemma 3n → JSON MCQ)
└─> LLM Stage 6: TRANSLATE (Gemma 3n → target locales)
KnowledgeCards (Database) ← Images (Files) ← Provenance (Files)
P2P Clash (Nearby API) · Voice (Vosk STT + Android TTS)
code
Code
---

## 4) Gemma 3n on Android via MediaPipe
### 4.1 Model delivery & format
- We use **LiteRT `.task`** bundles, which contain the model graph and tokenizer.
- In Kikko, these models are downloaded by the user from a repository or sideloaded, then stored in the app's private directory, making the app fully offline-capable.

### 4.2 Dependency & initialization (Kotlin)
```kotlin
// build.gradle.kts
implementation(libs.mediapipe.tasks.genai)

// In ForgeLlmHelper.kt
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelPath) // e.g., /data/user/0/.../gemma-3n-e2b.task
    .setMaxTokens(4096)
    .build()
val llmInference = LlmInference.createFromOptions(context, options)
4.3 Multimodal (text + image)
Gemma 3n's vision capabilities are crucial for the "Identification" stage.
code
Kotlin
// In ForgeLlmHelper.kt
val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setTemperature(0.2f)
    .setTopK(40)
    .setGraphOptions(
        GraphOptions.builder().setEnableVisionModality(true).build()
    )
    .build()
val session = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)

session.addQueryChunk("Identify this subject based on the report.")
session.addImage(BitmapImageBuilder(bitmap).build())
session.generateResponseAsync { partialResult, done -> /* ... */ }
5) Gemini Nano on Android via AICore & ML Kit
5.1 Model delivery & API
Unlike Gemma 3n's manual .task management, Gemini Nano is delivered and updated via Google Play Services through the AICore SDK.
We access it using the high-level ML Kit GenAI Image Description API, which abstracts away model availability checks, downloads, and inference calls.
5.2 Dependency & initialization (Kotlin)
code
Kotlin
// build.gradle.kts
implementation("com.google.mlkit:genai-image-description:1.0.0-beta1")

// In ForgeLiveViewModel.kt or a helper
val imageDescriber = ImageDescription.getClient(
    ImageDescriberOptions.builder(context).build()
)
5.3 Usage (Image Description with Streaming)
This is used for the instant description feature in the Live Forge. The API provides a natural streaming interface via a callback.
code
Kotlin
// In ForgeLiveViewModel.kt
val request = ImageDescriptionRequest.builder(bitmap).build()
val fullResponse = StringBuilder()

// runInference provides a streaming callback
imageDescriber.runInference(request) { chunk ->
    fullResponse.append(chunk)
    // Update UI with partial result
}.await() // Coroutine wrapper for the Task

// Use fullResponse.toString() when done and trigger TTS
imageDescriber.close()
5.4 Feature Availability
Before running inference, it's mandatory to call imageDescriber.checkFeatureStatus().await(). The ML Kit API handles the download prompt if the model isn't ready on the device, ensuring a smooth user experience.
6) Prompt & output contracts (LLM stages)
We enforce strict JSON schemas to keep the game deterministic. All prompts begin with a system preface (defining the AI's role and constraints) and a clear output format specification.
- Stage 2 (IDENTIFY):
Input: JSON of ML Kit analysis + image.
Output (JSON): { "specificName": "...", "deckName": "...", "confidence": 0.95, "reasoning": {...} }.
- Stage 4 (FACTS):
Input: Text description, OCR data.
Output (JSON): { "stats": {"Energy": "520", ...}, "allergens": ["Milk"], ... }.
- Stage 5 (QUIZ):
Input: Text description, stats JSON.
Output (JSON Array): [{"q": "...", "o": ["...", "..."], "c": 0, "explanation": "..."}].
7) Android building blocks used
CameraX & ML Kit: Provide the initial raw data ("pollen").
Room Database: Manages the queue of forging tasks (PollenGrain table) and the final collection (KnowledgeCard table).
WorkManager: Acts as the offline orchestrator, running each forging stage sequentially as a foreground service to ensure reliability.
Nearby Connections API: Powers the P2P card duels in the Clash Arena.
Vosk & Android TTS: Provide offline speech-to-text and text-to-speech capabilities.
8) Performance & resource profile (guidance)
Model Selection: The app allows users to switch between Gemma 3n and Gemini Nano, demonstrating the trade-offs between the power of a larger .task model and the efficiency of the system-integrated Nano.
Streaming: UI is updated via streaming callbacks (setResultListener for MediaPipe, runInference callback for ML Kit) to reduce perceived latency.
Memory: Bitmaps are carefully managed, with copies created for parallel processing tasks to avoid recycled bitmap crashes, a critical lesson learned during development.
9) Privacy, safety & offline stance
100% On-Device: No cloud calls are made in the core forging and gameplay loops. All user data, images, and generated knowledge remain on the device.
Provenance: The app saves the full inputs (ML Kit JSON) and outputs (LLM raw response) for each stage, creating a "Thread of Provenance" that allows for verification and debugging.
Safety: Outputs are constrained via strict JSON schemas in prompts.
10) Packaging, updates & CI/CD
Model Licensing: The app directs users to a web repository to download models, where they can review licenses before installation.
Versioning: Models are managed in named folders, allowing for multiple versions to coexist.
CI/CD: The development loop heavily relies on validating prompt contracts and ensuring the robustness of the sequential, state-based processing pipeline managed by WorkManager.
Appendix A — Minimal Android init checklist
For Gemma 3n:
Add com.google.mediapipe:tasks-genai dependency.
Download a .task file to app storage.
Create LlmInference with the model path and configuration.
For multimodal, enable visionModality and set MaxNumImages(1).
For Gemini Nano:
Add com.google.mlkit:genai-image-description dependency.
Get the ImageDescription client.
Check feature status with checkFeatureStatus().
Run inference on a Bitmap via runInference().