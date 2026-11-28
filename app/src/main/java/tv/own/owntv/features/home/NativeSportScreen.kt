package tv.own.owntv.features.home

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.features.sporthub.SportHubScreen
import tv.own.owntv.features.sporthub.*
import tv.own.owntv.features.sports.SportsPlayback
import tv.own.owntv.features.sports.SportsScreen
import org.koin.compose.koinInject

/** A new destination composed from the existing singleton-backed companions, not new repositories. */
@Composable
internal fun NativeSportScreen(liveVm: LiveViewModel, homeVm: HomeViewModel, profileId: Long?, active: Boolean,
    onPlay: (ChannelEntity, List<ChannelEntity>) -> Unit,
    onSources: () -> Unit, onSettings: (MinTvSettingsDestination) -> Unit,
    onEntryHook: ((() -> Boolean)?) -> Unit, onHeader: () -> Unit = {}, onChildFocused: () -> Unit, modifier: Modifier = Modifier,
    restoreAfterPlayback: Boolean = false, onPlaybackReturnFinished: () -> Unit = {}) {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val usable = active && lifecycle == Lifecycle.State.RESUMED
    val home by liveVm.nativeHomeFavorites.collectAsStateWithLifecycle()
    val library by liveVm.homeLibraryState.collectAsStateWithLifecycle()
    val favorites = home.channels.takeIf { home.profileId == profileId && library.profileId == profileId }.orEmpty()
        .filter { it.sourceId in library.sourceIds && library.visibleFavoriteIds?.contains(it.id) != false }
    var returnEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var baseEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var hubReturnEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var hubEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var restoreBase by remember(profileId) { mutableStateOf(false) }
    var hubOpen by rememberSaveable(profileId) { mutableStateOf(false) }
    var hubRoute by rememberSaveable(profileId) { mutableStateOf(HubRoute(HubPage.DISCOVER).key) }
    val hubReads by HubSessionSnapshots.reads.collectAsStateWithLifecycle()
    // 0.20: one Sport. The upstream Sports section (scores, calendar, leagues and their match/team pages)
    // opens inside this destination: from the hub's "Resultat & kalender" tile (Back returns to that
    // tile) or from Settings / the player (Back returns to the Sport start). The hub stays for Swedish
    // hockey (SHL, HockeyAllsvenskan) and the public-source consent until upstream covers them.
    val sportsPlayback = koinInject<SportsPlayback>()
    val pendingTeam by sportsPlayback.pendingTeam.collectAsStateWithLifecycle()
    val pendingMatch by sportsPlayback.pendingMatch.collectAsStateWithLifecycle()
    var sportsOpen by rememberSaveable(profileId) { mutableStateOf(false) }
    var sportsEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    // Header entry only: the first live sports channel when that row exists (returns use returnEntry).
    var liveEntry by remember(profileId) { mutableStateOf<(() -> Boolean)?>(null) }
    var focusSports by remember(profileId) { mutableStateOf(false) }
    var refocusHub by remember(profileId) { mutableStateOf(false) }
    LaunchedEffect(pendingTeam, pendingMatch) {
        if (pendingTeam != null || pendingMatch != null) { hubOpen = false; sportsOpen = true; focusSports = true }
    }
    fun openSports() { restoreBase = false; sportsOpen = true; focusSports = true }
    fun closeSports() {
        sportsOpen = false; focusSports = false
        if (hubOpen) refocusHub = true else restoreBase = true
    }
    LaunchedEffect(refocusHub, usable, hubOpen, sportsOpen, hubEntry) {
        if (refocusHub && usable && hubOpen && !sportsOpen && hubEntry != null) {
            repeat(2) { withFrameNanos { } }
            if (hubEntry?.invoke() == true) refocusHub = false
        }
    }
    // The section draws over the dashboard: move remote focus onto it once its entry is known.
    LaunchedEffect(focusSports, sportsOpen, usable, sportsEntry) {
        if (focusSports && sportsOpen && usable && sportsEntry != null) {
            repeat(2) { withFrameNanos { } }
            if (sportsEntry?.invoke() == true) focusSports = false
        }
    }
    fun openHub(route: HubRoute) { restoreBase = false; hubRoute = route.key; hubOpen = true }
    LaunchedEffect(restoreBase, usable, hubOpen, sportsOpen, returnEntry) {
        if (restoreBase && usable && !hubOpen && !sportsOpen && returnEntry != null) {
            repeat(2) { withFrameNanos { } }
            if (returnEntry?.invoke() == true) restoreBase = false
        }
    }
    DisposableEffect(profileId, hubOpen, sportsOpen, baseEntry, hubEntry, sportsEntry, liveEntry) {
        val header = baseEntry?.let { base -> { liveEntry?.invoke() == true || base() } }
        onEntryHook(if (sportsOpen) sportsEntry else if (hubOpen) hubEntry else header)
        onDispose { onEntryHook(null) }
    }
    tv.own.owntv.features.shell.NativePlaybackContentEntry(restoreAfterPlayback, profileId.toString(),
        if (sportsOpen) sportsEntry else if (hubOpen) hubReturnEntry else returnEntry, onHeader, onPlaybackReturnFinished)
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }.focusGroup()) {
        MinSportCompanion(true, usable && !hubOpen && !sportsOpen, liveVm, profileId, favorites, onPlay, 64.mpx, onSources,
            reservedTop = 32.mpx, compact = false, onSettings = { onSettings(MinTvSettingsDestination.SPORT) },
            dashboard = { data, actions, first, entry -> NativeSportDashboard(data, actions, first, entry, onHeader,
                onExplore = if (profileId != null) ({ openHub(HubRoute(HubPage.DISCOVER)) }) else null,
                hubReads = hubReads, onHubTeam = { openHub(HubRoute(HubPage.TEAM,it.league,it.id)) },
                onHubMatch = { openHub(HubRoute(HubPage.MATCH,it.league,it.id)) }, onHubMine = { openHub(HubRoute(HubPage.MINE)) }, onLiveEntry = { liveEntry = it }) },
            onMatchcenterEntry = { baseEntry = it }, onPlaybackReturnEntry = { returnEntry = it }) { hockey, _ -> hockey() }
        if (hubOpen && profileId != null) SportHubScreen(profileId, usable && !sportsOpen, liveVm, favorites, onPlay,
            { onSettings(MinTvSettingsDestination.SPORT) }, {
                restoreBase = true
                hubOpen = false
            }, { hubEntry = it }, onHeader, HubRoute.decode(hubRoute) ?: HubRoute(HubPage.DISCOVER),
            restoreAfterPlayback = restoreAfterPlayback, onPlaybackEntry = { hubReturnEntry = it }, onScores = ::openSports)
        if (sportsOpen && profileId != null) {
            // Composed before SportsScreen, so its pages' own Back handlers take precedence over this one.
            androidx.activity.compose.BackHandler(enabled = usable) { closeSports() }
            SportsScreen(onPlayChannel = onPlay, onAddPlaylist = onSources, onChildFocused = onChildFocused,
                onEntryHook = { sportsEntry = it }, contentStart = 64.mpx, active = usable,
                modifier = Modifier.fillMaxSize().background(DesignLabBackground).testTag("sport-scores"))
        }
    }
}
