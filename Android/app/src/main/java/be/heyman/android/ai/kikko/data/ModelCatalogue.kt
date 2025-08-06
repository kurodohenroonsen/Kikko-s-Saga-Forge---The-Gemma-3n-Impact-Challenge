package be.heyman.android.ai.kikko.data

import be.heyman.android.ai.kikko.data.ConfigKey

/**
 * Catalogue des modèles LLM et Vosk disponibles pour l'application.
 * Ces modèles peuvent être téléchargés par l'utilisateur via la boîte à outils.
 * Initialement dans le package `debug`, il est déplacé ici car ses informations
 * sont utilisées par des fonctionnalités principales (ex: ToolsDialogFragment).
 */
object ModelCatalogue {

    val allModels: List<Model> = listOf(
        Model(
            name = "Gemma 3n E2B (kikko.be - zip)",
            downloadFileName = "gemma-3n-E2B-it-int4.task.zip",
            url = "https://www.kikko.be/model/gemma-3n-E2B-it-int4.task.zip",
            sizeInBytes = 2450829619,
            isZip = true,
            unzipDir = "",
            llmSupportImage = true,
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 4096)
        ),
        Model(
            name = "Gemma 3n E2B (.task direct)",
            downloadFileName = "gemma-3n-E2B-it-int4.task",
            url = "https://www.kikko.be/model/gemma-3n-E2B-it-int4.task",
            sizeInBytes = 3136226711,
            isZip = false,
            unzipDir = "",
            llmSupportImage = true,
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 4096)
        ),
        Model(
            name = "Gemma 3n E4B (kikko.be - zip)",
            downloadFileName = "gemma-3n-E4B-it-int4.task.zip",
            url = "https://www.kikko.be/model/gemma-3n-E4B-it-int4.task.zip",
            sizeInBytes = 3513918248,
            isZip = true,
            unzipDir = "",
            llmSupportImage = true,
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 4096)
        ),
        Model(
            name = "Gemma 3n E4B (.task direct)",
            downloadFileName = "gemma-3n-E4B-it-int4.task",
            url = "https://www.kikko.be/model/gemma-3n-E4B-it-int4.task",
            sizeInBytes = 4405655031,
            isZip = false,
            unzipDir = "",
            llmSupportImage = true,
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 4096)
        ),
        Model(
            name = "Gemma 3 1B (kikko.be)",
            downloadFileName = "gemma3-1B-it-int4.task.zip",
            url = "https://www.kikko.be/model/gemma3-1B-it-int4.task.zip",
            sizeInBytes = 392700000,
            isZip = true,
            unzipDir = "",
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 4096)
        ),
        Model(
            name = "Qwen 2.5 1.8B Chat (kikko.be)",
            downloadFileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task.zip",
            url = "https://www.kikko.be/model/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task.zip",
            sizeInBytes = 1380000000,
            isZip = true,
            unzipDir = "",
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 2048)
        ),
        Model(
            name = "Hammer 2.1 - 1.5B (kikko.be)",
            downloadFileName = "hammer2.1_1.5b_q8_ekv4096.task.zip",
            url = "https://www.kikko.be/model/hammer2.1_1.5b_q8_ekv4096.task.zip",
            sizeInBytes = 1380000000,
            isZip = true,
            unzipDir = "",
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 2048)
        ),
        Model(
            name = "Hammer 2.1 - 0.5B (kikko.be)",
            downloadFileName = "hammer2p1_05b_.task",
            url = "https://www.kikko.be/model/hammer2p1_05b_.task",
            sizeInBytes = 1380000000,
            isZip = false,
            unzipDir = "",
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 2048)
        ),
        Model(
            name = "DeepSeek (kikko.be) Zip",
            downloadFileName = "deepseek_q8_ekv1280.task.zip",
            url = "https://www.kikko.be/model/deepseek_q8_ekv1280.task.zip",
            sizeInBytes = 1560000000,
            isZip = true,
            unzipDir = "",
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 2048)
        ),
        Model(
            name = "DeepSeek (kikko.be) task",
            downloadFileName = "deepseek_q8_ekv1280.task",
            url = "https://www.kikko.be/model/deepseek_q8_ekv1280.task",
            sizeInBytes = 1860000000,
            isZip = false,
            unzipDir = "",
            configValues = mapOf(ConfigKey.MAX_TOKENS.label to 2048)
        ),
        Model(
            name = "Vosk Small French (kikko.be)",
            downloadFileName = "vosk-model-small-fr-0.22.zip",
            url = "https://www.kikko.be/model/vosk-model-small-fr-0.22.zip",
            sizeInBytes = 42233323,
            isZip = true,
            unzipDir = "vosk-model-small-fr-0.22"
        ),
        Model(
            name = "Vosk Small English (kikko.be)",
            downloadFileName = "vosk-model-small-en-us-0.15.zip",
            url = "https://www.kikko.be/model/vosk-model-small-en-us-0.15.zip",
            sizeInBytes = 41205931,
            isZip = true,
            unzipDir = "vosk-model-small-en-us-0.15"
        ),
        Model(
            name = "Vosk Small Japanese (kikko.be)",
            downloadFileName = "vosk-model-small-ja-0.22.zip",
            url = "https://www.kikko.be/model/vosk-model-small-ja-0.22.zip",
            sizeInBytes = 49704573,
            isZip = true,
            unzipDir = "vosk-model-small-ja-0.22"
        )
    )
}