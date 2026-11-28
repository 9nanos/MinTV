package tv.own.owntv.features.shell

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.nav.MainSection

class NativeNavigationTest {
    private val all = MainSection.entries.toSet()
    @Test fun everyLegacyOwnerHasACanonicalHome() {
        assertEquals(TopDestination.HOME, topDestination(MainSection.HOME))
        assertEquals(TopDestination.TV, topDestination(MainSection.LIVE_TV))
        assertEquals(TopDestination.TV, topDestination(MainSection.EPG))
        assertEquals(TopDestination.MOVIES, topDestination(MainSection.MOVIES))
        assertEquals(TopDestination.SERIES, topDestination(MainSection.SERIES))
        for (section in listOf(MainSection.MORE, MainSection.SETTINGS, MainSection.DOWNLOADS))
            assertEquals(TopDestination.PROFILE, topDestination(section))
        // Upstream 5.2's Sports section belongs under Min TV's Sport destination. Until v0.20 M2 folds it
        // into the Sport tab it is opened from Home's live sports row, like Search from its key: an alias.
        assertEquals(TopDestination.SPORT, topDestination(MainSection.SPORTS))
        val aliases = TopDestination.entries.flatMap { nativeRoutes(it, all) }.toSet() + MainSection.SEARCH + MainSection.SPORTS
        assertEquals(all, aliases)
    }
    @Test fun sportUsesPresentationOnlyAndHomeDeepLinkStillMapsHome() {
        assertEquals(MainSection.HOME, nativeEntry(TopDestination.SPORT, all))
        assertEquals(TopDestination.SPORT, topDestination(MainSection.HOME, sport = true))
        assertEquals(TopDestination.HOME, topDestination(MainSection.HOME))
    }
    @Test fun filmAndSeriesAreSeparateTabsWithoutSubNavigation() {
        assertEquals(listOf(MainSection.MOVIES), nativeRoutes(TopDestination.MOVIES, all))
        assertEquals(listOf(MainSection.SERIES), nativeRoutes(TopDestination.SERIES, all))
        assertEquals(MainSection.MOVIES, nativeEntry(TopDestination.MOVIES, all))
        assertEquals(MainSection.SERIES, nativeEntry(TopDestination.SERIES, all))
    }
    @Test fun hiddenMovieDoesNotReappearThroughTheSeriesTab() {
        assertEquals(emptyList<MainSection>(), nativeRoutes(TopDestination.MOVIES, all - MainSection.MOVIES))
        assertNull(nativeEntry(TopDestination.MOVIES, all - MainSection.MOVIES))
        assertEquals(MainSection.SERIES, nativeEntry(TopDestination.SERIES, all - MainSection.MOVIES))
    }
    @Test fun tvAlwaysEntersGridAndGuideRemainsAvailableWithoutReplacingPreference() {
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, all))
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, all, MainSection.EPG))
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, all - MainSection.EPG, MainSection.EPG))
    }
    @Test fun tvAlwaysShowsChannelsWhenLegacyStableSettingsHideLiveButKeepGuide() {
        // The TV tab is always visible. Old navigation preferences must not
        // trap an upgraded user on the legacy EPG screen with no D-pad entry.
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, setOf(MainSection.EPG)))
        assertTrue(nativeTvSelectable(MainSection.LIVE_TV, setOf(MainSection.EPG)))
        assertTrue(nativeTvSelectable(MainSection.LIVE_TV, setOf(MainSection.LIVE_TV)))
        assertFalse(nativeTvSelectable(MainSection.LIVE_TV, emptySet()))
        assertFalse(nativeTvSelectable(MainSection.EPG, setOf(MainSection.EPG)))
        assertEquals(MainSection.LIVE_TV, nativeEntry(TopDestination.TV, all, MainSection.EPG))
        assertNull(nativeEntry(TopDestination.TV, emptySet()))
        assertNull(nativeEntry(TopDestination.MOVIES, emptySet()))
        assertNull(nativeEntry(TopDestination.SERIES, emptySet()))
    }
    @Test fun nativeTvScreensIgnorePersistedLegacyGuideModeAndRetainSeparateEpg() {
        assertEquals(NativeTvSurface.CHANNEL_GRID, nativeTvSurface(MainSection.LIVE_TV))
        assertEquals(NativeTvSurface.PROGRAMME_GUIDE, nativeTvSurface(MainSection.EPG))
        assertNull(nativeTvSurface(MainSection.HOME))
    }
    @Test fun profileAndSettingsRemainReachableWithNoContentOrNetwork() {
        assertEquals(listOf(MainSection.SETTINGS, MainSection.MORE, MainSection.DOWNLOADS),
            nativeRoutes(TopDestination.PROFILE, emptySet()))
        assertEquals(MainSection.SETTINGS, nativeEntry(TopDestination.PROFILE, emptySet()))
    }
    @Test fun backHierarchyTerminatesAndDoesNotSkipContentLevel() {
        for (destination in TopDestination.entries) assertEquals(NativeBack.HEADER, nativeBack(false, destination))
        for (destination in TopDestination.entries - TopDestination.HOME)
            assertEquals(NativeBack.HOME, nativeBack(true, destination))
        assertEquals(NativeBack.EXIT, nativeBack(true, TopDestination.HOME))
    }
    @Test fun adapterDoesNotMutateLegacyVisibilityOrPersistNewIds() {
        val hidden = mutableSetOf(MainSection.EPG, MainSection.MOVIES)
        val visible = all - hidden
        TopDestination.entries.forEach { nativeRoutes(it, visible); nativeEntry(it, visible) }
        assertEquals(setOf(MainSection.EPG, MainSection.MOVIES), hidden)
        assertFalse(nativeRoutes(TopDestination.TV, visible).contains(MainSection.EPG))
    }
}
