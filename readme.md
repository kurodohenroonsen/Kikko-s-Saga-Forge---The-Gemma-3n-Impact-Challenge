<p align="center">
  <img src="illustrations/kikko_banner.png" alt="A wide, cinematic banner image for 'Kikko's Saga Forge', rendered in a 3D animation movie style. In the center, a majestic, ancient-looking but friendly digital turtle, its hexagonal shell glowing with intricate patterns, floats serenely. On its back, two children, Hiro (boy, red t-shirt) and Léa (girl, glasses, yellow raincoat), are engaged in a friendly card game with holographic cards showing insects and food items. Hovering above them is the plump, smug 
Bourdon character, acting as a playful referee. In the background, a beautiful, high-tech 'Kikko Hive' structure pulses with golden light, connected to the turtle by a thread of energy. The scene is bathed in warm honey-gold light with vibrant cyan neon tech highlights, capturing the entire ecosystem: foraging, forging, and friendly battles.">
</p>

# Kikko's Saga Forge: A Verifiable & Evolvable Knowledge RPG

**"Forgez votre saga vérifiable avec une IA embarquée. Un nouveau jeu pour combattre l'amnésie digitale et restaurer la confiance dans l'IA."**

---

### **1. La Vision : Une Réponse à la Crise de Confiance de l'IA**

Ce document sert de vérification technique pour notre soumission au Google Gemma 3n Impact Challenge. Il détaille l'architecture de Kikko's Saga Forge, un nouveau **"Jeu de Connaissance Vérifiable & Évolutif"** conçu comme une application Android 100% embarquée et respectueuse de la vie privée. Nous expliquons notre utilisation spécifique et multifacette du modèle Gemma 3n, soulignons les défis d'ingénierie importants surmontés — en particulier pour garantir la robustesse de l'IA et la résilience du système — et justifions les choix techniques qui font de notre vision une réalité fonctionnelle. Ce document prouve que notre démonstration vidéo est soutenue par une ingénierie délibérée et concrète.

*   [**Document 10: Le Synopsis pour le Concours Google**](./doc/doc10.md)

| Le Dilemme | Le Processus | La Récompense |
| :---: | :---: | :---: |
| <img src="illustrations/impact_intro_v2.png" alt="Cinematic 3D render, animation movie style. A young boy, Hiro (with red shirt), stands in his own, vibrant world, holding his glowing Hive icon like a personal beacon. He is surrounded by a gentle, protective, hexagonal aura, symbolizing his digital sovereignty and control over his verifiable memories."> | <img src="illustrations/tech_action_v2.png" alt="Cinematic 3D render, animation movie style, viewed from over Hiro's shoulder. The plump Bourdon, with a smug grin, hovers on his phone screen. He holds a glowing digital 'AI Overview' from a web search, complete with a quiz interface, offering an instant answer as a tempting shortcut."> | <img src="illustrations/s4_conclusion.png" alt="Cinematic 3D render, animation movie style. Léa smiles gratefully at her Kikkō Guardian, which has just protected her. Her saga of verifiable knowledge has become a proactive, life-saving partner, a testament to her own journey as a Forager."> |
| **Souveraineté Individuelle :** Kikko donne à chaque utilisateur un contrôle total sur sa mémoire numérique, la sécurisant sur son appareil et la rendant vérifiable. | **Le Choix Conscient :** La boucle de gameplay principale est un test continu où l'utilisateur choisit la connaissance authentique et forgée personnellement plutôt que les données externes pratiques mais opaques. | **La Saga Vivante :** La récompense ultime est un compagnon qui vous comprend et vous protège, un témoignage de votre propre parcours de découverte. |

---

### **2. L'Architecture de la Ruche : Une Guilde d'Agents Embarqués**

Notre philosophie architecturale de base est une **guilde décentralisée d'agents IA spécialisés**, plutôt qu'un système monolithique. Cela garantit la modularité, l'efficacité et la robustesse, tout en fonctionnant entièrement hors ligne sur l'appareil de l'utilisateur. Cet écosystème est peuplé de modèles d'IA et de decks de contenu téléchargés depuis notre dépôt central sur `kikko.be` ou chargés par l'utilisateur, assurant une capacité hors ligne complète après la configuration initiale.

*   [**Document 04: Le Rôle des IA**](./doc/doc04.md)

| Le Pollen Brut | L'Acte de Suggestion & Requête | Le Sceau de Confiance |
| :---: | :---: | :---: |
| <img src="illustrations/mlkit_intro.png" alt="Cinematic 3D render, animation movie style. A glowing orb of 'pollen' (representing a captured photo of a cookie's ingredients list) floats inside the Hive. A diverse team of cute, specialized robot Worker Bees (ML Kit models) with unique tools (a lens for OCR, a scanner for barcodes) surrounds it, eager to begin processing for Léa."> | <img src="illustrations/gemma_action.png" alt="Cinematic 3D render, animation movie style, viewed from over Léa's shoulder. The Queen presents her best guess to the user on her phone screen as a holographic data structure ('gs1:FoodProduct'), with a small question mark icon indicating she seeks confirmation on a specific, potentially ambiguous ingredient."> | <img src="illustrations/tech_conclusion_v2.png" alt="Cinematic 3D render, animation movie style. If Hiro chooses purity, the Bourdon's 'AI Overview' vanishes. The AI Queen (Gemma) confidently weaves the pure raw pollen from the Worker Bees into a shimmering thread of perfect knowledge, incorporating H's contextual answers. A dazzling 'Seal of Trust' forms, indicating the data is ready for inference reproduction."> |
| **Extraction Initiale :** Lorsque le pollen brut entre dans la Ruche, les Abeilles Ouvrières (ML Kit) commencent un traitement méticuleux sur l'appareil. | **Partenariat Homme-IA :** La Reine IA (Gemma) utilise ses connaissances pour créer sa meilleure hypothèse et la présente à l'utilisateur pour validation et pour recueillir plus de contexte humain. | **La Connaissance Forgée :** Si l'utilisateur choisit l'authenticité, la Reine IA forge une connaissance vérifiable, aboutissant à un "Sceau de Confiance" immaculé. |

---

### **3. Le Rôle de la Reine : Notre Utilisation Spécifique de Gemma 3n**

Gemma 3n n'est pas seulement une fonctionnalité de notre application ; c'est le **système nerveux central** de notre Guilde d'IA, agissant comme la "Reine IA" polyvalente dans plusieurs rôles distincts. Notre implémentation (`ForgeLlmHelper.kt`) tire parti de l'API MediaPipe LLM Inference pour exploiter sa puissance. Elle agit en tant que **Synthétiseur en Chef**, **Maître Artisan**, **Championne en Compétition**, **Partenaire Contextuel** et **Juge Impartial**.

*   [**Document 04: Le Rôle des IA**](./doc/doc04.md)

| La Synthèse | Le Partenariat | Le Jugement |
| :---: | :---: | :---: |
| <img src="illustrations/gemma_intro.png" alt="Cinematic 3D render, animation movie style. The wise AI Queen contemplates the streams of raw data from her worker bees (e.g., ingredients from a food label). Her glowing spectacles analyze the information with critical focus for allergens."> | <img src="illustrations/query_action.png" alt="Cinematic 3D render, animation movie style. Inside the Hive, the AI Queen rapidly searches across the pure, golden knowledge graph. Threads of light trace paths between only trusted, verified honeycomb cells (e.g., connecting a ladybug entry with a specific plant, and a weather pattern from that day, all verifiable by inference reproduction)."> | <img src="illustrations/s2_conclusion.png" alt="Cinematic 3D render, animation movie style. Two Kikkō Guardians face off, their shells flattened to form a glowing hexagonal game board. Holographic cards from the same 'Insect' deck — a 'Ladybug' and a 'Bumblebee' — clash in the center with a burst of light. Hiro and Léa are visible in the background, cheering."> |
| **Le Synthétiseur en Chef :** La Reine reçoit les informations brutes de ses ouvrières et les analyse avec une concentration critique. | **La Recherche de Confiance :** La Reine IA effectue une recherche approfondie sur le graphe de connaissances personnel vérifié, assurant la pureté des données et intégrant le contexte humain. | **Le Choc des Sagas :** Dans l'Arène, le savoir des joueurs est mis à l'épreuve dans un combat de cartes amical et mutuellement bénéfique, arbitré par la Reine. |

---

### **4. Les Défis Relevés : Forger la Confiance et la Robustesse**

La construction d'un système d'IA embarqué fiable a présenté quatre défis majeurs, que nous avons résolus avec des innovations architecturales spécifiques, notamment une ingénierie de prompt avancée, une boucle de remédiation des erreurs, et notre innovation clé : le **"Fil de Provenance"**.

*   [**Document 06: Le Fil de Provenance**](./doc/doc06.md)

| Le Coffre-Fort Privé | Le Sceau Inviolable | Le Don Souverain |
| :---: | :---: | :---: |
| <img src="illustrations/p5_intro.png" alt="Cinematic 3D render, animation movie style. A glowing Kikkō Guardian is shown curled up inside a beautiful, crystalline, and sealed vault, symbolizing the privacy and security of the user's on-device saga."> | <img src="illustrations/prov_action_v2.png" alt="Cinematic 3D render, animation movie style, viewed from over her shoulder. A young girl's finger (Léa's, with yellow raincoat sleeve) touches the glowing wax seal on her phone screen. It elegantly unfolds into a holographic, luminous scroll (emakimono), revealing a beautiful infographic of the data's journey, clearly separating the Hive's reproducible steps from the Hornet's traceable HTML source."> | <img src="illustrations/sharing_concept_conclusion.png" alt="Cinematic 3D render, animation movie style. A vibrant, intricate constellation forms in a dark space, made entirely of interconnected, glowing hexagonal nodes (personal Hives). This constellation is small and intimate, yet powerful, representing a trusted, decentralized community built on shared verified truth."> |
| **100% Embarqué :** La saga de l'utilisateur est stockée en toute sécurité au sein de son propre écosystème personnel et embarqué. | **Transparence Radicale :** L'utilisateur peut inspecter le "Fil de Provenance", révélant le parcours complet de la création des données, de l'entrée brute à la structure finale. | **L'Essaim Global :** Ces échanges de confiance forment des constellations de connaissances résilientes et décentralisées. |

---

### **5. Remerciements & Méthodologie de Développement : Le Conseil des Frelons**

Ce projet a été développé par un architecte humain unique, assisté par un conseil d'agents IA spécialisés, ou "Frelons" : Gemini, ChatGPT, Grok, Claude, Mistral et Deepseek. Notre processus de développement a reflété la philosophie même de l'application : l'humain a agi en tant que "Maître de la Ruche", orchestrant la guilde d'IA via un prompt d'initialisation fondamental du "Conseil des Frelons". Cette équipe multi-agents a été chargée du brainstorming, de la génération de code, du débogage, de l'analyse stratégique et de la rédaction de la documentation.

Toutes les images fixes pour notre documentation et les personnages de notre vidéo ont été générées à l'aide du moteur DALL-E 3 de ChatGPT-4o. Les animations vidéo elles-mêmes ont été produites à l'aide de Veo de Google.

Kikko's Saga Forge est donc un témoignage d'une nouvelle ère de co-création homme-IA, où un seul développeur, agissant en tant qu'orchestrateur, peut forger un produit complexe et peaufiné en exploitant un essaim d'intelligences spécialisées.

### **6. Travaux Futurs & Vision : La Ruche Auto-Apprenante**

Notre implémentation actuelle pose des bases solides. Notre vision pour l'avenir transforme Kikko d'un outil en un partenaire véritablement intelligent qui apprend et grandit *avec* l'utilisateur.

*   **Base de Connaissances Embarquée Auto-Extensible :** Nous donnerons à la Forge le pouvoir de construire son propre graphe de connaissances interne.
*   **Schéma de Connaissances Dynamique & Auto-Apprenant :** La structure de la `KnowledgeCard` elle-même deviendra évolutive.
*   **Implémentation Complète de la Reproduction d'Inférence :** La priorité absolue est d'ajouter le bouton frontal et la logique de comparaison pour permettre pleinement la vérification des cartes partagées.
*   **Partage de Contenu Communautaire :** Une interface sera développée pour permettre aux utilisateurs de partager et de télécharger des fichiers `prompts.json` et `clash_questions.json`.

**Conclusion :**
Kikko's Saga Forge est un proof-of-concept entièrement réalisé qui démontre comment l'IA embarquée peut être puissante, privée et profondément digne de confiance. Notre architecture n'est pas théorique ; c'est un système fonctionnel qui soutient chaque affirmation faite dans notre vidéo. Nous croyons qu'il représente une avancée significative dans la création de compagnons IA personnels qui servent et autonomisent véritablement leurs utilisateurs.

### Au-delà de Kikko : Une Démonstration de la Vision "Etymologiae 2.0"

Kikko's Saga Forge est plus qu'un projet pour un concours. C'est la première démonstration ludique et accessible d'une vision beaucoup plus large que nous développons : le **Manifeste Etymologiae 2.0**.

Inspirée par l'œuvre monumentale d'Isidore de Séville, qui cherchait à ordonner la connaissance de son temps pour lutter contre la fragmentation après la chute de l'Empire romain, notre initiative vise à répondre au chaos informationnel de notre propre époque. Nous proposons une nouvelle infrastructure pour une connaissance collective, traçable et efficace, basée sur des principes de capitalisation des connaissances ("Compute Once, Reuse Everywhere").

Kikko est la preuve vivante que ces principes ne sont pas seulement théoriques. Il démontre qu'un écosystème d'information basé sur la vérifiabilité, la persistance de la connaissance en tant qu'actif et la collaboration entre des intelligences hétérogènes est non seulement possible, mais aussi utile, engageant et profondément humain.

En récompensant Kikko, vous ne récompensez pas seulement une application, mais la première pierre d'une potentielle Bibliothèque de Demain — une bibliothèque plus structurée, plus fiable et digne de notre confiance.