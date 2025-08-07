# Rapport de Synthèse V1 : Plan Directeur de la Ruche Kikko
## Forgé par Le Bourdon, Synthétiseur du Conseil des Frelons

### Préambule
Ce document est la première synthèse architecturale pour le projet "Kikko's Saga Forge". Il s'inspire de la vision audacieuse du Frelon Visionnaire et la tempère avec les exigences de maintenabilité de l'Architecte et les contraintes de performance du Pragmatiste. Cet artefact est destiné à être critiqué, affiné et validé par le Conseil pour devenir notre plan de refactorisation final.

---

### 1. Principes Architecturaux Fondamentaux

L'architecture de Kikko reposera sur trois piliers non négociables :

1.  **100% Embarqué & Souveraineté de l'Utilisateur :** Toute la logique métier, la gestion des données et l'inférence IA critiques doivent s'exécuter sur l'appareil de l'utilisateur. La confidentialité est absolue.
2.  **Évolutivité par Configuration :** Le cœur du comportement de l'application (logique IA, règles de jeu) doit être piloté par des fichiers de configuration locaux qui peuvent être mis à jour à distance. L'application doit pouvoir "apprendre" de nouveaux comportements sans nécessiter une mise à jour de l'APK.
3.  **Robustesse & Résilience :** L'architecture doit être conçue pour gérer les échecs (perte de réseau, inférence IA qui échoue) de manière gracieuse, en garantissant qu'aucune donnée utilisateur n'est perdue.

---

### 2. Architecture Globale : MVVM+ avec Couche de Données Robuste

Nous adoptons une architecture **MVVM (Model-View-ViewModel)** claire, renforcée par un **Repository Pattern** pour une séparation nette des responsabilités.

*   **UI Layer (View) :**
    *   Composée d'**Activities** (pour les écrans principaux) et de **Fragments** (pour les UI complexes ou réutilisables).
    *   La vue est "stupide" : elle ne fait qu'observer les changements d'état exposés par le ViewModel et lui transmet les actions de l'utilisateur.
    *   Utilise **StateFlow** pour un flux de données réactif et conscient du cycle de vie.

*   **Presentation Logic Layer (ViewModel) :**
    *   Chaque écran principal aura son propre `ViewModel`.
    *   Il contient la logique de présentation et gère l'état de l'UI (`UiState`).
    *   Il ne communique JAMAIS directement avec la base de données ou les sources de données distantes. Il passe exclusivement par le `Repository`.

*   **Data Layer :**
    *   **Repository :** C'est la seule source de vérité pour les ViewModels. Il abstrait l'origine des données (base de données locale, cache, futur serveur distant).
    *   **DAO (Data Access Objects) :** Des classes simples responsables de l'encapsulation des requêtes SQL brutes pour une entité spécifique (ex: `CardDao`, `PollenGrainDao`).
    *   **DatabaseHelper (`SQLiteOpenHelper`) :** Notre point d'accès unique et contrôlé à la base de données SQLite native.

#### **Diagramme de Flux de Données (MVVM + Repository)**

```plantuml
@startuml
skinparam rectangle {
    BackgroundColor #FBF5E6
    BorderColor #5D4037
    ArrowColor #5D4037
}
skinparam cloud {
    BackgroundColor #A9D6E5
}

package "UI Layer" {
  rectangle "Activity / Fragment" as View
  rectangle "ViewModel" as VM
}

package "Data Layer" {
  rectangle "Repository" as Repo
  rectangle "DAO" as DAO
  database "SQLite (DatabaseHelper)" as DB
}

cloud "kikko.be (futur)" as Remote

View -> VM : User Action
VM -> Repo : Request Data
Repo -> DAO : Query Local Data
DAO -> DB : Execute SQL
DB --> DAO : Return Cursor
DAO --> Repo : Return Data Object
Repo --> VM : Return Data
VM -> View : Update StateFlow<UiState>

Repo --> Remote : Fetch Updates (future)
@enduml