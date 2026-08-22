package app.nexstream.player.ui.screens.main

/**
 * Typed navigation keys — replaces all "guide", "movies", "settings_appearance" etc string routes.
 * Sealed so the compiler enforces exhaustiveness in every when() branch.
 */
sealed interface AppRoute {

    // ── Top-level rail destinations ───────────────────────────────────────────
    data object Home        : AppRoute
    data object Recent      : AppRoute
    data object Guide       : AppRoute
    data object Movies      : AppRoute
    data object Series      : AppRoute
    data object CatchUp     : AppRoute
    data object Search      : AppRoute
    data object MyList      : AppRoute
    data object Reminders   : AppRoute
    data object Downloads   : AppRoute
    data object Picks       : AppRoute
    data object Music       : AppRoute
    data object Device      : AppRoute

    // ── Settings sub-destinations ─────────────────────────────────────────────
    data object Settings             : AppRoute
    data object SettingsPlaylists    : AppRoute
    data object SettingsAppearance   : AppRoute
    data object SettingsLicence      : AppRoute
    data object SettingsPlayer       : AppRoute
    data object SettingsProfiles     : AppRoute
    data object SettingsAccount      : AppRoute
    data object SettingsAbout        : AppRoute
    data object SettingsNavigation   : AppRoute
    data object SettingsSports        : AppRoute
    data object SettingsSyncSettings  : AppRoute
    data object SettingsChannelGroups : AppRoute
}

// ── Route grouping helpers ────────────────────────────────────────────────────

/** The top-level rail section this route belongs to. */
fun AppRoute.rootSection(): AppRoute = when (this) {
    AppRoute.Home                                     -> AppRoute.Home
    AppRoute.Recent                                   -> AppRoute.Recent
    AppRoute.Guide                                    -> AppRoute.Guide
    AppRoute.Movies                                   -> AppRoute.Movies
    AppRoute.Series                                   -> AppRoute.Series
    AppRoute.CatchUp                                  -> AppRoute.CatchUp
    AppRoute.Search                                   -> AppRoute.Search
    AppRoute.MyList                                   -> AppRoute.MyList
    AppRoute.Reminders                                -> AppRoute.Reminders
    AppRoute.Downloads                                -> AppRoute.Downloads
    AppRoute.Picks                                    -> AppRoute.Picks
    AppRoute.Music                                    -> AppRoute.Music
    AppRoute.Device                                   -> AppRoute.Device
    AppRoute.Settings,
    AppRoute.SettingsPlaylists,
    AppRoute.SettingsAppearance,
    AppRoute.SettingsLicence,
    AppRoute.SettingsPlayer,
    AppRoute.SettingsProfiles,
    AppRoute.SettingsAccount,
    AppRoute.SettingsAbout,
    AppRoute.SettingsNavigation,
    AppRoute.SettingsSports,
    AppRoute.SettingsSyncSettings,
    AppRoute.SettingsChannelGroups                    -> AppRoute.Settings
}

/** True for routes that have a category panel. */
val AppRoute.hasCategoryPanel: Boolean
    get() = this == AppRoute.Home || this == AppRoute.Guide || this == AppRoute.Movies ||
            this == AppRoute.Series || this == AppRoute.CatchUp || this.isSettings ||
            this == AppRoute.Recent || this == AppRoute.Search ||
            this == AppRoute.MyList || this == AppRoute.Reminders ||
            this == AppRoute.Downloads || this == AppRoute.Picks || this == AppRoute.Music ||
            this == AppRoute.Device

/** True for routes in the settings sub-tree. */
val AppRoute.isSettings: Boolean
    get() = rootSection() == AppRoute.Settings