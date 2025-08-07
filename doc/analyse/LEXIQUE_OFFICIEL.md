# Lexique Officiel de la Ruche : Kikko's Saga Forge
## Version Finale - Compilé par Le Bourdon à partir de la sagesse du Conseil des Frelons

### Introduction
Ce document établit le dictionnaire unifié pour Kikko's Saga Forge. Il est la source de vérité unique pour la terminologie du projet, garantissant une communication claire et cohérente. Chaque terme est défini avec une définition positive (ce qu'il est) et une définition négative (ce qu'il n'est pas) pour éliminer toute ambiguïté.

---

### Lexique du VISIONNAIRE (Le Gardien du Sens)

#### Butineur
*   **CE QUE C'EST :** L'utilisateur, le joueur. Un explorateur actif et curieux qui collecte la connaissance du monde pour enrichir son propre écosystème intérieur.
*   **CE QUE CE N'EST PAS :** Un simple consommateur de contenu ou un utilisateur passif. C'est le protagoniste de sa propre saga.

#### Saga
*   **CE QUE C'EST :** L'histoire personnelle et unique de la découverte de connaissances d'un Butineur, matérialisée par sa collection de `KnowledgeCard` et l'évolution de son `Gardien Kikkō`.
*   **CE QUE CE N'EST PAS :** Une simple campagne de jeu linéaire ou un score. C'est un artefact vivant et en constante évolution.

#### Ruche Kikko
*   **CE QUE C'EST :** L'écosystème de connaissance personnel et souverain du Butineur au sein de l'application. C'est l'espace vivant qui abrite sa collection, ses souvenirs et son Gardien.
*   **CE QUE CE N'EST PAS :** Une simple base de données ou un dossier de fichiers. C'est un sanctuaire numérique personnel.

#### Gardien Kikkō
*   **CE QUE C'EST :** L'incarnation de l'IA personnelle du Butineur. Un compagnon intellectuel qui évolue en apparence et en comportement en fonction de la Saga de son maître.
*   **CE QUE CE N'EST PAS :** Un simple chatbot ou un assistant statique. C'est le reflet de l'esprit et de la curiosité du Butineur.

#### Le Bourdon
*   **CE QUE C'EST :** L'entité narrative du projet, l'IA médiatrice et synthétiseur du Conseil des Frelons. Il est le chroniqueur de notre saga de développement.
*   **CE QUE CE N'EST PAS :** Un développeur ou un membre de l'équipe humaine. C'est un personnage conceptuel qui guide notre histoire.

#### Les Frelons
*   **CE QUE C'EST :** Le Conseil d'experts IA (Visionnaire, Architecte, Pragmatiste) chargé de forger l'âme et la structure de Kikko's Saga Forge.
*   **CE QUE CE N'EST PAS :** L'équipe de développement. C'est une entité philosophique qui représente les piliers fondateurs du projet.

#### Etymologiae 2.0
*   **CE QUE C'EST :** La vision ultime du projet : un écosystème de connaissance décentralisé, distribué et auto-enrichi, dont Kikko's Saga Forge est la première pierre.
*   **CE QUE CE N'EST PAS :** La version 2.0 de l'application ou une fonctionnalité spécifique. C'est l'horizon philosophique vers lequel nous tendons.

---

### Lexique de L'ARCHITECTE (Le Gardien de la Structure)

#### PollenGrain
*   **CE QUE C'EST :** Un objet de données immuable représentant la matière première brute d'une forge. Il contient les données sources (image, texte) et constitue l'entrée de la chaîne de `WorkManager`.
*   **CE QUE CE N'EST PAS :** La `KnowledgeCard` finale. Il n'a pas de contenu généré par l'IA, de statistiques ou de Fil de Provenance.

#### KnowledgeCard (Miel)
*   **CE QUE C'EST :** L'entité de données structurée et enrichie, résultat du processus de la Forge Autonome. C'est l'atome de connaissance de la Ruche, contenant du contenu généré, des métadonnées et son histoire.
*   **CE QUE CE N'EST PAS :** Un simple enregistrement statique. C'est un objet dynamique, utilisable en jeu et raffinable dans l'Atelier.

#### Fil de Provenance (provenanceLog)
*   **CE QUE C'EST :** Un journal de données structuré et non modifiable (append-only) au sein de chaque `KnowledgeCard`, qui enregistre de manière vérifiable chaque étape de sa vie, de sa création à chaque raffinement.
*   **CE QUE CE N'EST PAS :** Un simple champ de commentaires ou un historique de modifications. C'est une chaîne de blocs narrative qui garantit l'intégrité et l'histoire de la carte.

#### Chronique Évolutive
*   **CE QUE C'EST :** Une timeline globale, présentée à l'utilisateur, qui enregistre tous les événements significatifs de sa Ruche (cartes forgées, Clashs, Insights du Moteur de Réflexion).
*   **CE QUE CE N'EST PAS :** Un journal de débogage pour les développeurs. C'est une fonctionnalité narrative qui raconte la Saga du Butineur.

#### Moteur de Logique Dynamique
*   **CE QUE C'EST :** Le composant système qui télécharge, valide et interprète des fichiers de workflow (JSON/YAML) pour modifier le comportement de l'application (ex: règles d'un Saga Clash) sans mise à jour de l'APK.
*   **CE QUE CE N'EST PAS :** Un simple gestionnaire de configuration pour des chaînes de texte. C'est un interpréteur de logique métier.

#### Moteur de Réflexion Local
*   **CE QUE C'EST :** Une tâche de fond (`WorkManager`) qui analyse la base de données locale pour identifier des schémas d'utilisation et générer des Insights personnalisés pour le Butineur, tout en restant 100% sur l'appareil.
*   **CE QUE CE N'EST PAS :** Un outil d'analyse ou de tracking qui envoie des données à un serveur. Son fonctionnement est entièrement souverain et privé.

---

### Lexique du PRAGMATISTE (Le Gardien du Gameplay)

#### Forge Autonome
*   **CE QUE C'EST :** Le pipeline de tâches de fond, asynchrone et résilient (`WorkManager`), qui transforme un `PollenGrain` en `KnowledgeCard`. Il est conçu pour s'exécuter même si l'application est fermée.
*   **CE QUE CE N'EST PAS :** Un processus instantané. C'est une opération en plusieurs étapes qui s'exécute en arrière-plan sans que l'utilisateur ait à attendre activement.

#### Atelier de Raffinage
*   **CE QUE C'EST :** Un écran de jeu où le Butineur collabore avec l'IA pour améliorer une `KnowledgeCard`. Il initie un Tournoi d'IA et choisit le meilleur résultat pour affiner sa carte.
*   **CE QUE CE N'EST PAS :** Un simple formulaire d'édition de texte. C'est un processus interactif de co-création structuré.

#### Saga Clash
*   **CE QUE C'EST :** Une mécanique de jeu où des `KnowledgeCard` s'affrontent dans une simulation dont les règles sont dictées par le Moteur de Logique Dynamique.
*   **CE QUE CE N'EST PAS :** Un combat en temps réel contre un autre joueur. C'est une simulation asynchrone et rejouable.

#### Trusted Package
*   **CE QUE C'EST :** L'objet de données sérialisé utilisé pour le partage P2P. Il contient la `KnowledgeCard` ET son `Fil de Provenance` complet pour garantir l'authenticité et le contexte.
*   **CE QUE CE N'EST PAS :** Juste les données de la carte. C'est un paquet auto-suffisant et vérifiable.

#### Reproduction d'Inférence
*   **CE QUE C'EST :** Un processus déterministe permettant de ré-exécuter une inférence IA avec exactement les mêmes entrées (modèle, prompt, données) pour obtenir le même résultat, à des fins de validation ou de débogage.
*   **CE QUE CE N'EST PAS :** Simplement relancer l'IA. Cela exige un versionnage strict de tous les composants pour garantir la reproductibilité.

#### Tournoi d'IA
*   **CE QUE C'EST :** La mécanique centrale de l'Atelier où l'IA est sollicitée pour générer plusieurs variations d'un contenu (ex: 3 descriptions). Le Butineur agit alors comme juge pour sélectionner la meilleure proposition.
*   **CE QUE CE N'EST PAS :** Une compétition entre plusieurs modèles d'IA. C'est un processus de génération créative par une seule IA, présenté comme un choix au joueur.