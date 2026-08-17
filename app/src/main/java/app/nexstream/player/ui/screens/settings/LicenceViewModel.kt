package app.nexstream.player.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.nexstream.player.data.profile.ProfileManager
import app.nexstream.player.data.sync.ProfileSyncManager
import app.nexstream.player.data.sync.WatchlistSyncManager
import app.nexstream.player.license.AppAccessState
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
    val licenceKey: String? = null,
    val email: String? = null,
    val licenceType: String? = null,
    val expiresAt: String? = null,
    val deviceLimit: Int = 1,
    val devices: List<DeviceInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingDevices: Boolean = false,
    val devicesLoadFailed: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val currentDeviceId: String = "",
    val deviceModel: String = "",
    val isResellerAssigned: Boolean = false,
    val resellerId: Int? = null,
    val accessState: AppAccessState = AppAccessState.LOADING,
    val trialDaysLeft: Int = 0,
    val trialExpiresAt: String? = null
)

@HiltViewModel
class LicenceViewModel @Inject constructor(
    private val licenceManager: LicenceManager,
    private val trialManager: TrialManager,
    private val themeManager: ThemeManager,
    private val profileSyncManager: ProfileSyncManager,
    private val watchlistSyncManager: WatchlistSyncManager,
    private val profileManager: ProfileManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LicenceUiState())
    val uiState: StateFlow<LicenceUiState> = _uiState

    private val _accessState = MutableStateFlow(trialManager.getLocalAccessState())
    val accessState: StateFlow<AppAccessState> = _accessState

    private var accessStateJob: kotlinx.coroutines.Job? = null

    init {
        val activated = licenceManager.isActivated()
        val localAccessState = trialManager.getLocalAccessState()
        android.util.Log.i("LicenceVM", "init: isActivated=$activated localState=$localAccessState deviceId=${licenceManager.getDeviceId()}")
        val model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            .replaceFirstChar { it.uppercase() }
        _uiState.update {
            it.copy(
                isActivated        = activated,
                licenceKey         = licenceManager.getStoredLicenceKey(),
                email              = licenceManager.getStoredEmail(),
                licenceType        = licenceManager.getStoredLicenceType(),
                expiresAt          = licenceManager.getStoredExpiresAt(),
                deviceLimit        = licenceManager.getStoredDeviceLimit(),
                currentDeviceId    = licenceManager.getDeviceId(),
                deviceModel        = model,
                isResellerAssigned = licenceManager.isResellerAssigned(),
                resellerId         = licenceManager.getStoredResellerId(),
                accessState        = localAccessState,
                trialDaysLeft      = trialManager.getDaysLeft(),
                trialExpiresAt     = trialManager.getTrialExpiresAt()
            )
        }
        if (activated) loadDevices()
        else checkAndUpdateAccessState()

        // Refresh if licence is activated remotely (e.g. via FCM push)
        viewModelScope.launch {
            licenceManager.activationEvents.collect {
                val deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
                    .replaceFirstChar { c -> c.uppercase() }
                _uiState.update { s ->
                    s.copy(
                        isActivated        = true,
                        licenceKey         = licenceManager.getStoredLicenceKey(),
                        email              = licenceManager.getStoredEmail(),
                        licenceType        = licenceManager.getStoredLicenceType(),
                        expiresAt          = licenceManager.getStoredExpiresAt(),
                        deviceLimit        = licenceManager.getStoredDeviceLimit(),
                        deviceModel        = deviceModel,
                        isResellerAssigned = licenceManager.isResellerAssigned(),
                        resellerId         = licenceManager.getStoredResellerId(),
                        accessState        = AppAccessState.LICENSED
                    )
                }
                _accessState.value = AppAccessState.LICENSED
                loadDevices()
            }
        }
    }

    private fun checkAndUpdateAccessState() {
        accessStateJob?.cancel()
        accessStateJob = viewModelScope.launch {
            val deviceId = licenceManager.getDeviceId()
            android.util.Log.i("LicenceVM", "checkAndUpdateAccessState: calling checkAccessState for $deviceId")
            val state = trialManager.checkAccessState(deviceId)
            // Re-check: licence may have been activated while we were querying the server
            if (licenceManager.isActivated()) {
                android.util.Log.i("LicenceVM", "checkAndUpdateAccessState: licence activated during check → LICENSED")
                _accessState.value = AppAccessState.LICENSED
                _uiState.update {
                    it.copy(
                        isActivated        = true,
                        licenceKey         = licenceManager.getStoredLicenceKey(),
                        email              = licenceManager.getStoredEmail(),
                        licenceType        = licenceManager.getStoredLicenceType(),
                        expiresAt          = licenceManager.getStoredExpiresAt(),
                        deviceLimit        = licenceManager.getStoredDeviceLimit(),
                        isResellerAssigned = licenceManager.isResellerAssigned(),
                        resellerId         = licenceManager.getStoredResellerId(),
                        accessState        = AppAccessState.LICENSED,
                        trialDaysLeft      = trialManager.getDaysLeft()
                    )
                }
                loadDevices()
                return@launch
            }
            android.util.Log.i("LicenceVM", "checkAndUpdateAccessState: result=$state")
            _accessState.value = state
            if (state == AppAccessState.LICENSED) {
                _uiState.update {
                    it.copy(
                        accessState        = state,
                        isActivated        = true,
                        licenceKey         = licenceManager.getStoredLicenceKey(),
                        email              = licenceManager.getStoredEmail(),
                        licenceType        = licenceManager.getStoredLicenceType(),
                        expiresAt          = licenceManager.getStoredExpiresAt(),
                        deviceLimit        = licenceManager.getStoredDeviceLimit(),
                        isResellerAssigned = licenceManager.isResellerAssigned(),
                        resellerId         = licenceManager.getStoredResellerId(),
                        trialDaysLeft      = trialManager.getDaysLeft()
                    )
                }
                loadDevices()
            } else {
                _uiState.update { it.copy(accessState = state, trialDaysLeft = trialManager.getDaysLeft(), trialExpiresAt = trialManager.getTrialExpiresAt()) }
            }
        }
    }

    fun activate(key: String) {
        accessStateJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            when (val result = licenceManager.activate(key)) {
                is LicenceResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading          = false,
                            isActivated        = true,
                            licenceKey         = licenceManager.getStoredLicenceKey(),
                            email              = result.email,
                            licenceType        = result.licenceType,
                            expiresAt          = result.expiresAt,
                            deviceLimit        = result.deviceLimit,
                            isResellerAssigned = licenceManager.isResellerAssigned(),
                            resellerId         = licenceManager.getStoredResellerId(),
                            accessState        = AppAccessState.LICENSED
                        )
                    }
                    _accessState.value = AppAccessState.LICENSED
                    loadDevices()
                    themeManager.refresh()
                    triggerPostActivationSync()
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
        accessStateJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val deviceId = licenceManager.getDeviceId()
            when (val result = trialManager.checkAssignedLicenceForDisplay(deviceId)) {
                is app.nexstream.player.license.AssignedLicenceCheck.FullLicenceActivated -> {
                    _uiState.update {
                        it.copy(
                            isLoading          = false,
                            isActivated        = true,
                            licenceKey         = licenceManager.getStoredLicenceKey(),
                            email              = licenceManager.getStoredEmail(),
                            licenceType        = licenceManager.getStoredLicenceType(),
                            expiresAt          = licenceManager.getStoredExpiresAt(),
                            deviceLimit        = licenceManager.getStoredDeviceLimit(),
                            isResellerAssigned = licenceManager.isResellerAssigned(),
                            resellerId         = licenceManager.getStoredResellerId(),
                            accessState        = AppAccessState.LICENSED,
                            successMessage     = "Licence activated successfully!"
                        )
                    }
                    _accessState.value = AppAccessState.LICENSED
                    themeManager.refresh()
                    triggerPostActivationSync()
                }
                is app.nexstream.player.license.AssignedLicenceCheck.TrialFound -> {
                    val localState = trialManager.getLocalAccessState()
                    _uiState.update {
                        it.copy(
                            isLoading      = false,
                            accessState    = localState,
                            trialDaysLeft  = result.daysLeft,
                            trialExpiresAt = result.expiresAt,
                            successMessage = "Trial confirmed: ${result.daysLeft} day${if (result.daysLeft == 1) "" else "s"} remaining"
                        )
                    }
                }
                is app.nexstream.player.license.AssignedLicenceCheck.None -> {
                    _uiState.update {
                        it.copy(isLoading = false,
                            errorMessage = "No licence found for this device. Ask your administrator to assign one.")
                    }
                }
            }
        }
    }

    private fun triggerPostActivationSync() {
        viewModelScope.launch {
            profileSyncManager.syncFromServer()
            val profileId = profileManager.activeProfile.value?.id ?: return@launch
            watchlistSyncManager.syncFromServer(profileId)
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            licenceManager.sendHeartbeat()
            android.util.Log.i("LicenceViewModel", "heartbeat sent, fetching devices (key=${if (licenceManager.isActivated()) "present" else "missing"})")
            _uiState.update { it.copy(isLoadingDevices = true, devicesLoadFailed = false) }
            val response = licenceManager.getDevices()
            android.util.Log.i("LicenceViewModel", "getDevices response: success=${response?.success} count=${response?.devices?.size} deviceId=${licenceManager.getDeviceId()}")
            _uiState.update {
                it.copy(
                    isLoadingDevices = false,
                    devicesLoadFailed = response == null,
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
            val localState = trialManager.getLocalAccessState()
            _uiState.update {
                LicenceUiState(
                    currentDeviceId = licenceManager.getDeviceId(),
                    accessState     = localState,
                    trialDaysLeft   = trialManager.getDaysLeft(),
                    trialExpiresAt  = trialManager.getTrialExpiresAt()
                )
            }
        }
    }
}