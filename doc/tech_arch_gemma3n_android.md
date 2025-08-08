# Kikko’s Saga Forge — Technical Architecture (Gemma 3n on Android)

> **Version:** 1.0 · **Last updated:** 2025‑08‑08  
> **Scope:** How Kikko uses **Google AI Edge** to run **Gemma 3n** on‑device, plus the other Android components that make the “Knowledge Forging” use case compelling, efficient, and privacy‑preserving.

---

## 1) Executive summary
**Kikko’s Saga Forge** turns **live camera input** into **verifiable knowledge cards**, fully **offline**. We use **Gemma 3n** (Google AI Edge) for on‑device text+vision reasoning, wrapped by a reproducible **prompt→JSON** contract, and orchestrated through **WorkManager** with a **Room** queue. This unlocks: sub‑second local reasoning (device‑dependent), zero cloud cost, and strict data locality.

**Why Gemma 3n?** It’s a **multimodal** small language model designed for **edge devices** (phones, tablets, laptops). It runs via the **AI Edge LLM Inference API** using **LiteRT (`.task`)** bundles; Android supports **multimodal prompting** (text + one image per session) and standard decoding controls, with optional **on‑device RAG** and **function calling** SDKs.

---

## 2) Most compelling use case (story + spec)
**“Field‑to‑Knowledge, offline.”** A child points the camera at a product label or a plant. Kikko captures **raw pollen** (OCR text, barcode, objects), then **forges** a **card**:
1. **Identify** the subject from detections and context (vision+text).  
2. **Describe** concisely (kid‑friendly) and extract **structured facts** (JSON).  
3. Generate **quiz** questions and **translations**.  
4. Store card + provenance; optionally **duel** with another device in **Clash**.

**Why this is compelling on Google AI Edge:**
- **Latency & privacy:** zero network; instant feedback in the classroom or in the wild.  
- **Multimodal:** combine OCR’d text and the camera frame inside the same LLM session.  
- **Cost:** no server bills; works in low‑connectivity contexts.  
- **Extensible:** add **on‑device RAG** for curricula or safety sheets; add **function calling** for tools (e.g., unit converters).

---

## 3) High‑level architecture
```
CameraX → ML Kit (OCR, Barcode, Object) → Pollen (RAW)
               │                            │
               └─> Provenance (JSON) ───────┤
Room (HiveJob queue) ── WorkManager (foreground when needed)
               │
               ├─> LLM Stage A: IDENTIFY (Gemma 3n, text+image)
               ├─> LLM Stage B: DESCRIPTION (Gemma 3n → Markdown)
               ├─> LLM Stage C: FACTS (Gemma 3n → JSON schema)
               ├─> LLM Stage D: QUIZ (Gemma 3n → JSON MCQ)
               └─> LLM Stage E: TRANSLATE (Gemma 3n → target locales)

Cards (Room) ← Images (MediaStore) ← Provenance (files) ← Audit logs
P2P Clash (Nearby/Bluetooth) · Voice (Vosk STT + Android TTS)
```

---

## 4) Gemma 3n on Android via Google AI Edge

### 4.1 Model delivery & format
- Use **LiteRT `.task`** bundles (tokenizer + compiled graph). During dev, **ADB‑push** to `/data/local/tmp/llm/…`. In production, **download post‑install** (APK size limits) and cache in app‑private storage.  
- **Gemma‑3n** models are commonly distributed as LiteRT bundles. Vision prompting supports **one image per session**.

### 4.2 Dependency & initialization (Kotlin)
```kotlin
// build.gradle
implementation("com.google.mediapipe:tasks-genai:0.10.24")

// Load model & basic config
val options = LlmInferenceOptions.builder()
    .setModelPath(modelPath) // /data/local/tmp/llm/gemma-3n-e2b.task
    .setMaxTokens(512)
    .setTopK(40)
    .setTemperature(0.7f)
    .build()
val llm = LlmInference.createFromOptions(context, options)

// Single‑turn generation
val out = llm.generateResponse(prompt)

// Streaming
val streamOpts = LlmInferenceOptions.builder()
    .setResultListener { partial, done -> /* update UI */ }
    .build()
llm.generateResponseAsync(streamOpts)
```

### 4.3 Multimodal (text + image)
```kotlin
val visionSession = LlmInferenceSession.createFromOptions(
  llm,
  LlmInferenceSession.LlmInferenceSessionOptions.builder()
    .setTopK(10)
    .setTemperature(0.4f)
    .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
    .build()
)
visionSession.addQueryChunk("Extract key facts from this label.")
visionSession.addImage(BitmapImageBuilder(bitmap).build())
val result = visionSession.generateResponse()
```

> Notes: enable **vision modality**, pass **MPImage**, add **text then image**, and set **MaxNumImages(1)** for Gemma 3n.

### 4.4 CPU/GPU selection & quantization
- Provide a **backend toggle** (CPU/GPU) in a dev menu; production defaults are device‑profiled.  
- Prefer **INT4** quantized variants for throughput/size when available.

### 4.5 On‑device RAG & function calling (optional)
- For long documents (nutrition regs, plant guides), add **on‑device RAG**: chunk → embed → local vector index → retrieve → **augment prompt** → generate.  
- Use **function calling** for local tools (e.g., unit conversion) driven by the LLM.

---

## 5) Prompt & output contracts (LLM stages)
We enforce **strict JSON schemas** to keep the game deterministic and kid‑safe. All prompts begin with a **system preface** (tone, brevity, age‑appropriateness) and a **schema block**.

**A) IDENTIFY** (multimodal)  
- **Input:** OCR text (cleaned), object labels, barcode payload, + image.  
- **Output (JSON):** `{ name, category, confidence, evidence[] }`.

**B) DESCRIPTION**  
- **Output (Markdown):** ≤80 words, simple vocabulary, cite evidence tokens (`[OCR:x]`).

**C) FACTS**  
- **Output (JSON):** `{ nutrition?:{…}, origin?:{…}, safety?:{…}, tags:[…] }`.

**D) QUIZ**  
- **Output (JSON):** `{ q: string, choices:[…], correctIndex:int, rationale:string }`.

**E) TRANSLATE**  
- **Output:** same Markdown in target locales.

---

## 6) Android building blocks used
- **CameraX** (preview + capture), **ML Kit** (Text Recognition, Barcode, Object) to form **pollen**.  
- **Room** (entities: `HiveJob`, `Card`, `Provenance`); **Flow** to live‑update UI.  
- **WorkManager** with **foreground** `dataSync` for long LLM runs; exponential backoff & retry.  
- **Nearby/Bluetooth** for **Clash** discovery & pairing; **Vosk** for offline STT; **Android TTS** for voice replies.

---

## 7) Performance & resource profile (guidance)
- Prefer **INT4** models for decode speed and footprint; keep **maxTokens** conservative (≤512) per stage.  
- Stream UI from `generateResponseAsync()` to reduce perceived latency.  
- Avoid bundling models in the APK; **download on first run**.  
- Test on a device matrix (e.g., Pixel 8/8a, S23+, 4GB mid‑tier); measure **TTFT** and **tok/s**.

---

## 8) Privacy, safety & offline stance
- **No cloud calls** in the forging path; all data remains on device.  
- **Provenance:** persist OCR, detections, and prompts used to generate each card.  
- **Safety:** constrain outputs via schemas; block unsafe categories at the **prompt gateway**; (optionally) run local safety classifiers as a pre‑filter.

---

## 9) Packaging, updates & CI/CD
- Model **licensing/acceptance**: require user consent before downloading Gemma weights.  
- **Version pinning:** store model **bundle ID** + **hash**; allow **rollback**.  
- CI: run unit tests for **prompt contracts** (JSON schema validation) and integration tests for **LLM stages** with **golden outputs**.

---

## 10) Risks & mitigations
- **Device heterogeneity** → provide a **model selector** (E2B ↔ E4B; CPU/GPU).  
- **Cold‑start cost** → pre‑warm in a **foreground worker**.  
- **Long prompts** → truncate OCR, dedupe labels, use RAG only when needed.  
- **Hallucinations** → strict schemas, cite evidence tokens, UI flags for low confidence.

---

## Appendix A — Minimal Android init checklist
1. Add `implementation "com.google.mediapipe:tasks-genai:0.10.24"`.  
2. Download a **Gemma‑3n** `.task` (E2B/E4B) to app storage.  
3. Create `LlmInference` with **path, top‑k, temperature, maxTokens**.  
4. For multimodal, enable **vision modality**, set **MaxNumImages(1)** and pass **MPImage**.  
5. Use **streaming** for responsiveness; validate **JSON** on receipt; persist **provenance**.
