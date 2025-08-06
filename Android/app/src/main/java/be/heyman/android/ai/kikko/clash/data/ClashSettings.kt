package be.heyman.android.ai.kikko.clash.data

/**
 * Data class pour contenir tous les paramètres de configuration du Clash.
 */
data class ClashSettings(
    val queenModelName: String,
    val brain: String = "CPU",
    val temperature: Float = 0.44f,
    val isTtsEnabled: Boolean = true,
    val showSetupOnLaunch: Boolean = true
)