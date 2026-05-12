package app.nexstream.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.license.DeviceInfo
import app.nexstream.player.license.LicenceManager
import app.nexstream.player.license.LicenceResult
import app.nexstream.player.license.TrialManager
import app.nexstream.player.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LicenceUiState(
    val isActivated: Boolean = false,
    val email: String? = null,
    val licenceType: String? = null,
    val expiresAt: String? = null,
    val deviceLimit: Int = 1,
    val devices: List<DeviceInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingDevices: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentDeviceId: String = ""
)

@HiltViewModel
class LicenceViewModel @Inject constructor(
    private val licenceManager: LicenceManager,
    private val trialManager: TrialManager,
    private val themeManager: ThemeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LicenceUiState())
    val uiState: StateFlow<LicenceUiState> = _uiState

    init {
        _uiState.update {
            it.copy(
                isActivated  = licenceManager.isActivated(),
                email        = licenceManager.getStoredEmail(),
                licenceType  = licenceManager.getStoredLicenceType(),
                expiresAt    = licenceManager.getStoredExpiresAt(),
                deviceLimit  = licenceManager.getStoredDeviceLimit(),
                currentDeviceId = licenceManager.getDeviceId()
            )
        }
        if (licenceManager.isActivated()) loadDevices()
    }

    fun activate(key: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            when (val result = licenceManager.activate(key)) {
                is LicenceResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading   = false,
                            isActivated = true,
                            email       = result.email,
                            licenceType = result.licenceType,
                            expiresAt   = result.expiresAt,
                            deviceLimit = result.deviceLimit
                        )
                    }
                    loadDevices()
                    themeManager.refresh()
                }
                is LicenceResult.DeviceLimitReached -> _uiState.update {
                    it.copy(isLoading = false,
                        errorMessage = "Device limit reached. Remove a device at nexstream.uk to continue.")
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

    // Check if a licence has been assigned to this device on the server and auto-activate
    fun checkAssignedLicence() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val deviceId = licenceManager.getDeviceId()
            val activated = trialManager.checkAndActivateAssignedLicence(deviceId)
            if (activated) {
                _uiState.update {
                    it.copy(
                        isLoading   = false,
                        isActivated = true,
                        email       = licenceManager.getStoredEmail(),
                        licenceType = licenceManager.getStoredLicenceType(),
                        expiresAt   = licenceManager.getStoredExpiresAt(),
                        deviceLimit = licenceManager.getStoredDeviceLimit(),
                        successMessage = "Licence activated successfully!"
                    )
                }
                loadDevices()
                themeManager.refresh()
            } else {
                _uiState.update {
                    it.copy(isLoading = false,
                        errorMessage = "No licence found for this device. Ask your administrator to assign one.")
                }
            }
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            val response = licenceManager.getDevices()
            _uiState.update {
                it.copy(
                    isLoadingDevices = false,
                    devices          = response?.devices ?: emptyList(),
                    deviceLimit      = response?.device_limit ?: it.deviceLimit
                )
            }
        }
    }

    fun deactivate() {
        viewModelScope.launch {
            licenceManager.removeDevice() // remove from server before clearing local prefs
            licenceManager.deactivate()
            _uiState.update { LicenceUiState(currentDeviceId = licenceManager.getDeviceId()) }
        }
    }
}