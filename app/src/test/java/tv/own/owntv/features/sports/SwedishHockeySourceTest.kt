package tv.own.owntv.features.sports

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.sports.MonthBoard
import tv.own.owntv.core.sports.SportsCatalog as CoreCatalog
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.MatchSummary
import tv.own.owntv.core.sports.model.Ranking
import tv.own.owntv.core.sports.model.StandingsTable
import tv.own.owntv.core.sports.model.Team
import tv.own.owntv.features.home.Competition
import tv.own.owntv.features.home.HockeyTable
import tv.own.owntv.features.home.ShlGame
import tv.own.owntv.features.home.ShlStanding
import tv.own.owntv.features.home.SportSnapshot
import tv.own.owntv.features.home.SportsCatalog
import tv.own.owntv.features.home.Stockholm
import tv.own.owntv.features.home.SwedishHockeyData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class SwedishHockeySourceTest {
    private val now = LocalDateTime.of(2026, 11, 28, 12, 0).atZone(Stockholm).toInstant().toEpochMilli()
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 11, day, hour, 0).atZone(Stockholm).toInstant().toEpochMilli()

    private val games = listOf(
        ShlGame("1", "Färjestad BK", "Skellefteå AIK", at(26, 19), "3-2"),
        ShlGame("2", "Brynäs IF", "Färjestad BK", at(28, 19), null),
        ShlGame("3", "Frölunda HC", "Luleå HF", at(30, 19), null),
    )

    private inner class Data : SwedishHockeyData {
        var refreshes = 0
        override suspend fun cached(competition: Competition) = if (competition == SportsCatalog.shl)
            SportSnapshot(competition, "17567", games.map { SportsCatalog.fixture(competition, it) }, now, null) else null
        override suspend fun refresh(competition: Competition, now: Long): SportSnapshot? { refreshes++; return null }
        override suspend fun standings(competition: Competition, season: String, now: Long): HockeyTable? = null
        override suspend fun cachedStandings(competition: Competition, season: String) =
            HockeyTable(season, listOf(ShlStanding(1, "Färjestad BK", 40, 20, 15), ShlStanding(2, "Frölunda HC", 38, 20, -2)), now)
    }

    private object Espn : SportsSource {
        var calls = 0
        override suspend fun scoreboard(league: League, day: LocalDate?): List<Match> { calls++; return emptyList() }
        override suspend fun summary(match: Match): MatchSummary = error("unused")
        override suspend fun standings(league: League): StandingsTable { calls++; return StandingsTable(league.key, emptyList()) }
        override suspend fun teams(league: League): List<Team> = emptyList()
        override suspend fun month(league: League, month: YearMonth) = MonthBoard(emptyList(), null)
        override suspend fun rankings(league: League) = Ranking(league.key, emptyList())
        override suspend fun teamSchedule(league: League, teamId: String, fixtures: Boolean): List<Match> = emptyList()
    }

    private val data = Data()
    private val source = SwedishHockeySource(Espn, data, "Slut", { h, a -> "$h – $a" }) { now }
    private val shl = CoreCatalog.league("hockey/swe.shl")!!

    @Test fun `SHL and HockeyAllsvenskan are in the upstream catalogue`() {
        assertEquals("SHL", shl.name)
        assertEquals("HockeyAllsvenskan", CoreCatalog.league("hockey/swe.allsvenskan")!!.name)
    }

    @Test fun `a day's SHL games come from the Swedish reader, results as final`() = runBlocking {
        val day = source.scoreboard(shl, LocalDate.of(2026, 11, 26))
        assertEquals(listOf("Färjestad BK – Skellefteå AIK"), day.map { it.title })
        val game = day.single()
        assertEquals(MatchState.POST, game.state); assertEquals("Slut", game.statusText)
        assertEquals(3, game.home!!.score!!.value); assertEquals(true, game.home!!.winner)
        assertEquals(shl.key, game.feed)
        assertEquals(0, data.refreshes)
    }

    @Test fun `a stale season refreshes once and keeps the stored copy when that fails`() = runBlocking {
        val later = SwedishHockeySource(Espn, data, "Slut", { h, a -> "$h – $a" }) { now + 31 * 60_000L }
        assertEquals(1, later.scoreboard(shl, LocalDate.of(2026, 11, 26)).size)
        assertEquals(1, data.refreshes)
    }

    @Test fun `a game without a result is never live from its start time`() = runBlocking {
        val today = source.scoreboard(shl, null).single()
        assertEquals(MatchState.PRE, today.state)
    }

    @Test fun `team schedule and month filter the stored season`() = runBlocking {
        val fbk = SportsCatalog.farjestadId
        val next = source.teamSchedule(shl, fbk, fixtures = true)
        assertEquals(listOf("Brynäs IF – Färjestad BK"), next.map { it.title })
        val done = source.teamSchedule(shl, fbk, fixtures = false)
        assertEquals(listOf("Färjestad BK – Skellefteå AIK"), done.map { it.title })
        assertEquals(3, source.month(shl, YearMonth.of(2026, 11)).matches.size)
    }

    @Test fun `the table maps rank, team, points and goal difference`() = runBlocking {
        val rows = source.standings(shl).groups.single().rows
        assertEquals(listOf(1, 2), rows.map { it.rank })
        assertEquals(SportsCatalog.farjestadId, rows.first().team.id)
        assertEquals("+15", rows.first().difference); assertEquals("-2", rows[1].difference)
        assertEquals(40, rows.first().points)
    }

    @Test fun `every other league still goes to ESPN`() = runBlocking {
        val before = Espn.calls
        source.scoreboard(CoreCatalog.league("hockey/nhl")!!, null)
        source.standings(CoreCatalog.league("soccer/eng.1")!!)
        assertEquals(before + 2, Espn.calls)
        assertTrue(source.teams(shl).any { it.id == SportsCatalog.farjestadId })
    }
}
