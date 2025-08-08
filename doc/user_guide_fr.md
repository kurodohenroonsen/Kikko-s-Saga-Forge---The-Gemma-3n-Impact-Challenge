# Kikko’s Saga Forge — Guide Utilisateur

> **Version** : 1.0 (brouillon)
> **Plateforme** : Android (offline, on-device)

---

## Sommaire

1. [À propos](#à-propos)
2. [Installation](#installation)
3. [Premier démarrage & permissions](#premier-démarrage--permissions)
4. [Écran d’accueil (Start)](#écran-daccueil-start)
5. [Menu Outils (Tools)](#menu-outils-tools)
6. [Forage en direct (Forge Live)](#forage-en-direct-forge-live)
7. [Atelier de la Forge (Forge Workshop)](#atelier-de-la-forge-forge-workshop)
8. [Cartes & Decks](#cartes--decks)
9. [Audience Royale (chat IA)](#audience-royale-chat-ia)
10. [Éditeur de Prompts](#éditeur-de-prompts)
11. [Arène de Clash (duels)](#arène-de-clash-duels)
12. [Quiz de révision](#quiz-de-révision)
13. [Import/Export de Saga (.kikkoSaga)](#importexport-de-saga-kikkosaga)
14. [Gestion des modèles IA locaux](#gestion-des-modèles-ia-locaux)
15. [Dépannage rapide](#dépannage-rapide)

---

## À propos

**Kikko’s Saga Forge** est un RPG de connaissance vérifiable. Vous capturez des données ("pollen brut") avec l’appareil photo, l’IA locale les forge en cartes de savoir, puis vous jouez, révisez et partagez. 100% **offline** après installation des ressources.

---

## Installation

* Téléchargez l’APK depuis le lien du README.
* Autorisez l’installation d’apps depuis votre navigateur/fichier.
* Lancez l’application.

> **IMG**
> `![Écran Android « Installer l’app »](illustrations/userguide/install_apk.png "PROMPT: smartphone screenshot-like, Android package install dialog for 'Kikko’s Saga Forge', no personal data visible, neutral lighting")`

---

## Premier démarrage & permissions

L’app peut demander : **Caméra**, **Micro** (si STT activé), **Notifications**, **Bluetooth/Proximité** (Clash P2P), **Localisation approximative** (radar Clash). Acceptez si vous utilisez ces fonctions.

> **IMG**
> `![Boîtes de dialogue de permissions](illustrations/userguide/permissions.png "PROMPT: collage of Android runtime permission popups for Camera, Microphone, Nearby devices, styled to match Kikko theme, no personal info")`

---

## Écran d’accueil (Start)

Quatre boutons principaux : **Kikko (Decks)**, **Pollen (Live)**, **Forge (Atelier)**, **Clash**. En haut à droite : **Outils** (icône engrenage). Des compteurs affichent : Pollen brut, En forge, Miel total (cartes), Erreurs.

**Astuce secrète** : toucher et **maintenir \~1 s** la zone du **ventre de Kikkō** (centre-bas de l’écran) déclenche une petite animation.

> **IMG**
> `![Accueil avec compteurs + zone secrète](illustrations/userguide/start_screen.png "PROMPT: cinematic 3D UI mockup, honey-gold hex grid frame, Start screen with 4 big buttons (Decks, Pollen, Forge, Clash), top-right tools icon, counters badges; highlight a translucent rectangle over turtle belly (center-bottom) labeled 'hold 1s secret'")`

---

## Menu Outils (Tools)

Depuis l’icône **engrenage** :

* **Importer/Exporter une Saga** (.kikkoSaga)
* **Ajouter/Supprimer un modèle IA** (.task)
* **Gérer les Prompts** (ouvrir l’éditeur)
* **(Optionnel) Importer un modèle Vosk** (reconnaissance vocale)
* **Nettoyer la ruche** (réinitialiser la base locale)

> **IMG**
> `![Boîte de dialogue Outils](illustrations/userguide/tools_dialog.png "PROMPT: modal sheet listing tools actions: Import/Export Saga, Add model, Delete model (list), Manage prompts, Import Vosk, Nuke database; honey-gold material UI")`

---

## Forage en direct (Forge Live)

Mode **caméra** pour capter du *pollen brut* (étiquettes, codes-barres, objets). Une surcouche dessine les détections (OCR, labels, etc.). Chaque capture crée un **Grain de pollen** dans la file de la Forge.

> **IMG**
> `![Caméra avec overlay](illustrations/userguide/forge_live.png "PROMPT: smartphone camera viewfinder with honey-gold frame, green overlay around ingredients list, small badges 'OCR', 'Barcode'; instruction callout 'capture to add RAW pollen'")`

---

## Atelier de la Forge (Forge Workshop)

Tableau de bord des **grains** et de leur **statut** :

* **RAW** → **IDENTIFYING** → **PENDING\_DESCRIPTION** → **PENDING\_STATS** → **PENDING\_QUIZ** → **PENDING\_TRANSLATION** → **HONEY (carte)**
* **ERROR** si un traitement a échoué (relance possible).

> **IMG**
> `![Liste des grains avec statuts](illustrations/userguide/forge_workshop.png "PROMPT: list of items with colored status chips (RAW, IDENTIFYING, PENDING_DESCRIPTION, etc.), honey-gold accents, one item expanded showing actions: retry, open provenance")`

---

## Cartes & Decks

**Kikko (Decks)** ouvre la galerie par deck (**Food**, **Plant**, **Insect**, **Bird**). Touchez une carte pour les détails : image, description, attributs, tags, actions (**Quiz**, **Traduire**, **Supprimer**).

> **IMG**
> `![Grille de cartes par deck](illustrations/userguide/decks_grid.png "PROMPT: grid of collectible cards categorized by deck (Food, Plant, Insect, Bird), clean material design, honey-gold highlights, no personal data")`

> **IMG**
> `![Détail d’une carte](illustrations/userguide/card_details.png "PROMPT: full-screen card detail dialog with big image, name, description, chips, and 3 actions: Quiz, Translate, Delete; cinematic honey-gold UI")`

---

## Audience Royale (chat IA)

Conversation avec la **Reine** (LLM on-device) :

* Bouton **modèle** pour choisir la **reine** (sélection d’un fichier .task)
* **Réglages** (Température, Top‑K)
* Option **voix** : importer un modèle **Vosk** (STT) et utiliser TTS pour réponses orales
* Possibilité d’**ajouter une image** au message (analyse contextuelle)

> **IMG**
> `![Chat avec la Reine](illustrations/userguide/royal_audience.png "PROMPT: chat screen with alternating bubbles (user/queen), top appbar 'Audience Royale', buttons for 'Model' and 'Settings', small mic button state; one message shows an attached photo thumbnail; elegant honey-gold theme")`

> **IMG**
> `![Sélecteur de modèle & Réglages](illustrations/userguide/queen_model_settings.png "PROMPT: two modal dialogs side-by-side: 1) list of local .task models with radio selection; 2) sliders for temperature and top‑K labeled in friendly language; material components, honey-gold")`

---

## Éditeur de Prompts

Permet de **parcourir** les clés de prompts, **modifier**, **enregistrer**, **importer/exporter** un JSON, ou **restaurer** les valeurs par défaut.

> **IMG**
> `![Éditeur de prompts](illustrations/userguide/prompt_editor.png "PROMPT: screen with toolbar menu (import/export/reset), a spinner listing prompt keys, a large multiline text area, and a Save button; clean honey-gold UI")`

---

## Arène de Clash (duels)

Sélectionnez un **mode** (Solo / P2P), choisissez vos **champions** (cartes par deck), puis lancez le **duel**. En P2P, utilisez le **radar** pour détecter l’adversaire, **acceptez** la connexion, et commencez.

> **IMG**
> `![Clash – préparation](illustrations/userguide/clash_setup.png "PROMPT: arena setup screen: judge status line (model/brain/temp), deck slots for Player 1 and Player 2, buttons Random/Settings/Radar, start button disabled until ready")`

> **IMG**
> `![Clash – connexion P2P](illustrations/userguide/clash_p2p_connect.png "PROMPT: modal 'connection request' dialog with opponent name and short auth code, buttons Accept/Decline; background shows radar scanning animation")`

> **IMG**
> `![Clash – duel en cours](illustrations/userguide/clash_duel.png "PROMPT: split view: two big card images facing off with light burst at center, score/progress indicators, background animated video muted")`

---

## Quiz de révision

Chaque carte peut générer un **Quiz** (questions à choix multiple). Répondez, obtenez un **feedback** immédiat, puis voyez votre **score final**.

> **IMG**
> `![Écran de Quiz](illustrations/userguide/quiz.png "PROMPT: quiz screen with progress 'Question 1/5', a material card showing the question, 4 radio answers, Submit then Next; feedback card turns green/red")`

---

## Import/Export de Saga (.kikkoSaga)

* **Exporter** : crée une archive ".kikkoSaga" (cartes + images + analyses) et ouvre le **partage**.
* **Importer** : sélectionnez une archive ; les cartes **nouvelles** sont **greffées** sans doublons par nom.

> **IMG**
> `![Flux Export/Import](illustrations/userguide/saga_flow.png "PROMPT: two-step storyboard: 1) export creating KikkoSaga_YYYYMMDD.kikkoSaga and Android share sheet; 2) import picking a .kikkoSaga then success toast; neutral device UI")`

---

## Gestion des modèles IA locaux

* **Ajouter un modèle** : sélectionner un fichier **.task** → listé comme **modèle local**.
* **Supprimer** : depuis la liste des modèles locaux.
* **Vosk (voix)** : importer un **.zip** du modèle vocal ; il s’installe dans le dossier local et devient disponible pour l’**Audience**.

> **IMG**
> `![Liste des modèles locaux](illustrations/userguide/local_models.png "PROMPT: recycler list of local AI models (.task) with filename and trash icon per row; honey-gold accents")`

---

## Dépannage rapide

* **La vidéo d’accueil s’arrête en arrière-plan** : normal (Android met en pause la lecture quand l’activité est en pause).
* **Aucun modèle vocal** : importez un **modèle Vosk** avant d’activer la dictée.
* **Statut ERROR** dans l’Atelier : réessayez l’étape ou supprimez le grain.

> **IMG**
> `![Aide & conseils](illustrations/userguide/help_tips.png "PROMPT: small tips panel with 3 bullets and icons (video pause, mic/model, error retry); clean honey-gold infographics")`

---

## Notes pour contributeurs

* Les captures d’écran/illustrations peuvent être déposées dans `illustrations/userguide/`.
* Les **titles** des balises `![...](... "TITLE")` contiennent des **prompts résumés** pour régénérer des visuels cohérents.

---

*Fin du guide.*
