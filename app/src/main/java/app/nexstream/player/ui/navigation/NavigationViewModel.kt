package app.nexstream.player.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Shared navigation state for the three-pane layout (Rail → Panel → Content).
 * Injected as a singleton so all screens share the same focus/selection state.
 *
 * All rail items have a panel — hasPanel does not exist.
 *
 * Focus zones:
 *   RAIL    — left sidebar, always visible
 *   PANEL   — middle column, width animates 0dp ↔ 280dp when entering/leaving
 *   CONTENT — right area, no direct back-navigation to panel (Back button only)
 */
enum class FocusZone { RAIL, PANEL, CONTENT }

data class NavPanelItem(val id: String, val label: String)

data class NavigationState(
    val focusZone: FocusZone = FocusZone.RAIL,

    // The currently selected rail item id
    val selectedRailItemId: String? = null,

    // Per-rail-item memory: railItemId -> last selected panel item id
    val lastSelectedPanelItemPerRail: Map<String, String> = emptyMap(),

    // Whether the panel is visible (width > 0)
    val isPanelVisible: Boolean = false,

    // The currently active panel item id (drives content)
    val activePanelItemId: String? = null,
)

@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(NavigationState())
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    // -------------------------------------------------------------------------
    // Rail events
    // -------------------------------------------------------------------------

    /**
     * Rail: Center/OK pressed.
     * Selects the rail item and expands the panel.
     * Focus stays on RAIL — the user must DPad Right to enter the panel.
     */
    fun onRailItemConfirmed(railItemId: String) {
        _state.update { current ->
            current.copy(
                selectedRailItemId = railItemId,
                isPanelVisible = true,
                focusZone = FocusZone.RAIL,
                activePanelItemId = current.lastSelectedPanelItemPerRail[railItemId]
            )
        }
    }

    /**
     * Rail: DPad Right pressed.
     * Expands the panel and moves focus to it.
     * Target panel item priority: last selected → "All" → first item.
     * The caller should pass the resolved targetPanelItemId via resolveInitialPanelItem().
     */
    fun onRailDPadRight(railItemId: String, targetPanelItemId: String) {
        _state.update { current ->
            current.copy(
                selectedRailItemId = railItemId,
                isPanelVisible = true,
                focusZone = FocusZone.PANEL,
                activePanelItemId = targetPanelItemId,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Panel events
    // -------------------------------------------------------------------------

    /**
     * Panel: Center/OK pressed on a panel item.
     * Selects the item and updates content — focus stays on PANEL.
     */
    fun onPanelItemConfirmed(panelItemId: String) {
        _state.update { current ->
            val railId = current.selectedRailItemId ?: return@update current
            current.copy(
                activePanelItemId = panelItemId,
                focusZone = FocusZone.PANEL,
                lastSelectedPanelItemPerRail = current.lastSelectedPanelItemPerRail
                    + (railId to panelItemId),
            )
        }
    }

    /**
     * Panel: DPad Right pressed on a panel item.
     * Selects the item and shifts focus to CONTENT.
     */
    fun onPanelDPadRight(panelItemId: String) {
        _state.update { current ->
            val railId = current.selectedRailItemId ?: return@update current
            current.copy(
                activePanelItemId = panelItemId,
                focusZone = FocusZone.CONTENT,
                lastSelectedPanelItemPerRail = current.lastSelectedPanelItemPerRail
                    + (railId to panelItemId),
            )
        }
    }

    /**
     * Panel: DPad Left or Back pressed.
     * Hides the panel (width → 0) and returns focus to the selected rail item.
     */
    fun onPanelExitToRail() {
        _state.update { current ->
            current.copy(
                isPanelVisible = false,
                focusZone = FocusZone.RAIL,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Content events
    // -------------------------------------------------------------------------

    /**
     * Content: Back pressed.
     * Returns focus to the panel (which stays visible) on the last active panel item.
     */
    fun onContentBack() {
        _state.update { current ->
            current.copy(
                focusZone = FocusZone.PANEL,
                isPanelVisible = true,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolve the target panel item when entering from the rail via DPad Right.
     * Priority: last selected for this rail item → "All" item if it exists → first item.
     */
    fun resolveInitialPanelItem(
        railItemId: String,
        panelItems: List<NavPanelItem>,
    ): String? {
        if (panelItems.isEmpty()) return null
        val lastSelected = _state.value.lastSelectedPanelItemPerRail[railItemId]
        if (lastSelected != null && panelItems.any { it.id == lastSelected }) return lastSelected
        val allItem = panelItems.firstOrNull { it.id == "all" || it.label.equals("All", ignoreCase = true) }
        if (allItem != null) return allItem.id
        return panelItems.first().id
    }
}
