# Inventaire Exhaustif des Cas d'Utilisation - Kikko's Saga Forge
## Basé sur le Plan Directeur V2 - Synthèse Finale du Conseil des Frelons

### Introduction
Ce document constitue l'inventaire fonctionnel exhaustif découlant du "Plan Directeur Final V2". Il a pour vocation de décomposer la vision architecturale en une liste granulaire de tous les cas d'utilisation possibles, servant de spécification fondamentale pour la conception UML et le backlog de développement. L'analyse est structurée selon trois perspectives complémentaires : celle du Visionnaire, qui cartographie le parcours de l'utilisateur ("le Butineur") ; celle de l'Architecte, qui décrit les processus autonomes du Système ; et celle du Pragmatiste, qui anticipe et spécifie la gestion des erreurs et des cas limites. Ensemble, ces trois inventaires forment une carte complète et précise des capacités du système.

---

### Cas d'Utilisation du VISIONNAIRE (L'Expérience du Butineur)
Cette section détaille chaque interaction initiée par le Butineur, cartographiant l'intégralité de son parcours, depuis le premier lancement jusqu'aux interactions les plus avancées avec l'écosystème.

| UC-ID | Nom du Cas d'Utilisation | Acteur(s) | Description Brève |
|---|---|---|---|
| **Intégration & Première Utilisation** |
| UC-U01 | Premier Lancement et Configuration Initiale | Butineur | Le Butineur ouvre l'application, navigue dans le tutoriel et gère les demandes de permissions essentielles (caméra, stockage). |
| UC-U02 | Créer et Gérer son Profil Butineur | Butineur | Le Butineur configure son profil, choisit ses domaines d'intérêt et ses préférences d'IA. |
| **Forge de Connaissances** |
| UC-U03 | Capturer du Pollen (Données Brutes) | Butineur | Le Butineur capture des données (image, texte, audio) via l'interface multimodale pour créer un `PollenGrain`. |
| UC-U04 | Lancer la Forge Autonome | Butineur | Le Butineur sauvegarde le `PollenGrain`, ce qui déclenche la chaîne de `WorkManager` pour sa transformation en `KnowledgeCard`. |
| UC-U05 | Raffiner une Connaissance (Atelier) | Butineur | Le Butineur lance des "tournois d'IA" sur une propriété de carte, compare les résultats et valide le meilleur pour améliorer la carte. |
| **Gestion de la Collection (La Ruche)** |
| UC-U06 | Consulter sa Collection de Cartes | Butineur | Le Butineur parcourt, filtre, trie et recherche parmi ses `KnowledgeCard` dans sa Ruche personnelle. |
| UC-U07 | Consulter le Détail d'une Carte | Butineur | L'utilisateur ouvre une carte pour voir son contenu complet (stats, description, quiz). |
| UC-U08 | Consulter la Chronique Évolutive d'une Carte | Butineur | Le Butineur visualise l'historique complet d'une carte (`provenanceLog`) pour voir sa création et ses raffinements successifs. |
| **Fonctionnalités Interactives & Sociales** |
| UC-U09 | Participer à un "Saga Clash" | Butineur | L'utilisateur engage un duel ludique avec un autre joueur en local (Nearby Connections), basé sur les stats des cartes. |
| UC-U10 | Partager une Carte en P2P | Butineur | L'utilisateur envoie une `KnowledgeCard` (avec sa provenance) à un autre utilisateur via une technologie P2P (ex: WebTorrent). |
| UC-U11 | Importer une Carte P2P | Butineur | Le Butineur reçoit et importe une carte partagée, qui est validée et ajoutée à sa collection. |
| **Personnalisation Avancée** |
| UC-U12 | Mettre à Jour la Logique de l'IA (Dynamique) | Butineur | L'utilisateur active un nouveau workflow téléchargé depuis `kikko.be` pour modifier le comportement de l'IA (ex: "Forge Artistique"). |
| UC-U13 | Accepter une Suggestion du Moteur de Réflexion | Butineur | L'utilisateur reçoit une suggestion personnalisée (ex: "créer un alias") et choisit de l'appliquer, affinant son IA locale. |

---

### Cas d'Utilisation de L'ARCHITECTE (Les Opérations du Système)
Cette section décrit les opérations autonomes exécutées par le Système, formant le cœur fonctionnel et intelligent de la Ruche.

| UC-ID | Nom du Cas d'Utilisation | Acteur(s) | Description Brève |
|---|---|---|---|
| **Initialisation & Maintenance** |
| UC-S01 | Initialiser l'Application | Système | Au démarrage, le Système initialise Hilt, Room, la Navigation Compose et charge la configuration locale. |
| UC-S02 | Appliquer une Migration de Base de Données | Système | Lors d'une mise à jour, le Système exécute les migrations Room pour faire évoluer le schéma de la BDD sans perte de données. |
| UC-S03 | Nettoyer les Données Obsolètes | Système | Une tâche périodique supprime les fichiers temporaires, les `PollenGrain` échoués ou les caches invalides pour maintenir la santé du système. |
| **Forge Autonome (WorkManager)** |
| UC-S04 | Exécuter la Chaîne de Forge | Système | Le `WorkManager` orchestre la séquence de workers (`Identification`, `Description`, etc.) pour transformer un `PollenGrain`. |
| UC-S05 | Effectuer une Inférence IA Embarquée | Système | Le Système utilise Gemma 3n (via `ForgeLlmHelper`) pour exécuter une inférence dans le cadre d'un `Worker`. |
| UC-S06 | Persister l'État de la Forge | Système | Chaque `Worker` met à jour le statut du `PollenGrain` et les données de la `KnowledgeCard` dans la base Room. |
| **Logique Dynamique & Réflexion** |
| UC-S07 | Mettre à Jour la Logique Dynamique | Système | Le Système télécharge, valide (checksum) et sauvegarde les nouveaux fichiers de workflow/prompts depuis `kikko.be`. |
| UC-S08 | Interpréter un Workflow Dynamique | Système | Le `WorkflowInterpreter` lit un fichier de logique et orchestre l'appel des `Use Cases` correspondants. |
| UC-S09 | Exécuter le Moteur de Réflexion Local | Système | Le `ReflectionWorker` analyse périodiquement la BDD pour identifier des patterns d'utilisation ou de correction. |
| UC-S10 | Générer une Suggestion d'Ajustement | Système | Suite à l'analyse, le `ReflectionWorker` génère une proposition concrète d'amélioration et la stocke pour l'utilisateur. |
| **Partage & Sécurité** |
| UC-S11 | Gérer une Session de Partage P2P | Système | Le Système gère la découverte de pairs, la connexion, le transfert de données et la déconnexion. |
| UC-S12 | Gérer la Chronique Évolutive (Provenance) | Système | Le Système ajoute des blocs signés au `provenanceLog` d'une carte lors de sa création ou de son raffinement par un utilisateur. |
| UC-S13 | Valider l'Intégrité d'une Carte Reçue | Système | À la réception d'une carte P2P, le Système vérifie la signature cryptographique de sa chronique pour garantir son authenticité. |

---

### Cas d'Utilisation du PRAGMATISTE (La Gestion de l'Imprévu)
Cette section spécifie comment le système doit réagir face aux erreurs et aux cas limites, garantissant sa robustesse et sa fiabilité.

| UC-ID | Nom du Cas d'Utilisation | Acteur(s) | Description Brève |
|---|---|---|---|
| **Erreurs de Données & Persistance** |
| UC-E01 | Gérer un Échec de Migration de Base de Données | Système, Butineur | La migration Room échoue. Le Système bloque l'accès, notifie l'utilisateur de l'erreur critique et propose une restauration/réinitialisation. |
| UC-E02 | Gérer une Corruption de la Base de Données | Système, Butineur | Une corruption est détectée. Le Système tente d'isoler les données, informe l'utilisateur et se met en mode de fonctionnement dégradé. |
| UC-E03 | Gérer un Manque d'Espace de Stockage | Système, Butineur | L'écriture échoue. Le Système suspend les opérations de forge, notifie l'utilisateur et l'invite à libérer de l'espace. |
| **Erreurs de la Forge & IA** |
| UC-E04 | Gérer un Échec d'Inférence IA | Système, Butineur | Une inférence Gemma 3n échoue après plusieurs tentatives. Le `Worker` marque le `PollenGrain` comme `FAILED` et le Butineur est notifié. |
| UC-E05 | Gérer une Interruption de la Chaîne de Forge | Système | L'application est fermée durant une forge. `WorkManager` garantit que la chaîne reprendra à la prochaine opportunité. |
| **Erreurs de Connectivité & Configuration** |
| UC-E06 | Gérer une Perte de Connexion | Système | Le téléchargement d'un workflow échoue. Le Système utilise la version en cache et retentera plus tard silencieusement. |
| UC-E07 | Gérer un Fichier de Configuration Invalide | Système | Un workflow téléchargé est corrompu (checksum invalide). Il est rejeté et l'ancienne version est conservée. |
| UC-E08 | Gérer un Échec de Partage P2P | Système, Butineur | Le transfert P2P est interrompu. Les deux utilisateurs sont notifiés de l'échec et peuvent réessayer. |
| **Erreurs d'Environnement** |
| UC-E09 | Gérer une Permission Essentielle Refusée | Système, Butineur | Le Butineur refuse une permission (ex: caméra). La fonctionnalité dépendante est désactivée et un message explicatif est affiché. |
| UC-E10 | Gérer une Batterie Faible | Système | Le Système reporte automatiquement les tâches de fond énergivores (`WorkManager`) jusqu'à ce que l'appareil soit en charge. |