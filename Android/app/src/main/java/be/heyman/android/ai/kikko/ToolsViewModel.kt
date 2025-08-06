package be.heyman.android.ai.kikko


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import be.heyman.android.ai.kikko.data.Model
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsUiState(
    val isLoading: Boolean = false,
    val models: List<Model> = emptyList(),
    val errorMessage: String? = null
)


class ToolsViewModel @Inject constructor(
    // private val modelRepository: ModelRepository // Sera injecté plus tard
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadModels() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        // TODO: Remplacer par un véritable appel au repository pour lister les modèles disponibles et locaux
        val dummyModels = listOf(
            Model(name = "gemma-3n-E2B-it-int4.task", url = "...", downloadFileName = "gemma-3n-E2B-it-int4.task", sizeInBytes = 1_200_000_000, llmSupportImage = true),
            Model(name = "gemma-3n-E4B-it-int4.task", url = "...", downloadFileName = "gemma-3n-E4B-it-int4.task", sizeInBytes = 2_500_000_000, llmSupportImage = true)
        )
        _uiState.update { it.copy(isLoading = false, models = dummyModels) }
    }

    fun downloadModel(model: Model) = viewModelScope.launch {
        // TODO: Implémenter la logique de téléchargement du modèle
        _uiState.update { it.copy(errorMessage = "La fonctionnalité de téléchargement n'est pas encore implémentée.") }
    }

    fun deleteModel(model: Model) = viewModelScope.launch {
        // TODO: Implémenter la logique de suppression du modèle
        _uiState.update { it.copy(errorMessage = "La fonctionnalité de suppression n'est pas encore implémentée.") }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}