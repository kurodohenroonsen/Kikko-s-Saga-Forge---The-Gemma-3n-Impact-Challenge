# Plan Directeur V2 : L'Architecture Finale de la Ruche Kikko
## Forgé par Le Bourdon, après l'Épreuve du Feu du Conseil des Frelons

### Préambule
Ce document est le plan directeur final et faisant autorité pour l'architecture de "Kikko's Saga Forge". Il est le fruit de la confrontation entre le "Rapport de Synthèse V1", la critique rigoureuse du Frelon Architecte et les défis ambitieux du Frelon Visionnaire. Cette architecture n'est plus une proposition, mais un décret. Elle est conçue pour être la fondation d'une cathédrale capable de voyager parmi les étoiles : un système à la fois élégant, robuste, et prêt pour une évolution sans limite.

---

### 1. Le Crédo Architectural (Principes Non Négociables)

1.  **Pureté & Séparation (Décret de l'Architecte) :** L'architecture adhère strictement aux principes de la **Clean Architecture**. Les couches UI, Domaine et Données sont sacro-saintes et leurs dépendances unidirectionnelles (UI → Domaine ← Données).
2.  **Souveraineté de l'Utilisateur :** Le système reste **100% embarqué** pour toutes les opérations critiques, garantissant une confidentialité absolue.
3.  **Évolutivité Malléable (Réponse au Visionnaire) :** Le système est conçu pour être plus que configurable ; il est **malléable**. Sa logique peut être redéfinie à distance.
4.  **Robustesse par Abstraction :** La gestion des erreurs et l'état des opérations sont gérés de manière prévisible via des `Result` et `StateFlow`, garantissant une expérience utilisateur résiliente.

---

### 2. L'Architecture Stratifiée : Le Modèle "Clean MVVM+"

Nous abandonnons toute ambiguïté. L'architecture est une **Clean Architecture** pure, avec le **Domain Layer** au centre, totalement indépendant de l'UI et des détails d'implémentation de la persistance.

*   **UI Layer (100% Jetpack Compose) :**
    *   **Une seule `MainActivity`** gère le cycle de vie global.
    *   La navigation est entièrement gérée par **Jetpack Navigation for Compose**. Fini les Fragments.
    *   Les **Composables** sont "stupides" : ils reçoivent un `UiState` et émettent des événements.

*   **Domain Layer (Pure Kotlin Module) :**
    *   Le cœur de notre logique. Ne contient **aucune dépendance Android**.
    *   **Entities :** Des `data class` pures qui représentent nos concepts métier (`KnowledgeCard`, `PollenGrain`).
    *   **Repositories (Interfaces) :** Des contrats qui définissent *ce que* la couche de données doit faire, pas *comment*.
    *   **Use Cases (Interactors) :** Des classes avec une seule responsabilité publique, encapsulant une action métier unique (ex: `ForgeNewCardUseCase`). Elles sont le seul point d'entrée du ViewModel vers le domaine.

*   **Data Layer :**
    *   **Repositories (Implementations) :** L'implémentation concrète des interfaces du domaine.
    *   **Room Database (`KikkoDatabase`) :** Nous adoptons Room pour une abstraction SQL élégante, typée et sécurisée. Fini le `SQLiteOpenHelper` manuel.
    *   **DAOs (Data Access Objects) :** Interfaces Room pour les requêtes.
    *   **Data Sources :** Abstractions pour les sources de données (ex: `RemoteConfigSource` pour `kikko.be`).

#### **Diagramme de Flux de Dépendances (Clean Architecture)**
```plantuml
@startuml
skinparam rectangle {
    BackgroundColor #FBF5E6
    BorderColor #5D4037
    ArrowColor #D32F2F
}

package "UI Layer (Compose)" {
  rectangle "ViewModel" as VM
  rectangle "Screen" as UI
}

package "Domain Layer (Pure Kotlin)" {
  rectangle "UseCase" as UC
  rectangle "Repository (Interface)" as RepoInterface
  rectangle "Entity" as DomainEntity
}

package "Data Layer" {
  rectangle "Repository (Implementation)" as RepoImpl
  rectangle "Room DAO" as DAO
  rectangle "DB Entity" as DBEntity
}

UI -> VM : Events
VM --> UI : StateFlow<UiState>

VM -> UC : Executes
UC -> RepoInterface : Uses

RepoImpl ..|> RepoInterface : Implements
RepoImpl -> DAO : Uses
DAO -> DBEntity : Manipulates
RepoImpl --> DomainEntity : Maps DB Entity to

note "La flèche de dépendance pointe toujours vers l'intérieur,\nvers le Domain Layer." as N1
@enduml
3. Les Mécanismes de la Vision : Comment nous répondons aux Épreuves
Épreuve 1 : La Malléabilité (Au-delà de la Configuration)
Le ConfigurationManager évolue en un Moteur de Logique Dynamique.
Logique Déclarative : Au lieu de simples prompts, kikko.be pourra fournir des fichiers de workflow (JSON/YAML) décrivant des chaînes d'actions conditionnelles.
Interprète de Workflow : La Ruche intégrera un WorkflowInterpreter qui lira ces fichiers et orchestrera les UseCases appropriés.
Exemple : Un nouveau workflow pourrait définir un "Clash Éclair" où seule la stat "Vitesse" est comparée, ou une "Forge Artistique" qui ne génère qu'une description poétique. Le tout, sans toucher à l'APK.
Épreuve 2 : L'Enrichissement (Au-delà du Partage)
Le provenance.json devient une Chronique Évolutive.
Structure en Blocs : Le provenanceLog ne sera plus un objet, mais un tableau JSON de "Blocs de Forge".
Sédimentation du Savoir : Le premier bloc est l'inférence originelle. Lorsqu'un utilisateur reçoit une carte et la "raffine" (corrige une stat, ajoute une note), un nouveau bloc est ajouté au journal, signé numériquement par sa Ruche.
Visualisation : L'UI affichera l'historique de la carte, permettant de voir qui a ajouté ou corrigé quelle information, créant une véritable généalogie de la connaissance.
Épreuve 3 : La Réflexion (Au-delà de la Forge)
Nous introduisons le Moteur de Réflexion Local.
ReflectionWorker : Une tâche WorkManager périodique et à faible priorité.
Analyse Locale : Ce worker analysera la base de données locale pour des patterns : "Quelles sont les corrections les plus fréquentes de l'utilisateur ?", "Quels types de pollen mènent à des échecs d'identification ?".
Auto-Ajustement : Sur la base de cette analyse, le moteur pourra proposer des ajustements au Butineur : "Je remarque que vous corrigez souvent 'Chêne' en 'Hêtre'. Voulez-vous que je crée un alias local pour améliorer mes prochaines identifications ?". C'est le premier pas vers une IA qui apprend de son partenaire humain.
4. La Stack Technique Finale
Architecture : Clean Architecture, MVVM+
UI : 100% Jetpack Compose & Navigation Compose
Injection de Dépendances : Hilt
Base de Données : Room
Asynchronisme : Kotlin Coroutines (StateFlow, suspend functions)
Tâches de Fond : WorkManager
Réseau : Retrofit
IA Embarquée : MediaPipe LLM Inference API (Gemma 3n)
5. Plan de Refactorisation Priorisé
La transformation de notre prototype en cette architecture d'élite se fera en 4 phases claires :
Phase 1 - Les Fondations :
Mise en place de Hilt pour l'injection de dépendances.
Création de la base de données Room et de ses DAOs.
Mise en place de la Navigation Compose de base.
Phase 2 - La Plomberie des Données :
Migration de la logique SQL des anciens DAOs vers les nouveaux DAOs Room.
Création des Repositories (Interfaces dans le domaine, Implémentations dans la data).
Définition des Entities du domaine et des entités de la base de données, avec leurs mappers.
Phase 3 - Le Cerveau de la Ruche :
Création des Use Cases dans le domaine pour chaque action métier.
Modification des ViewModels pour qu'ils n'appellent plus les Repositories directement, mais les Use Cases.
Phase 4 - La Façade Scintillante :
Réécriture complète de chaque écran (Activity/Fragment) en Composables purs.
Liaison finale du flux de données réactif (StateFlow) entre les ViewModels et les Composables.
Conclusion
Ce Plan Directeur V2 est notre serment. Il est l'union de l'ambition du Visionnaire et de la rigueur de l'Architecte. Il est la carte qui nous guidera à travers la complexité de la refactorisation. En suivant ce plan, nous ne construirons pas seulement une application pour gagner un concours. Nous forgerons le prototype fonctionnel d'une nouvelle ère de l'IA personnelle : une IA digne de confiance.
code
Code
---
**Fichier fourni :** `ARCHITECTURE_CIBLE_V2.md`

**Fichiers restants :** Aucun.

Le plan est gravé. Il est complet, il est robuste, il est ambitieux. Il est à nous.

---
### **Proposition de Quête :**

Maître Butineur, les parchemins sont rangés. Les plans sont tracés. La Forge est froide et silencieuse, attendant le souffle du soufflet et le choc du marteau. Il n'y a plus de place pour la théorie.

*   **Quête A : Lancer la Grande Refactorisation.** C'est la seule voie. Nous devons maintenant transformer ce plan en code. Nous commencerons par la **Phase 1 : Les Fondations**. Je vous fournirai, un par un, les fichiers nécessaires pour mettre en place Hilt, Room, et la navigation Compose. C'est la quête la plus importante, la plus longue, et la plus gratifiante.
*   **Quête B : Discuter d'un Détail du Plan.** Si un aspect de ce plan vous semble encore obscur, nous pouvons prendre le temps de le clarifier avant de commencer.

**Recommandation du Bourdon :**
L'encre est sèche, Maître Butineur. Chaque minute que nous passons à discuter est une minute que nous ne passons pas à construire. La **Quête A** n'est plus une option, c'est notre devoir. Donnez l'ordre, et je préparerai la première pierre de notre nouvelle Ruche : la configuration de Hilt.

L'heure de la forge a sonné.