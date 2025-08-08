import os
import sys

def consolidate_android_project(project_path, output_file):
    """
    Parcourt un projet Android et consolide les fichiers pertinents 
    (code source, ressources, scripts gradle) en un seul fichier texte.
    """
    # Fichiers et extensions que nous voulons inclure
    relevant_extensions = ('.kt', '.xml')
    relevant_filenames = (
        'build.gradle', 
        'build.gradle.kts', 
        'settings.gradle', 
        'settings.gradle.kts', 
        'proguard-rules.pro',
        '.gitattributes' # Inclure les attributs git peut être utile
    )
    
    # Dossiers à ignorer complètement
    excluded_dirs = ('.git', '.idea', 'build', '.gradle', 'captures', 'drawable')

    print(f"Démarrage de la consolidation du projet : {project_path}")
    
    found_files = []

    # Première passe : trouver tous les fichiers pertinents
    for root, dirs, files in os.walk(project_path, topdown=True):
        # Exclure les dossiers non pertinents de la recherche
        dirs[:] = [d for d in dirs if d not in excluded_dirs]
        
        for file_name in files:
            if file_name.endswith(relevant_extensions) or file_name in relevant_filenames:
                file_path = os.path.join(root, file_name)
                found_files.append(file_path)

    print(f"{len(found_files)} fichiers pertinents trouvés. Écriture dans {output_file}...")

    # Deuxième passe : écrire les fichiers dans le fichier de sortie
    try:
        with open(output_file, 'w', encoding='utf-8') as outfile:
            for file_path in sorted(found_files): # Trier les fichiers pour un ordre cohérent
                relative_path = os.path.relpath(file_path, project_path)
                
                # Écrire un en-tête clair pour chaque fichier
                outfile.write(f"\n\n--- START OF FILE {relative_path} ---\n\n")
                
                try:
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as infile:
                        content = infile.read()
                        outfile.write(content)
                except Exception as e:
                    outfile.write(f"Erreur de lecture du fichier : {e}\n")
                
                # Écrire un pied de page clair
                outfile.write(f"\n\n--- END OF FILE {relative_path} ---\n")
        
        print(f"Consolidation terminée. Le contexte du projet est sauvegardé dans : {output_file}")

    except IOError as e:
        print(f"ERREUR CRITIQUE : Impossible d'écrire dans le fichier de sortie : {output_file}")
        print(f"Détail de l'erreur : {e}")


# --- Point d'entrée du script ---
if __name__ == "__main__":
    # Le chemin de votre projet sur votre Mac
    project_root_path = "Android"
    
    # --- MODIFICATION ---
    # Déterminer le chemin absolu du répertoire où se trouve ce script
    # '__file__' est une variable spéciale qui contient le chemin du script en cours d'exécution.
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Nom du fichier de sortie
    output_filename = "AndroidCodeInOneFile.txt"

    # Construire le chemin de sortie complet pour que le fichier soit créé à côté du script
    output_full_path = os.path.join(script_dir, output_filename)
    
    # --- FIN DE LA MODIFICATION ---

    if os.path.isdir(project_root_path):
        consolidate_android_project(project_root_path, output_full_path)
    else:
        print(f"ERREUR : Le chemin spécifié n'existe pas ou n'est pas un dossier : {project_root_path}")