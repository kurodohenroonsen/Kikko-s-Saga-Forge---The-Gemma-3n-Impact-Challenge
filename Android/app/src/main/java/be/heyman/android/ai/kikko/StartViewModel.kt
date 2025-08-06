package be.heyman.android.ai.kikko

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class StartUiState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)


class StartViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StartUiState())
    val uiState = _uiState.asStateFlow()

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}