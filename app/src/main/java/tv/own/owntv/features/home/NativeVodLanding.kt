package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import tv.own.owntv.core.metadata.MetadataType
import tv.own.owntv.features.discovery.*
import tv.own.owntv.features.shell.components.VodPosterArt
import tv.own.owntv.features.shell.components.vodRating
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StagePoster
import tv.own.owntv.ui.theme.mpx

/** One tab's content (Film or Serier): already-read discovery rows, never a network fetch. */
internal data class NativeVodSnapshot(val profile: Long?, val type: MetadataType, val rows: List<DiscoveryRow>,
    val resumes: List<SmartResume>, val loading: Boolean = false)

/** Rows that end in "Show all": the user's own lists and provider folders. Rankings and genres are previews only. */
internal fun DiscoveryRow.opensAll(): Boolean = folder != null || kind == DiscoveryKind.FAVORITES || kind == DiscoveryKind.HISTORY

internal fun vodRowId(row: DiscoveryRow) = "vod-row-${row.key}"
internal fun vodCardKey(row: DiscoveryRow, card: DiscoveryCard) = "vod-${row.key}-${card.stableKey}"
internal fun vodAllKey(row: DiscoveryRow) = "vod-${row.key}-all"
internal const val VOD_SEARCH_KEY = "vod-search"

/**
 * Min TV 0.21 Film / Serier tab: Continue watching, then the profile's Favourites and Recently watched,
 * then one row per provider folder and the local recommendations. No sub-menus: the only way deeper is a
 * title's details or a row's "Show all". Every card is a real focus target owned by [rememberNativeRowsNavigation],
 * so ▲▼ move row to row and ◀▶ along a row without nearest-rectangle jumps.
 */
@Composable
internal fun NativeVodLanding(data: NativeVodSnapshot, first: FocusRequester, onEntry: ((() -> Boolean)?) -> Unit,
    onHeader: () -> Unit, onSearch: () -> Unit, onShowAll: (DiscoveryRow) -> Unit, onTitle: (DiscoveryCard) -> Unit,
    onResume: (SmartResume) -> Unit, modifier: Modifier = Modifier) {
    val movie = data.type == MetadataType.MOVIE
    val resumes = data.resumes.take(6)
    val empty = resumes.isEmpty() && data.rows.isEmpty()
    val rows = buildList {
        if (empty) add(NativeRow(VOD_SEARCH_KEY, listOf(VOD_SEARCH_KEY)))
        if (resumes.isNotEmpty()) add(NativeRow("vod-continue", resumes.map { "vod-resume-${it.key}" }))
        data.rows.forEach { row ->
            add(NativeRow(vodRowId(row), row.cards.map { vodCardKey(row, it) } + listOfNotNull(vodAllKey(row).takeIf { row.opensAll() })))
        }
    }
    val nav = rememberNativeRowsNavigation(rows, data.profile, first, onEntry, onHeader)
    BoxWithConstraints(modifier.fillMaxSize().background(DesignLabBackground)) {
        val resumeWidth = (maxWidth - 164.mpx) / 3
        LazyColumn(state = nav.list, contentPadding = PaddingValues(horizontal = 64.mpx, vertical = 28.mpx),
            verticalArrangement = Arrangement.spacedBy(26.mpx), modifier = Modifier.fillMaxSize().then(nav.modifier).focusGroup()) {
            if (empty) item(key = VOD_SEARCH_KEY) {
                Column(verticalArrangement = Arrangement.spacedBy(18.mpx)) {
                    TvText(stringResource(if (data.loading) R.string.mintv_companion_loading else R.string.mintv_v021_vod_empty),
                        size = 22, color = MinTvMuted, lines = 2)
                    HomeButton(stringResource(if (movie) R.string.mintv_v021_search_movies else R.string.mintv_v021_search_series),
                        onSearch, nav.item(VOD_SEARCH_KEY).testTag(VOD_SEARCH_KEY))
                }
            }
            if (resumes.isNotEmpty()) item(key = "vod-continue") {
                Column(verticalArrangement = Arrangement.spacedBy(12.mpx)) {
                    TvText(stringResource(R.string.search_continue), size = 26, bold = true)
                    LazyRow(state = nav.rails.getValue("vod-continue"), horizontalArrangement = Arrangement.spacedBy(18.mpx), contentPadding = PaddingValues(5.mpx)) {
                        items(resumes, key = { it.key }) { item ->
                            SmartResumeCard(item, { onResume(item) }, nav.item("vod-resume-${item.key}").testTag("vod-resume-${item.key}").width(resumeWidth))
                        }
                    }
                }
            }
            items(data.rows, key = { vodRowId(it) }, contentType = { "vod-row" }) { row ->
                Column(verticalArrangement = Arrangement.spacedBy(10.mpx)) {
                    TvText(discoveryRowTitle(row, data.type), size = 26, bold = true)
                    LazyRow(state = nav.rails.getValue(vodRowId(row)), horizontalArrangement = Arrangement.spacedBy(20.mpx), contentPadding = PaddingValues(8.mpx)) {
                        items(row.cards, key = { it.stableKey }, contentType = { "poster" }) { card ->
                            val key = vodCardKey(row, card)
                            StagePoster(title = card.name, rating = card.rating?.takeIf { it > 0 }?.let(::vodRating),
                                line = card.year?.toString(), width = 176.mpx, height = 264.mpx, onClick = { onTitle(card) },
                                modifier = nav.item(key).testTag(key)) {
                                VodPosterArt(card.poster, if (movie) OwnTVIcon.MOVIES else OwnTVIcon.SERIES)
                            }
                        }
                        if (row.opensAll()) item(key = "all", contentType = "all") {
                            val key = vodAllKey(row)
                            HomeButton(stringResource(R.string.mintv_v021_show_all), { onShowAll(row) }, nav.item(key).testTag(key))
                        }
                    }
                }
            }
        }
    }
}
