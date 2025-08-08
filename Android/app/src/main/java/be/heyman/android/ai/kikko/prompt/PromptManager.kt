package be.heyman.android.ai.kikko.prompt

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.MissingFormatArgumentException

/**
 * Singleton pour gérer les prompts de l'IA.
 * Charge, met en cache et fournit les prompts depuis un fichier JSON interne,
 * permettant leur modification, import, export et restauration.
 */
object PromptManager {

    // BOURDON'S AGGRESSIVE LOGGING: Un TAG dédié et facile à filtrer.
    private const val TAG = "PromptManagerTrace"
    private const val PROMPTS_FILENAME = "prompts.json"
    private val promptsCache = mutableMapOf<String, String>()
    private var isInitialized = false

    /**
     * Initialise le gestionnaire. Doit être appelé une seule fois au démarrage de l'application.
     * Vérifie si le fichier de prompts personnalisés existe, sinon le copie depuis les assets.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return

            val internalFile = File(context.filesDir, PROMPTS_FILENAME)
            if (!internalFile.exists()) {
                Log.i(TAG, "Le fichier de prompts n'existe pas. Copie depuis les assets.")
                try {
                    context.assets.open(PROMPTS_FILENAME).use { inputStream ->
                        FileOutputStream(internalFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Erreur lors de la copie des prompts depuis les assets.", e)
                    return // L'initialisation a échoué
                }
            }
            loadPromptsFromFile(internalFile)
            isInitialized = true
        }
    }

    /**
     * Récupère un prompt formaté depuis le cache.
     * @param key La clé du prompt (ex: "forge_identification").
     * @param formatArgs Les arguments pour le formatage de la chaîne.
     * @return Le prompt formaté, ou une chaîne d'erreur si non trouvé.
     */
    fun getPrompt(key: String, vararg formatArgs: Any?): String {
        // BOURDON'S AGGRESSIVE LOGGING: Log de la demande initiale.
        Log.d(TAG, ">>> Demande de prompt reçue pour la clé: '$key'")

        val rawPrompt = promptsCache[key]
        if (rawPrompt == null) {
            Log.e(TAG, "!!! ÉCHEC: Clé de prompt introuvable: '$key'")
            return "ERREUR: Prompt '$key' non trouvé."
        }

        // BOURDON'S AGGRESSIVE LOGGING: Affiche le prompt brut et les arguments.
        Log.d(TAG, "    Prompt brut trouvé:\n--- START RAW PROMPT ---\n$rawPrompt\n--- END RAW PROMPT ---")
        Log.d(TAG, "    Arguments reçus (${formatArgs.size}): ${formatArgs.joinToString { it.toString().take(100) + "..." }}")

        return try {
            val formattedPrompt = String.format(rawPrompt, *formatArgs)
            Log.d(TAG, "<<< Formatage réussi. Prompt final généré.")
            formattedPrompt
        } catch (e: MissingFormatArgumentException) {
            // BOURDON'S AGGRESSIVE LOGGING: Affiche une erreur détaillée en cas d'échec.
            val expectedArgs = "%[\\d$]*s".toRegex().findAll(rawPrompt).count()
            Log.e(TAG, "!!! ÉCHEC FATAL DE FORMATAGE pour le prompt '$key' !!!")
            Log.e(TAG, "    Arguments ATTENDUS (basé sur les '%s'): $expectedArgs")
            Log.e(TAG, "    Arguments REÇUS: ${formatArgs.size}")
            Log.e(TAG, "    Exception: ", e)
            "ERREUR: Le prompt '$key' a échoué au formatage. Attendu: $expectedArgs, Reçu: ${formatArgs.size}."
        }
    }

    /**
     * Retourne une copie de tous les prompts actuellement chargés.
     */
    fun getAllPrompts(): Map<String, String> = promptsCache.toMap()

    /**
     * Sauvegarde un nouvel ensemble de prompts dans le fichier interne et met à jour le cache.
     */
    suspend fun savePrompts(context: Context, updatedPrompts: Map<String, String>) = withContext(Dispatchers.IO) {
        promptsCache.clear()
        promptsCache.putAll(updatedPrompts)
        val internalFile = File(context.filesDir, PROMPTS_FILENAME)
        try {
            internalFile.writeText(Gson().toJson(updatedPrompts))
            Log.i(TAG, "Prompts sauvegardés avec succès dans le fichier interne.")
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors de la sauvegarde des prompts.", e)
        }
    }

    /**
     * Restaure les prompts à leur version d'origine en les recopiant depuis les assets.
     */
    suspend fun restoreDefaults(context: Context) = withContext(Dispatchers.IO) {
        val internalFile = File(context.filesDir, PROMPTS_FILENAME)
        try {
            context.assets.open(PROMPTS_FILENAME).use { inputStream ->
                FileOutputStream(internalFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            loadPromptsFromFile(internalFile)
            Log.i(TAG, "Prompts restaurés aux valeurs par défaut depuis les assets.")
        } catch (e: IOException) {
            Log.e(TAG, "Erreur lors de la restauration des prompts.", e)
        }
    }

    /**
     * Exporte le fichier de prompts actuel vers un emplacement partageable.
     * @return L'Uri du fichier exporté, ou null en cas d'erreur.
     */
    suspend fun exportPrompts(context: Context): Uri? = withContext(Dispatchers.IO) {
        val sourceFile = File(context.filesDir, PROMPTS_FILENAME)
        if (!sourceFile.exists()) return@withContext null

        val exportDir = File(context.cacheDir, "exports")
        if (!exportDir.exists()) exportDir.mkdirs()
        val exportFile = File(exportDir, "kikko_prompts_export.json")

        return@withContext try {
            sourceFile.copyTo(exportFile, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.provider", exportFile)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'exportation des prompts.", e)
            null
        }
    }

    /**
     * Importe un set de prompts depuis une Uri et remplace le set actuel.
     * @return true en cas de succès, false sinon.
     */
    suspend fun importPrompts(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = InputStreamReader(inputStream).readText()
                // Validation du JSON
                val type = object : TypeToken<Map<String, String>>() {}.type
                val importedMap: Map<String, String> = Gson().fromJson(jsonString, type)
                // Si le parsing réussit, on sauvegarde
                savePrompts(context, importedMap)
                return@withContext true
            }
        } catch (e: Exception) {
            when(e) {
                is IOException, is JsonSyntaxException, is IllegalStateException -> {
                    Log.e(TAG, "Échec de l'importation des prompts.", e)
                    return@withContext false
                }
                else -> throw e
            }
        }
        return@withContext false
    }

    private fun loadPromptsFromFile(file: File) {
        try {
            val jsonString = file.readText()
            val type = object : TypeToken<Map<String, String>>() {}.type
            val prompts: Map<String, String> = Gson().fromJson(jsonString, type)
            promptsCache.clear()
            promptsCache.putAll(prompts)
            Log.i(TAG, "${promptsCache.size} prompts chargés en mémoire depuis ${file.name}.")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du chargement des prompts depuis le fichier.", e)
        }
    }
}