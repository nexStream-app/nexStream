package app.nexstream.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.nexstream.player.ui.navigation.FocusZone
import app.nexstream.player.ui.navigation.NavPanelItem
import app.nexstream.player.ui.navigation.NavigationViewModel

/**
 * Root three-pane layout used across all screens.
 *
 * Layout: [Rail (always visible)] [Panel (animated width)] [Content]
 *
 * All rail items have a panel — hasPanel does not exist.
 *
 * Focus rules:
 *  - Rail: Center → select + expand panel, focus stays on rail
 *           DPad Right → expand panel + move focus to panel
 *  - Panel: Center → select item, update content, focus stays on panel
 *            DPad Right → select item, move focus to content
 *            DPad Left / Back → hide panel, return focus to rail
 *  - Content: Back only → return focus to panel (panel stays visible)
 *             DPad Left → blocked (no direct back-path to panel from content)
 */
@Composable
fun ThreePaneLayout(
    railItems: List<NavPanelItem>,
    panelItems: List<NavPanelItem>,
    navViewModel: NavigationViewModel = hiltViewModel(),
    railContent: @Composable (
        selectedId: String?,
        onItemConfirmed: (String) -> Unit,
        onDPadRight: (String) -> Unit,
    ) -> Unit,
    panelContent: @Composable (
        items: List<NavPanelItem>,
        activePanelItemId: String?,
        onItemConfirmed: (String) -> Unit,
        onDPadRight: (String) -> Unit,
        onDPadLeft: () -> Unit,
    ) -> Unit,
    contentArea: @Composable (activePanelItemId: String?) -> Unit,
) {
    val state by navViewModel.state.collectAsState()

    val railFocusRequester = remember { FocusRequester() }
    val panelFocusRequester = remember { FocusRequester() }

    val panelWidth by animateDpAsState(
        targetValue = if (state.isPanelVisible) 280.dp else 0.dp,
        label = "panelWidth"
    )

    LaunchedEffect(state.focusZone) {
        when (state.focusZone) {
            FocusZone.RAIL    -> railFocusRequester.requestFocus()
            FocusZone.PANEL   -> panelFocusRequester.requestFocus()
            FocusZone.CONTENT -> { /* content manages its own internal focus */ }
        }
    }

    BackHandler(enabled = state.focusZone == FocusZone.CONTENT) {
        navViewModel.onContentBack()
    }
    BackHandler(enabled = state.focusZone == FocusZone.PANEL) {
        navViewModel.onPanelExitToRail()
    }

    Row(modifier = Modifier.fillMaxSize()) {

        // ── Rail ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .focusRequester(railFocusRequester)
                .width(80.dp)
                .fillMaxHeight()
        ) {
            railContent(
                selectedId = state.selectedRailItemId,
                onItemConfirmed = { navViewModel.onRailItemConfirmed(it) },
                onDPadRight = { railItemId ->
                    val target = navViewModel.resolveInitialPanelItem(railItemId, panelItems)
                    if (target != null) navViewModel.onRailDPadRight(railItemId, target)
                },
            )
        }

        // ── Panel ─────────────────────────────────────────────────────────────
        // Width animates to 0 when hidden — content stays composed but unreachable by focus
        Box(
            modifier = Modifier
                .focusRequester(panelFocusRequester)
                .width(panelWidth)
                .fillMaxHeight()
        ) {
            if (state.isPanelVisible || panelWidth > 0.dp) {
                panelContent(
                    items = panelItems,
                    activePanelItemId = state.activePanelItemId,
                    onItemConfirmed = { navViewModel.onPanelItemConfirmed(it) },
                    onDPadRight = { navViewModel.onPanelDPadRight(it) },
                    onDPadLeft = { navViewModel.onPanelExitToRail() },
                )
            }
        }

        // ── Content ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            contentArea(activePanelItemId = state.activePanelItemId)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rail item — key event pattern for the rail zone
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Rail item composable skeleton — all rail items have a panel so DPad Right always
 * triggers onDPadRight. Replace the empty Box body with your icon + label UI.
 */
@Composable
fun RailItem(
    item: NavPanelItem,
    isSelected: Boolean,
    onConfirmed: (String) -> Unit,
    onDPadRight: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        onConfirmed(item.id); true
                    }
                    Key.DirectionRight -> {
                        onDPadRight(item.id); true
                    }
                    else -> false
                }
            }
    ) {
        // Rail item UI (icon + label)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Panel item — key event pattern for the panel zone
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Panel item composable skeleton.
 * Center/OK selects but keeps focus on panel.
 * DPad Right selects and moves focus to content.
 * DPad Left / Back returns focus to rail and hides panel.
 */
@Composable
fun PanelItemRow(
    item: NavPanelItem,
    isActive: Boolean,
    onConfirmed: (String) -> Unit,
    onDPadRight: (String) -> Unit,
    onDPadLeft: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        onConfirmed(item.id); true   // focus stays on panel
                    }
                    Key.DirectionRight -> {
                        onDPadRight(item.id); true   // focus moves to content
                    }
                    Key.DirectionLeft -> {
                        onDPadLeft(); true           // focus returns to rail, panel hides
                    }
                    else -> false
                }
            }
    ) {
        // Panel item UI
    }
}
