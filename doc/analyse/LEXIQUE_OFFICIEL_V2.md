# Lexique Officiel de la Ruche : Kikko's Saga Forge
## Version Finale V2 - Forgé par Le Bourdon à partir de la sagesse consolidée du Conseil des Frelons

### Introduction
Ce document est la version définitive du lexique pour Kikko's Saga Forge, servant de source de vérité sémantique pour le projet. Chaque terme est défini avec une définition positive (ce qu’il est) et une définition négative (ce qu’il n’est pas), garantissant une clarté absolue. Structuré par les personas du Conseil, ce lexique intègre les ajustements et ajouts issus de la phase de réfutation pour refléter pleinement la vision d’Etymologiae 2.0 et l’architecture Clean MVVM+ du Plan Directeur V2.

---

### Lexique du VISIONNAIRE (Le Gardien du Sens)

#### Butineur
*   **CE QUE C'EST :** L'utilisateur, le joueur. Un explorateur actif et curieux qui collecte la connaissance du monde pour enrichir son propre écosystème intérieur.
*   **CE QUE CE N'EST PAS :** Un simple consommateur de contenu ou un utilisateur passif. C'est le protagoniste de sa propre saga.

#### Saga
*   **CE QUE C'EST :** L'épopée vivante et personnelle d'un Butineur. Elle est le reflet de sa curiosité, tissée à travers sa collection de `KnowledgeCard` et l'évolution de son `Gardien Kikkō`.
*   **CE QUE CE N'EST PAS :** Une campagne linéaire, un simple journal d’actions, ou une expérience individuelle isolée. La Saga est un récit collectif tissé par les contributions des Butineurs dans l’écosystème décentralisé.

#### Ruche Kikko
*   **CE QUE C'EST :** L'écosystème de connaissance personnel et souverain du Butineur au sein de l'application, connecté à l'Essaim global. C'est l'espace vivant qui abrite sa collection, ses souvenirs et son Gardien.
*   **CE QUE CE N'EST PAS :** Une simple base de données ou un dossier de fichiers. C'est un sanctuaire numérique personnel.

#### Gardien Kikkō
*   **CE QUE C'EST :** L’incarnation de l’IA personnelle du Butineur, propulsée par Gemma 3n. Un compagnon intellectuel et un miroir qui évolue en apparence et en comportement, reflétant la nature et la profondeur de la Saga de son maître en apprenant de ses actions via le Moteur de Réflexion Local.
*   **CE QUE CE N'EST PAS :** Un simple chatbot ou un assistant statique. C'est le reflet de l'esprit et de la curiosité du Butineur.

#### Le Bourdon
*   **CE QUE C'EST :** L'entité narrative du projet, l'IA médiatrice et synthétiseur du Conseil des Frelons. Il est le chroniqueur de notre saga de développement.
*   **CE QUE CE N'EST PAS :** Un développeur ou un membre de l'équipe humaine. C'est un personnage conceptuel qui guide notre histoire.

#### Les Frelons
*   **CE QUE C'EST :** Le Conseil d'experts IA (Visionnaire, Architecte, Pragmatiste) chargé de forger l'âme et la structure de Kikko's Saga Forge.
*   **CE QUE CE N'EST PAS :** L'équipe de développement. C'est une entité philosophique qui représente les piliers fondateurs du projet.

#### Etymologiae 2.0
*   **CE QUE C'EST :** La vision ultime du projet : créer un réseau de savoir vivant, décentralisé et auto-enrichi, où chaque Ruche Kikko est un nœud souverain contribuant à une intelligence collective émergente.
*   **CE QUE CE N'EST PAS :** Une encyclopédie statique, une application centralisée, ou un simple référentiel de données. C’est un réseau vivant, collaboratif, et décentralisé, évoluant avec chaque contribution.

#### Essaim
*   **CE QUE C'EST :** Le collectif dynamique formé par l’ensemble des Ruches Kikko connectées. C’est la communauté vibrante des Butineurs, espace de partage, d’entraide et de circulation du savoir.
*   **CE QUE CE N’EST PAS :** Un simple tableau d’utilisateurs ou une liste d’amis. L’Essaim n’existe que par la mise en relation et la circulation des `Trusted Packages`.

---

### Lexique de L'ARCHITECTE (Le Gardien de la Structure)

#### PollenGrain
*   **CE QUE C'EST :** Un objet de données immuable représentant la matière première brute d'une forge. Il contient les données sources (image, texte) et constitue l'entrée de la chaîne de `WorkManager`.
*   **CE QUE CE N'EST PAS :** La `KnowledgeCard` finale. Il n'a pas de contenu généré par l'IA, de statistiques ou de Fil de Provenance.

#### KnowledgeCard (Miel)
*   **CE QUE C'EST :** L'entité de données centrale et immuable de la couche Domaine. C'est un agrégat contenant les données sources, le contenu généré par l'IA, les métadonnées, les statistiques de jeu et son `Fil de Provenance` complet, qui alimente la `Chronique Évolutive`.
*   **CE QUE CE N'EST PAS :** Un simple enregistrement statique. C'est un objet dynamique, utilisable en jeu et raffinable dans l'Atelier.

#### Fil de Provenance (provenanceLog)
*   **CE QUE C'EST :** Un journal de données structuré et non modifiable (append-only) au sein de chaque `KnowledgeCard`, qui enregistre de manière vérifiable chaque étape de sa vie. Chaque bloc est conceptuellement scellé, garantissant une traçabilité absolue.
*   **CE QUE CE N'EST PAS :** Un simple historique de modifications ou un log. C'est une structure de données qui garantit l'intégrité et l'histoire de la carte.

#### Chronique Évolutive
*   **CE QUE C'EST :** Une interface narrative dans Jetpack Compose qui présente une timeline interactive des événements de la Ruche (cartes forgées, Clashs, Insights), basée sur l'agrégation des `Fil de Provenance`.
*   **CE QUE CE N'EST PAS :** Un journal de débogage pour les développeurs. C'est une fonctionnalité narrative qui raconte la Saga du Butineur.

#### Moteur de Logique Dynamique
*   **CE QUE C'EST :** Le composant système (`WorkflowInterpreter`) qui télécharge, valide et interprète des fichiers de workflow (JSON/YAML) pour modifier le comportement de l'application (ex: règles d'un Saga Clash) sans mise à jour de l'APK.
*   **CE QUE CE N'EST PAS :** Un simple gestionnaire de configuration pour des chaînes de texte. C'est un interpréteur de logique métier.

#### Moteur de Réflexion Local
*   **CE QUE C'EST :** Une tâche de fond (`WorkManager`) qui analyse la base de données locale pour identifier des schémas d'utilisation et générer des `Insights` personnalisés pour le Butineur, tout en restant 100% sur l'appareil.
*   **CE QUE CE N'EST PAS :** Un outil d'analyse ou de tracking qui envoie des données à un serveur. Son fonctionnement est entièrement souverain et privé.

#### Cas d'Utilisation (Use Case)
*   **CE QUE C'EST :** Une classe du Domain Layer qui encapsule une unique règle métier ou une interaction système. C'est le seul point d'entrée des ViewModels vers la logique du domaine, garantissant une architecture propre.
*   **CE QUE CE N'EST PAS :** Une fonction du Repository ou une logique métier placée dans le ViewModel.

#### Workflow Dynamique
*   **CE QUE C'EST :** Un fichier JSON/YAML téléchargeable depuis `kikko.be`, définissant une séquence d’actions conditionnelles (ex : règles de Clash, étapes de forge) exécutées par le `WorkflowInterpreter`.
*   **CE QUE CE N'EST PAS :** Un simple fichier de configuration statique ou un script codé en dur. C’est une logique métier adaptable sans recompilation de l’APK.

---

### Lexique du PRAGMATISTE (Le Gardien du Gameplay)

#### Forge Autonome
*   **CE QUE C'EST :** Le pipeline de tâches de fond, asynchrone et résilient (`WorkManager`), qui transforme un `PollenGrain` en `KnowledgeCard`. Il est conçu pour s'exécuter même si l'application est fermée.
*   **CE QUE CE N'EST PAS :** Un processus instantané. C'est une opération en plusieurs étapes qui s'exécute en arrière-plan sans que l'utilisateur ait à attendre activement.

#### Atelier de Raffinage
*   **CE QUE C'EST :** Un écran interactif dans Jetpack Compose où le Butineur collabore avec le `Gardien Kikkō` pour affiner une `KnowledgeCard`, en lançant un `Tournoi d’IA` ou en modifiant manuellement les données, ajoutant un bloc au `Fil de Provenance`.
*   **CE QUE CE N'EST PAS :** Un simple formulaire d'édition de texte. C'est un processus interactif de co-création structuré.

#### Saga Clash
*   **CE QUE C'EST :** Une mécanique de jeu où des `KnowledgeCard` s'affrontent dans une simulation dont les règles sont dictées par le `Moteur de Logique Dynamique`.
*   **CE QUE CE N'EST PAS :** Un combat en temps réel ou une simple comparaison de chiffres. C'est une simulation tactique où la découverte de synergies entre les cartes est aussi importante que la victoire elle-même.

#### Trusted Package
*   **CE QUE C'EST :** L'objet de données sérialisé utilisé pour le partage P2P. Il contient la `KnowledgeCard` ET son `Fil de Provenance` complet pour garantir l'authenticité et le contexte.
*   **CE QUE CE N'EST PAS :** Juste les données de la carte. C'est un paquet auto-suffisant et vérifiable.

#### Reproduction d'Inférence
*   **CE QUE C'EST :** Un processus système garantissant qu'une inférence IA peut être ré-exécutée avec les mêmes entrées pour produire un résultat identique, assurant l'équité des Saga Clash et la fiabilité du débogage.
*   **CE QUE CE N'EST PAS :** Simplement relancer l'IA. Cela exige un versionnage strict de tous les composants pour garantir la reproductibilité.

#### Tournoi d'IA
*   **CE QUE C'EST :** Une mécanique interactive où le `Gardien Kikkō` génère plusieurs variations de contenu pour une `KnowledgeCard`. Le Butineur sélectionne la meilleure via l'Atelier de Raffinage.
*   **CE QUE CE N'EST PAS :** Une génération de propositions aléatoires. Les variations proposées par l'IA sont contextuelles et guidées par le contenu de la carte et la logique active.

#### Condition de Victoire
*   **CE QUE C'EST :** Le critère spécifique, défini par le `Moteur de Logique Dynamique` pour un `Saga Clash`, qui détermine le gagnant (ex: stat la plus haute, meilleure synergie de tags).
*   **CE QUE CE N'EST PAS :** Toujours "le plus grand nombre gagne".

#### Insight
*   **CE QUE C'EST :** Une suggestion ou une observation générée par le `Moteur de Réflexion Local`, basée sur l’analyse des données locales, présentée au Butineur pour améliorer la forge ou les Clashes.
*   **CE QUE CE N'EST PAS :** Une simple notification ou une alerte système. C’est une proposition intelligente et contextuelle.