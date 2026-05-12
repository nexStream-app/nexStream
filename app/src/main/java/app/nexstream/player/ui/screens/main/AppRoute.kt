package app.nexstream.player.ui.screens.main

/**
 * Typed navigation keys — replaces all "guide", "movies", "settings_appearance" etc string routes.
 * Sealed so the compiler enforces exhaustiveness in every when() branch.
 */
sealed interface AppRoute {

    // ── Top-level rail destinations ───────────────────────────────────────────
    data object Recent      : AppRoute
    data object Guide       : AppRoute
    data object Movies      : AppRoute
    data object Series      : AppRoute
    data object CatchUp     : AppRoute
    data object Search      : AppRoute
    data object MyList      : AppRoute
    data object Downloads   : AppRoute

    // ── Settings sub-destinations ─────────────────────────────────────────────
    data object Settings           : AppRoute
    data object SettingsPlaylists  : AppRoute
    data object SettingsAppearance : AppRoute
    data object SettingsLicence    : AppRoute
    data object SettingsPlayer     : AppRoute
    data object SettingsProfiles   : AppRoute
    data object SettingsAccount    : AppRoute
    data object SettingsAbout      : AppRoute
}

// ── Route grouping helpers ────────────────────────────────────────────────────

/** The top-level rail section this route belongs to. */
fun AppRoute.rootSection(): AppRoute = when (this) {
    AppRoute.Recent                                   -> AppRoute.Recent
    AppRoute.Guide                                    -> AppRoute.Guide
    AppRoute.Movies                                   -> AppRoute.Movies
    AppRoute.Series                                   -> AppRoute.Series
    AppRoute.CatchUp                                  -> AppRoute.CatchUp
    AppRoute.Search                                   -> AppRoute.Search
    AppRoute.MyList                                   -> AppRoute.MyList
    AppRoute.Downloads                                -> AppRoute.Downloads
    AppRoute.Settings,
    AppRoute.SettingsPlaylists,
    AppRoute.SettingsAppearance,
    AppRoute.SettingsLicence,
    AppRoute.SettingsPlayer,
    AppRoute.SettingsProfiles,
    AppRoute.SettingsAccount,
    AppRoute.SettingsAbout                            -> AppRoute.Settings
}

/** True for routes that have a category panel. */
val AppRoute.hasCategoryPanel: Boolean
    get() = this == AppRoute.Guide || this == AppRoute.Movies || this == AppRoute.Series || this == AppRoute.CatchUp

/** True for routes in the settings sub-tree. */
val AppRoute.isSettings: Boolean
    get() = rootSection() == AppRoute.Settings