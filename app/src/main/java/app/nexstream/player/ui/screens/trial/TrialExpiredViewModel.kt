package app.nexstream.player.ui.screens.trial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.license.LicenceManager
import app.nexstream.player.license.LicenceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrialExpiredUiState(
    val isActivated: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class TrialExpiredViewModel @Inject constructor(
    private val licenceManager: LicenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrialExpiredUiState())
    val uiState: StateFlow<TrialExpiredUiState> = _uiState

    fun getDeviceId(): String = licenceManager.getDeviceId()
    fun activate(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = licenceManager.activate(key)) {
                is LicenceResult.Success -> _uiState.update {
                    it.copy(isLoading = false, isActivated = true)
                }
                is LicenceResult.DeviceLimitReached -> _uiState.update {
                    it.copy(isLoading = false,
                        errorMessage = "Device limit reached. Visit nexstream.uk to manage devices.")
                }
                is LicenceResult.Expired -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "This licence has expired.")
                }
                is LicenceResult.Error -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
            }
        }
    }
}