package tv.own.owntv.features.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.movies.MovieViewModel
import tv.own.owntv.features.movies.MoviesScreen
import tv.own.owntv.features.series.SeriesViewModel
import tv.own.owntv.features.series.SeriesScreen

internal enum class NativeVodMode { LANDING, LIBRARY, MOVIE_DETAILS, SERIES_DETAILS }

/** Where a row's "Show all" leads: the existing grid, pinned to that one list (no category rail). */
internal fun vodShowAllKey(row: DiscoveryRow): tv.own.owntv.core.live.LiveKey? = when {
    row.folder != null -> tv.own.owntv.core.live.LiveKey.Folder(row.folder.id)
    row.kind == DiscoveryKind.FAVORITES -> tv.own.owntv.core.live.LiveKey.Favorites
    row.kind == DiscoveryKind.HISTORY -> tv.own.owntv.core.live.LiveKey.History
    else -> null
}

/**
 * One Film or Serier tab (Min TV 0.21). Presentation router only: existing VMs/details, access checks and the
 * player remain authoritative. The landing reads the cached discovery shelf for this one type.
 */
@Composable
internal fun NativeVodHost(profile: Long?, section: MainSection, active: Boolean, movieVm: MovieViewModel,
    seriesVm: SeriesViewModel, onSection: (MainSection) -> Unit, onFullscreen: (MainSection) -> Unit,
    onChildFocused: () -> Unit, onHeader: () -> Unit, onEntry: ((() -> Boolean)?) -> Unit,
    onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val db: OwnTVDatabase = koinInject()
    val custom: CustomizationStore = koinInject()
    val repository: DiscoveryRepository = koinInject()
    val type = if (section == MainSection.SERIES) MetadataType.TV else MetadataType.MOVIE
    val access by remember { activeProfileSources(settings, db.sourceDao()) }.collectAsStateWithLifecycle(initialValue = null)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var mode by rememberSaveable(profile, type) { mutableStateOf(NativeVodMode.LANDING) }
    var libraryKey by remember(profile, type) { mutableStateOf<tv.own.owntv.core.live.LiveKey?>(null) }
    var returnAfterPlayer by rememberSaveable(profile, type) { mutableStateOf(false) }
    val first = remember(profile, type) { FocusRequester() }
    var landingEntry by remember(profile, type) { mutableStateOf<(() -> Boolean)?>(null) }
    var childEntry by remember(profile, type) { mutableStateOf<(() -> Boolean)?>(null) }
    var restore by remember(profile, type) { mutableStateOf(false) }
    val currentMode by rememberUpdatedState(mode)
    val enterLanding by rememberUpdatedState(landingEntry)
    val enterChild by rememberUpdatedState(childEntry)
    val report by rememberUpdatedState(onEntry)
    DisposableEffect(profile, type) {
        report { (if (currentMode == NativeVodMode.LANDING) enterLanding else enterChild)?.invoke() ?: false }
        onDispose { report(null) }
    }
    val foreground = active && lifecycle == Lifecycle.State.RESUMED && profile != null && access?.profileId == profile
    val shelf = rememberDiscovery(type, mode == NativeVodMode.LANDING, foreground && mode == NativeVodMode.LANDING, profile ?: -1L)
    val visibility by remember(profile, type) { custom.observe(profile ?: -1, if (type == MetadataType.MOVIE) MediaType.MOVIE else MediaType.SERIES) }
        .collectAsStateWithLifecycle(initialValue = null)
    // Clear immediately on ownership/visibility changes, before the bounded progress reread finishes.
    var resumes by remember(profile, type, access, visibility) { mutableStateOf<List<SmartResume>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var variants by remember(profile, type) { mutableStateOf<DiscoveryCard?>(null) }
    val requestedMovie by movieVm.detailsRequest.collectAsStateWithLifecycle()
    val openedSeries by seriesVm.openedSeries.collectAsStateWithLifecycle()
    LaunchedEffect(requestedMovie, openedSeries?.id, type) {
        if (type == MetadataType.MOVIE && requestedMovie?.let { it.second == profile } == true && mode == NativeVodMode.LANDING) mode = NativeVodMode.MOVIE_DETAILS
        else if (type == MetadataType.TV && openedSeries != null && mode == NativeVodMode.LANDING) mode = NativeVodMode.SERIES_DETAILS
    }
    LaunchedEffect(active) {
        if (!active && mode != NativeVodMode.LANDING && mode != NativeVodMode.LIBRARY) returnAfterPlayer = true
        if (active && returnAfterPlayer) {
            returnAfterPlayer = false; seriesVm.closeSeries(); mode = NativeVodMode.LANDING; restore = true
        }
    }
    LaunchedEffect(profile, type, access, visibility, foreground, mode) {
        if (!foreground || access?.profileId != profile || visibility == null) return@LaunchedEffect
        val kind = if (type == MetadataType.MOVIE) SmartResumeKind.MOVIE else SmartResumeKind.EPISODE
        try { resumes = readSmartResumes(db, settings, custom, profile).filter { it.kind == kind }.take(6) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { resumes = emptyList() }
    }
    LaunchedEffect(restore, mode, shelf) {
        if (restore && mode == NativeVodMode.LANDING) { repeat(2) { withFrameNanos { } }; if (enterLanding?.invoke() == true) restore = false }
    }
    fun landing() { mode = NativeVodMode.LANDING; libraryKey = null; variants = null; restore = true }
    fun showAll(row: DiscoveryRow) {
        val key = vodShowAllKey(row) ?: return
        val owner = profile ?: return
        scope.launch {
            if (row.folder != null && !repository.folderAvailable(row.folder, owner, type)) return@launch
            if (settings.activeProfileId.first() != owner) return@launch
            libraryKey = key; variants = null; mode = NativeVodMode.LIBRARY
        }
    }
    fun title(card: DiscoveryCard) {
        if (card.variants.isNotEmpty()) { variants = card; return }
        val owner = profile ?: return
        scope.launch {
            if (!repository.available(card, owner) || settings.activeProfileId.first() != owner) return@launch
            if (card.type == MetadataType.MOVIE) {
                movieVm.requestDetails(card); mode = NativeVodMode.MOVIE_DETAILS
            } else {
                val show = db.seriesDao().getSeriesById(card.id) ?: return@launch
                if (CustomizeKeys.series(show) != card.stableKey || !repository.available(card, owner) || settings.activeProfileId.first() != owner) return@launch
                seriesVm.openSeries(show); mode = NativeVodMode.SERIES_DETAILS
            }
        }
    }
    fun resume(item: SmartResume) {
        val owner = profile ?: return
        scope.launch {
            val actual = readSmartResumes(db, settings, custom, owner, exact = item).firstOrNull { it.key == item.key } ?: return@launch
            if (settings.activeProfileId.first() != owner) return@launch
            if (actual.kind == SmartResumeKind.MOVIE) {
                if (movieVm.playByIdAsync(actual.itemId, actual.position) && !movieVm.externalPlayerOn.value) onFullscreen(MainSection.MOVIES)
            } else if (seriesVm.playFromHomeAsync(actual.seriesId ?: return@launch, actual.itemId, actual.position) && !seriesVm.externalPlayerOn.value) onFullscreen(MainSection.SERIES)
        }
    }
    BackHandler(mode != NativeVodMode.LANDING) { landing() }
    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        NativeVodLanding(NativeVodSnapshot(profile, type, shelf.rows, resumes, loading = foreground && shelf.rankedAt == null && shelf.rows.isEmpty() && !shelf.failed),
            first, { landingEntry = it }, onHeader, onSearch, ::showAll, ::title, ::resume)
        if (mode != NativeVodMode.LANDING) Box(Modifier.fillMaxSize().background(MinTvNavy)) {
            val locked = libraryKey.takeIf { mode == NativeVodMode.LIBRARY }
            if (type == MetadataType.MOVIE) MoviesScreen({ onFullscreen(MainSection.MOVIES) }, onChildFocused, lockedKey = locked,
                onEntryHook = { childEntry = it }, onDetailsClosed = { if (mode == NativeVodMode.MOVIE_DETAILS) landing() })
            else SeriesScreen({ onFullscreen(MainSection.SERIES) }, onChildFocused, lockedKey = locked,
                onEntryHook = { childEntry = it }, onSeriesClosed = { if (mode == NativeVodMode.SERIES_DETAILS) landing() })
        }
        variants?.let { card -> ProviderVariantPicker(card,
            onSelect = { variants = null; title(it.copy(variants = emptyList())) },
            onAll = { shelf.rows.firstOrNull { row -> row.cards.any { it.stableKey == card.stableKey } && row.folder != null }?.let(::showAll) ?: run { variants = null } },
            onDismiss = { variants = null; restore = true }) }
    }
}
