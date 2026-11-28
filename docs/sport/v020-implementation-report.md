# Min TV 0.20 — implementation report (running)

Brief: [v020-tv-first-codex-brief.md](v020-tv-first-codex-brief.md). Base: PR #43 branch.

## M1 — Live Sport TV-first (this PR)

### Root causes found by code inspection
1. **Plain sports channels were the last row.** `NativeSportDashboard` put the authorized channel rail
   below the hero, My teams, followed, today, upcoming and attribution rows. Entry focus was the
   match hero, so a plain channel needed up to ~6 D-pad moves before OK.
2. **No programme info on Sport channel cards.** Cards showed only name + "OK plays", so the user had
   to open TV to see what was actually on.
3. **Hidden 48 cap with no "show all".** The rail was silently truncated.
4. **Plain picks were validated against the next match (bug).** In the manual picker with no fixture
   selected, `pick()` called `play(channel, rows)` whose `expected` defaulted to `gameToMatch`. A
   non-hockey channel then returned silently (nothing played), and a hockey channel was checked
   against an unrelated fixture. Fixed: fixture-free picks pass `expected = null`.

### Changes
- New first row **Sport på TV nu**: the existing logical, profile-scoped sports channels
  (`homeSportsChannels` → `logicalHockeyChannels`), shown as soon as the local read finishes and
  independent of any match API or provider opt-in.
- Cards reuse `HomeNowChannelCard` with **stored Now/Next** from the same bounded owner as Home
  (`rememberHomeDashboardGuide`: max 16 keys, stored EPG only, no provider HTTP, 5 s clock, honest
  `at()` interval checks). Missing guide → "Ingen programinformation", still playable.
- **One OK plays the linear channel** (`actions.channel`, existing playback path, `expected = null`).
- **Alla sportkanaler** card ends the rail: fixture-free category/search picker; Back returns straight
  to the Sport card.
- Removed the duplicate bottom channel rail. Hero, My teams and fixture rails unchanged below.
- The row is omitted when no sports channels are identified, so empty profiles keep the existing
  recovery entry (`sport-empty-action`).

### Remote press journeys (from code, synthetic; not measured on a Chromecast)
| Journey | 0.19 | M1 |
| --- | --- | --- |
| Enter Sport → play first sports channel | 1 OK + 1–6 D-pad (depends on fixture rows) | **1 OK, 0 D-pad** |
| Enter Sport → play TV4 Sport Live 3 | 1 OK + 4–9 D-pad | **1 OK + 3 Right** |
| Enter Sport → any sports channel via category | 2+ OK, tied to next match; non-hockey could silently fail | 2+ OK, fixture-free, always plays |

### Tests
- `NativeV011PresentationTest.sportLiveChannelPlaysWithOneOkWithoutMatchData` (new): entry focus on
  the first channel, one OK plays, honest no-guide text, "Alla sportkanaler" opens browse, no
  duplicate rail.
- Updated entry/row navigation expectations in `NativeV011PresentationTest`, and the empty-profile
  leak check in `PerformanceJourneyTest` now targets the new card tag.
- Local: i18n number-locale, text-overflow and literal-inventory checks pass. Kotlin/Android build and
  instrumentation run in GitHub Actions only (no Android SDK in the authoring environment).

## M2 step 1 — One Sport entry (this PR)

### Root cause
Sport was three separate UIs under one top tab: Min TV's dashboard, Min TV's Explore hub, and the
upstream OwnTV Sports section (scores, calendar, leagues, match and team pages). The upstream section
was a page of its own (`MainSection.SPORTS`), reached from Settings → My teams and from the player's
match button, with a different header and no way Back to the Sport start.

### Direction (owner decision)
Upstream Sports is the base for scores, calendar, leagues and match→channel matching. Min TV's hub
stays only for what upstream lacks: Swedish hockey (SHL, HockeyAllsvenskan) and the public-source
consent. Next step: bring SHL/HA into the upstream engine so the hub can be retired.

### Changes
- The upstream section now opens **inside** the Sport destination (`NativeSportScreen`). The shell no
  longer has a separate `SportsScreen` page; every `MainSection.SPORTS` request lands in Sport.
- New **Resultat & kalender** tile in Explore (after Sources) opens it; focus moves into it. Back
  from its first page returns to the tile. Its own pages keep their Back behaviour. (A hero button was
  tried first: the hero action row has no room for a fifth action and truncated it.)
- Settings → My teams and the player's match button open the requested team/match inside Sport.

### Tests
- `ProductionStartupTest`: Sport → Explore → Resultat & kalender opens the section with the Sport tab still
  selected, Back returns focus to the tile. App unit tests for shell/home/sports pass locally (283).

## M2 step 2 — SHL and HockeyAllsvenskan in the upstream engine

- Core (`core/feat/swedish-hockey`, pinned): `SportsCatalog` lists `hockey/swe.shl` and
  `hockey/swe.allsvenskan`.
- App: `SwedishHockeySource` is the upstream section's `SportsSource`. It answers those two leagues
  from Min TV's existing Swedish hockey reader (same opt-in, cache, robots and back-off; refresh only
  when the stored season is older than 30 min, else the stored copy) and passes every other league to
  ESPN. Results are final; a game without a result is never shown live from its start time alone.
- Tests: `SwedishHockeySourceTest` (day, month, team schedule, table, staleness, ESPN pass-through).
- Table: the Swedish association's table has W, T (level after regulation), L and goals but no
  overtime losses, so SHL/HA tables use `M V O F MS P` (`tableColumns`) instead of the NHL set; tables
  cached before these fields existed are refreshed once, never shown as zeros. Unknown table teams keep
  the schedule's team ID. Core's US-Eastern "today" maps to Stockholm's today. With the source turned
  off and nothing stored, Swedish leagues show no games instead of an ESPN error.
- Next: once the upstream pages are checked on device with SHL followed, retire the hub's own SHL
  pages so Sport has a single engine.

## Fix — Sport rows lost remote focus after a guide refresh

The synthetic performance journey failed intermittently on this branch before M2 too (2/2 dispatches
on f4b7461): the "Sport på TV nu" row was hidden whenever channels were reloading, so a background
reload (guide refresh, source sync) removed the focused card and focus fell to the hero. The row now
keeps the last loaded channels until a reload finishes. Test:
`NativeSportHubTest.liveSportRowKeepsFocusWhileChannelsReload` (required in CI).

The decisive cause (shown by a focus dump in CI): on a revisit the live row came back only after its
reload, so header entry restored the card remembered from the previous visit (hero / "Mina lag", below
the row) and Down never reached a channel. Now the last loaded row is kept in memory per profile, so it
is there at once, and header entry (only) lands on the first live sports channel; returns from the hub
and from playback use their own entry and restore the exact card.

### Not done yet (M2–M4)
Unified hub/calendar/My teams, sport filters, ranked Watch Match fallback, performance before/after on
the 12k synthetic library, 720p/1080p screenshots review, Swedish release notes for a QA proposal.

### Manual Chromecast checklist (owner)
- [ ] Sport opens with focus on the first sports channel; OK starts it.
- [ ] Now/Next on cards matches TV; stale/missing guide shows no false programme.
- [ ] Back from playback returns to the same channel card.
- [ ] "Alla sportkanaler" → category → channel plays; Back ×1 from the category list returns to Sport.
- [ ] Hero "Se matchen" / manual picker for a match still behave as in 0.19.
