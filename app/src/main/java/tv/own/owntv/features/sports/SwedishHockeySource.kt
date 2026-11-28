package tv.own.owntv.features.sports

import tv.own.owntv.core.sports.MonthBoard
import tv.own.owntv.core.sports.SportsRefreshPolicy
import tv.own.owntv.core.sports.SportsSource
import tv.own.owntv.core.sports.model.Competitor
import tv.own.owntv.core.sports.model.League
import tv.own.owntv.core.sports.model.Match
import tv.own.owntv.core.sports.model.MatchState
import tv.own.owntv.core.sports.model.MatchSummary
import tv.own.owntv.core.sports.model.Ranking
import tv.own.owntv.core.sports.model.Score
import tv.own.owntv.core.sports.model.Sport
import tv.own.owntv.core.sports.model.StandingsGroup
import tv.own.owntv.core.sports.model.StandingsRow
import tv.own.owntv.core.sports.model.StandingsTable
import tv.own.owntv.core.sports.model.Team
import tv.own.owntv.features.home.Competition
import tv.own.owntv.features.home.HockeyDataException
import tv.own.owntv.features.home.HockeyFailureKind
import tv.own.owntv.features.home.SportFixture
import tv.own.owntv.features.home.SportSnapshot
import tv.own.owntv.features.home.SwedishHockeyData
import tv.own.owntv.features.home.Stockholm
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import tv.own.owntv.features.home.SportsCatalog as SwedishCatalog
import tv.own.owntv.features.home.Team as SwedishTeam

/**
 * Min TV 0.20: the upstream Sports section's source. SHL and HockeyAllsvenskan, which ESPN does not
 * carry, are read from Min TV's existing Swedish hockey reader (same opt-in, cache, robots and rate
 * rules); every other league goes to [other] (ESPN) unchanged.
 */
internal class SwedishHockeySource(
    private val other: SportsSource,
    private val hockey: SwedishHockeyData,
    private val finalText: String,
    private val pair: (String, String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : SportsSource {

    private fun competition(league: League): Competition? = if (league.sport != Sport.HOCKEY) null else when (league.slug) {
        SHL_SLUG -> SwedishCatalog.shl
        ALLSVENSKAN_SLUG -> SwedishCatalog.allsvenskan
        else -> null
    }

    /** [fetch]'s result; on failure [fallback] when there is one, else the failure. */
    private suspend fun <T> guarded(fallback: T?, fetch: suspend () -> T?): T? =
        try { fetch() ?: fallback } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { fallback ?: throw e }

    /**
     * The stored season while it is fresh; otherwise one refresh, which the reader itself throttles and
     * backs off (robots, 403/429). A failed refresh falls back to what is stored.
     */
    private suspend fun snapshot(competition: Competition): SportSnapshot {
        val stored = hockey.cached(competition)
        if (stored != null && clock() - stored.fetchedAt < FRESH_MS) return stored
        return guarded(stored) { hockey.refresh(competition, clock()) }
            ?: throw java.io.IOException("No Swedish hockey schedule for ${competition.id}")
    }

    /** Swedish hockey data turned off (opt-in) with nothing stored: no games, never an "ESPN unreachable". */
    private suspend fun matches(league: League, competition: Competition): List<Match> = try {
        snapshot(competition).fixtures.map { it.toMatch(league) }
    } catch (e: HockeyDataException) {
        if (e.issue.kind == HockeyFailureKind.DISABLED) emptyList() else throw e
    }

    override suspend fun scoreboard(league: League, day: LocalDate?): List<Match> {
        val competition = competition(league) ?: return other.scoreboard(league, day)
        // Core asks for "today" as ESPN's US-Eastern date; for a Swedish league today is Stockholm's.
        val now = clock()
        val date = if (day == null || day == SportsRefreshPolicy.espnDay(now)) Instant.ofEpochMilli(now).atZone(Stockholm).toLocalDate() else day
        return matches(league, competition).filter { Instant.ofEpochMilli(it.startMs).atZone(Stockholm).toLocalDate() == date }
    }

    override suspend fun month(league: League, month: YearMonth): MonthBoard {
        val competition = competition(league) ?: return other.month(league, month)
        val all = matches(league, competition)
        return MonthBoard(all.filter { YearMonth.from(Instant.ofEpochMilli(it.startMs).atZone(Stockholm)) == month },
            all.minOfOrNull { it.startMs })
    }

    override suspend fun teamSchedule(league: League, teamId: String, fixtures: Boolean): List<Match> {
        val competition = competition(league) ?: return other.teamSchedule(league, teamId, fixtures)
        val now = clock()
        return matches(league, competition)
            .filter { m -> m.competitors.any { it.team.id == teamId } }
            .filter { if (fixtures) it.state == MatchState.PRE && it.startMs >= now else it.state == MatchState.POST }
            .let { if (fixtures) it.sortedBy(Match::startMs) else it.sortedByDescending(Match::startMs) }
    }

    override suspend fun summary(match: Match): MatchSummary {
        val league = League(match.sport, match.leagueSlug, match.leagueName)
        if (competition(league) == null) return other.summary(match)
        // The schedule carries no timeline, statistics or lineups: the summary is the match itself.
        return MatchSummary(match, emptyList(), emptyList(), emptyList())
    }

    override suspend fun standings(league: League): StandingsTable {
        val competition = competition(league) ?: return other.standings(league)
        val season = snapshot(competition).seasonId
        val stored = hockey.cachedStandings(competition, season)
        // A table stored before W/T/L were kept is refreshed once (the reader throttles); never shown as zeros.
        val table = stored?.takeIf { t -> t.rows.all { it.won != null && it.tied != null && it.lost != null } }
            ?: guarded(stored) { hockey.standings(competition, season, clock()) }
            ?: throw java.io.IOException("No Swedish hockey table for ${competition.id}")
        val rows = table.rows.map { row ->
            // The same team ID as the schedule's, so a table row opens that team's games.
            val team = (SwedishCatalog.identity(row.team, tv.own.owntv.features.home.Sport.ICE_HOCKEY)
                ?: SwedishCatalog.make(row.team, tv.own.owntv.features.home.Sport.ICE_HOCKEY)).coreTeam()
            StandingsRow(row.rank, team, played = row.played ?: 0, won = row.won ?: 0, drawn = row.tied ?: 0, lost = row.lost ?: 0,
                scored = row.goalsFor ?: 0, conceded = row.goalsAgainst ?: 0,
                difference = row.goalDifference?.let { if (it > 0) "+$it" else "$it" } ?: "", points = row.points)
        }
        return StandingsTable(league.key, listOf(StandingsGroup(league.name, rows)))
    }

    override suspend fun teams(league: League): List<Team> {
        val competition = competition(league) ?: return other.teams(league)
        return SwedishCatalog.inCompetition(competition).map { it.coreTeam() }
    }

    override suspend fun rankings(league: League): Ranking =
        if (competition(league) != null) Ranking(league.key, emptyList()) else other.rankings(league)

    private fun SportFixture.toMatch(league: League): Match {
        val score = score
        val state = when {
            score != null -> MatchState.POST
            else -> MatchState.PRE // A snapshot has no live clock: never call a match live from its start time.
        }
        fun side(team: SwedishTeam, home: Boolean, goals: Int?) = Competitor(team.coreTeam(), home,
            winner = score?.let { if (home) it.home > it.away else it.away > it.home },
            score = goals?.let { Score(it.toString(), it) })
        return Match(
            id = id, sport = Sport.HOCKEY, feed = league.key, leagueSlug = league.slug, leagueName = league.name,
            startMs = faceoff, state = state, statusText = if (state == MatchState.POST) finalText else "",
            title = pair(home, away),
            competitors = listOf(side(homeTeam, true, score?.home), side(awayTeam, false, score?.away)),
            venue = venue,
        )
    }

    private fun SwedishTeam.coreTeam() = Team(id, name)

    companion object {
        const val SHL_SLUG = "swe.shl"
        const val ALLSVENSKAN_SLUG = "swe.allsvenskan"
        private const val FRESH_MS = 30 * 60_000L
    }
}
