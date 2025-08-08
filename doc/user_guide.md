# Kikko’s Saga Forge — User Guide

> **Version**: 1.0 (draft)
> **Platform**: Android (offline, on‑device)

---

## Table of Contents

1. [About](#about)
2. [Install](#install)
3. [First Launch & Permissions](#first-launch--permissions)
4. [Home (Start)](#home-start)
5. [Tools Menu](#tools-menu)
6. [Live Forge (Camera)](#live-forge-camera)
7. [Forge Workshop](#forge-workshop)
8. [Cards & Decks](#cards--decks)
9. [Royal Audience (AI Chat)](#royal-audience-ai-chat)
10. [Prompt Editor](#prompt-editor)
11. [Clash Arena (Duels)](#clash-arena-duels)
12. [Review Quiz](#review-quiz)
13. [Import/Export Saga (.kikkoSaga)](#importexport-saga-kikkosaga)
14. [Local AI Models](#local-ai-models)
15. [Quick Troubleshooting](#quick-troubleshooting)

---

## About

**Kikko’s Saga Forge** is a verifiable‑knowledge RPG. You capture **raw pollen** (camera input like labels, barcodes, objects), the on‑device AI **forges** it into collectible **knowledge cards**, then you play, revise, and share — all **offline** after resources are installed.

---

## Install

* Download the APK from the repository’s README link.
* Allow installs from your browser/file manager if prompted.
* Open the APK to install, then launch the app.

> **IMG**
> `![Android install screen](illustrations/userguide/install_apk.png "PROMPT: smartphone screenshot-like, Android package install dialog for 'Kikko’s Saga Forge', neutral lighting, no personal data visible")`

---

## First Launch & Permissions

The app may request: **Camera**, **Microphone** (if speech-to-text), **Notifications**, **Nearby Devices**/**Bluetooth** (P2P Clash), **Approx Location** (Clash radar). Grant only what you plan to use.

> **IMG**
> `![Runtime permissions popups](illustrations/userguide/permissions.png "PROMPT: collage of Android runtime permission popups for Camera, Microphone, Nearby devices, Notifications; styled to match Kikko’s honey-gold theme; no personal info")`

---

## Home (Start)

Four primary buttons: **Kikko (Decks)**, **Pollen (Live)**, **Forge (Workshop)**, **Clash**. Top‑right: **Tools** (gear). Counters show **Raw Pollen**, **In Forge**, **Total Honey (cards)**, **Errors**.

**Hidden tip**: press‑and‑hold (\~1s) the **turtle belly** area near the bottom center for a small animation.

> **IMG**
> `![Home with counters + secret area](illustrations/userguide/start_screen.png "PROMPT: cinematic 3D UI mockup, honey-gold hex grid frame, Start screen with 4 big buttons (Decks, Pollen, Forge, Clash), top-right tools icon, badges counters; translucent highlight over turtle belly labeled 'hold 1s secret'")`

---

## Tools Menu

From the **gear** icon:

* **Import/Export a Saga** (.kikkoSaga)
* **Add/Remove an AI model** (.task)
* **Manage Prompts** (open the editor)
* **(Optional) Import Vosk** speech model (STT)
* **Nuke the hive** (reset local DB)

> **IMG**
> `![Tools modal](illustrations/userguide/tools_dialog.png "PROMPT: modal sheet listing actions: Import/Export Saga, Add model, Delete model (list), Manage prompts, Import Vosk, Reset database; honey-gold material UI")`

---

## Live Forge (Camera)

Camera mode to capture **raw pollen**. An overlay draws detections (OCR text boxes, object labels, barcodes). Each capture creates a **Pollen Grain** queued for the Forge pipeline.

> **IMG**
> `![Camera with overlay](illustrations/userguide/forge_live.png "PROMPT: smartphone camera viewfinder with honey-gold frame, green overlay around an ingredients list, small 'OCR' and 'Barcode' badges; callout: 'Capture to add RAW pollen'")`

---

## Forge Workshop

Dashboard of **grains** and their **status**:

* **RAW** → **IDENTIFYING** → **PENDING\_DESCRIPTION** → **PENDING\_STATS** → **PENDING\_QUIZ** → **PENDING\_TRANSLATION** → **HONEY (card)**
* **ERROR** if a step failed (you can retry).

> **IMG**
> `![Grains with status chips](illustrations/userguide/forge_workshop.png "PROMPT: list with colored status chips (RAW, IDENTIFYING, PENDING_DESCRIPTION, PENDING_STATS, PENDING_QUIZ, PENDING_TRANSLATION, HONEY, ERROR); one item expanded with actions: retry, open provenance")`

---

## Cards & Decks

**Kikko (Decks)** opens the gallery by deck (**Food**, **Plant**, **Insect**, **Bird**). Tap a card for details: image, description, attributes, tags, and actions (**Quiz**, **Translate**, **Delete**).

> **IMG**
> `![Decks grid](illustrations/userguide/decks_grid.png "PROMPT: grid of collectible cards categorized by deck (Food, Plant, Insect, Bird); clean material design; honey-gold highlights; no personal data")`

> **IMG**
> `![Card details](illustrations/userguide/card_details.png "PROMPT: full-screen card detail view with big image, name, description, chips, and actions (Quiz, Translate, Delete); cinematic honey-gold UI")`

---

## Royal Audience (AI Chat)

Chat with the **Queen** (on‑device LLM):

* **Model** button to choose a **.task** file
* **Settings** (Temperature, Top‑K)
* Optional **voice**: import a **Vosk** model for STT; use TTS for spoken replies
* You can **attach an image** for contextual grounding

> **IMG**
> `![AI chat screen](illustrations/userguide/royal_audience.png "PROMPT: chat screen with alternating bubbles (user/queen), top appbar 'Royal Audience', buttons 'Model' and 'Settings', a mic button state; a message with an attached photo thumbnail; elegant honey-gold theme")`

> **IMG**
> `![Model picker & Settings](illustrations/userguide/queen_model_settings.png "PROMPT: two modals: 1) list of local .task models with radio selection; 2) sliders for temperature and top‑K with helper labels; material components, honey-gold accents")`

---

## Prompt Editor

Browse prompt keys, **edit**, **save**, **import/export** a JSON, or **restore defaults**.

> **IMG**
> `![Prompt editor](illustrations/userguide/prompt_editor.png "PROMPT: screen with toolbar menu (import/export/reset), a dropdown of prompt keys, a large multiline text area, and a Save button; clean honey-gold UI")`

---

## Clash Arena (Duels)

Pick a **mode** (Solo / P2P), choose your **champions** (cards by deck), then start the **duel**. For P2P, use the **radar** to discover the opponent, **accept** the connection, and play.

> **IMG**
> `![Clash setup](illustrations/userguide/clash_setup.png "PROMPT: arena setup screen: judge status line (model/brain/temp); deck slots for Player 1 and Player 2; buttons Random/Settings/Radar; Start disabled until both ready")`

> **IMG**
> `![Clash P2P connect](illustrations/userguide/clash_p2p_connect.png "PROMPT: 'connection request' dialog with opponent name and short auth code, Accept/Decline buttons; background radar scanning animation")`

> **IMG**
> `![Clash duel](illustrations/userguide/clash_duel.png "PROMPT: split view with two big card images facing off; bright light burst at center; score/progress indicators; animated background muted; cinematic honey-gold theme")`

---

## Review Quiz

Every card can spawn a **Quiz** (multiple-choice). Submit answers, get **instant feedback**, then see your **final score**.

> **IMG**
> `![Quiz screen](illustrations/userguide/quiz.png "PROMPT: quiz screen with progress 'Question 1/5', a material card showing the question, 4 radio answers, Submit then Next; feedback panel turns green/red")`

---

## Import/Export Saga (.kikkoSaga)

* **Export**: creates a `.kikkoSaga` archive (cards + images + analyses) and opens the Android **share sheet**.
* **Import**: pick a `.kikkoSaga`; **new** cards are **grafted** without name duplicates.

> **IMG**
> `![Import/Export flow](illustrations/userguide/saga_flow.png "PROMPT: two-step storyboard: 1) export creating KikkoSaga_YYYYMMDD.kikkoSaga and Android share sheet; 2) import picker then success toast; neutral device UI")`

---

## Local AI Models

* **Add a model**: pick a **.task** → appears in local models list.
* **Remove**: delete from the local list.
* **Vosk (voice)**: import a **.zip** speech model; it installs into the local folder and becomes available in **Royal Audience**.

> **IMG**
> `![Local models list](illustrations/userguide/local_models.png "PROMPT: recycler list of local AI models (.task) with filename and per-row trash icon; honey-gold accents")`

---

## Quick Troubleshooting

* **Welcome video pauses when app is backgrounded**: expected (Android pauses playback on activity pause).
* **No speech model**: import a **Vosk** model before enabling dictation.
* **ERROR status in Workshop**: retry the failed step or delete the grain.

> **IMG**
> `![Help & tips](illustrations/userguide/help_tips.png "PROMPT: small tips panel with 3 bullets and icons (video pause, mic/model, error retry); clean honey-gold infographics")`

---

## Notes for Contributors

* Place screenshots/illustrations under `illustrations/userguide/`.
* The **title** of each image (the part in quotes after the file path) holds a **concise prompt** to regenerate a consistent visual.

---

*End of guide.*
