import json
import re

def extract_lightweight_data(full_data):
    """
    Parcourt les soumissions d'un hackathon et extrait un résumé
    allégé pour chaque projet. (Cette fonction reste inchangée)
    """
    
    tech_keywords = [
        "NVIDIA Jetson", "NVIDIA Riva", "Gemma 3n", "vLLM", "Ollama",
        "FastAPI", "Flask", "Docker", "CUDA", "Vosk", "Web Speech API",
        "Tailscale", "Howler.js"
    ]
    
    lightweight_projects = []

    if 'hackathonWriteUps' not in full_data:
        print("Avertissement : La clé 'hackathonWriteUps' n'a pas été trouvée dans le JSON.")
        return []

    for submission in full_data['hackathonWriteUps']:
        write_up = submission.get('writeUp', {})
        
        project_title = write_up.get('title', 'N/A')
        tagline = write_up.get('subtitle', 'N/A')
        authors = write_up.get('authors', 'N/A')
        kaggle_url = "https://www.kaggle.com" + write_up.get('url', '')
        
        raw_markdown = write_up.get('message', {}).get('rawMarkdown', '')
        summary = raw_markdown.partition('## ')[0].strip().replace('# ', '').strip()
        
        source_code_url, demo_video_url = None, None
        for link in write_up.get('writeUpLinks', []):
            url = link.get('url', '').lower()
            if 'github.com' in url and not source_code_url:
                source_code_url = link.get('url')
            if ('youtube.com' in url or 'youtu.be' in url) and not demo_video_url:
                demo_video_url = link.get('url')
        
        technologies = []
        for tech in tech_keywords:
            if re.search(r'\b' + re.escape(tech) + r'\b', raw_markdown, re.IGNORECASE):
                technologies.append(tech)
        
        lightweight_project = {
            "project_title": project_title,
            "tagline": tagline,
            "authors": authors,
            "summary": summary,
            "technologies": sorted(list(set(technologies))),
            "source_code_url": source_code_url,
            "demo_video_url": demo_video_url,
            "kaggle_url": kaggle_url
        }
        
        lightweight_projects.append(lightweight_project)
        
    return lightweight_projects


def main():
    """
    Fonction principale qui lit, traite et écrit les fichiers JSON,
    avec une gestion d'erreur améliorée.
    """
    input_filename = 'writesupList.json'
    output_filename = 'writesupList_light.json'
    
    try:
        print(f"Lecture du fichier d'entrée : {input_filename}...")
        with open(input_filename, 'r', encoding='utf-8') as f:
            full_data = json.load(f)
            
        print("Traitement des données...")
        lightweight_data = extract_lightweight_data(full_data)
        
        if not lightweight_data:
            print("Aucun projet n'a été extrait. Vérifiez la structure du JSON.")
            return

        print(f"Écriture des données allégées dans : {output_filename}...")
        with open(output_filename, 'w', encoding='utf-8') as f:
            json.dump(lightweight_data, f, indent=4, ensure_ascii=False)
            
        print("\nOpération terminée avec succès !")
        print(f"{len(lightweight_data)} projets ont été traités.")

    except FileNotFoundError:
        print(f"ERREUR : Le fichier '{input_filename}' n'a pas été trouvé.")
        print("Veuillez vous assurer que le fichier existe dans le même répertoire que le script.")

    # --- BLOC DE DÉTECTION D'ERREUR AMÉLIORÉ ---
    except json.JSONDecodeError as e:
        print("-" * 60)
        print(f"ERREUR FATALE : Le fichier '{input_filename}' n'est pas un JSON valide.")
        print("-" * 60)
        print(f"Message d'erreur : {e.msg}")
        print(f"Ligne : {e.lineno}")
        print(f"Colonne : {e.colno}")
        print("-" * 60)
        
        # Afficher la ligne qui pose problème pour aider au débogage
        try:
            with open(input_filename, 'r', encoding='utf-8') as f:
                lines = f.readlines()
                if 0 < e.lineno <= len(lines):
                    faulty_line = lines[e.lineno - 1].strip()
                    print("Voici la ligne qui pose problème :\n")
                    print(f"Ligne {e.lineno}: {faulty_line}\n")
                    # Ajoute un pointeur pour indiquer la colonne exacte
                    pointer = ' ' * (len(f"Ligne {e.lineno}: ") + e.colno - 1) + '^'
                    print(pointer)
                    print("\nCONSEIL : Vérifiez les virgules, les guillemets et les accolades autour de cette zone.")
        except Exception as read_error:
            print(f"Impossible de lire la ligne exacte du fichier en raison de l'erreur : {read_error}")
        print("-" * 60)
        
    except Exception as e:
        print(f"Une erreur inattendue est survenue : {e}")

# Exécuter la fonction principale
if __name__ == "__main__":
    main()