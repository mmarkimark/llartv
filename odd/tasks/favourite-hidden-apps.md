Objective: issue #8 (v1.3) — manage the dock favourites and the hidden apps with the TV remote.
Branch: feat/v1-3-favourite-hidden-apps (from main 1ea2d47). Delivery: local work-unit commits; push/PR need owner approval.
Tests: JUnit (`./gradlew testDebugUnitTest`, JAVA_HOME=~/.local/jdk17), shellcheck, checks on AVD `tv11` and the MiTV-MSSP3.

## Specs

- S1 Issue #8: "Add TV-remote controls to select and reorder favourite apps, and hide/unhide apps from the grid.
  Reuse the current dock, hidden-app and column/capacity rules, preserving configured order and compatibility with
  scripts/config.sh. Hiding an app must not uninstall or disable it."
- S2 Owner decision (2026-10-30) on the interaction: "Prémer OK llarg sobre l'app (recomanat)" — holding OK on an
  app (dock or grid) opens a small action menu: *Afegir al dock* or *Treure del dock*, *Moure a l'esquerra* /
  *Moure a la dreta* (dock apps only), *Amagar* and *Informació de l'app*. Hidden apps are recovered from the
  settings panel, in a row *Apps amagades*.
- S3 A short OK press keeps launching the app as today. The MENU key keeps opening the app info as today.
- S4 Owner decision (2026-10-30) on removing from the dock: "Es queda buit si el dock l'has triat tu (recomanat)" —
  when home.conf has a `dock=` list (made from the menu or with `config.sh --dock`), the dock shows only those apps,
  even if fewer than the columns; the automatic fill from the grid only applies to the built-in default list (and
  dock.txt). This changes behaviour for an existing short `dock=` list and must be documented in the CHANGELOG.
- S5 Issue #8: "Respect existing dock capacity and hidden/dock interaction; document that interaction rather than
  silently changing it." Capacity = columns: *Afegir al dock* on a full dock does nothing to home.conf and shows a
  short message. A hidden app is never in the dock (existing rule: hidden apps are filtered before the dock is
  built); hiding a dock app therefore removes it from the dock, and this is documented.
- S6 Issue #8: "Select, reorder and remove favourites using only the remote; saved order appears correctly on Home
  after restart." Edits write `dock` and `hidden` to home.conf through the existing atomic Config write, keeping
  every other line; Home then reloads its apps.
- S7 Issue #8: "Navigation remains usable with empty selections, removed apps and newly installed apps, without
  losing a recoverable focus position." After an edit the focus stays on the moved app, or on a neighbour when the
  app left the row; an empty dock moves the focus to the grid (existing rule).
- S8 Issue #8: "Existing dock/hidden/columns configurations and ADB edits remain supported; update docs and repeat
  the shared performance checks."

## Tasks

- T1 [S1-S8] feat: pure logic (dock/hidden edits, dock composition with the S4 fill rule, OK long-press detection)
  with JVM tests written first; Home action menu on long OK; settings row *Apps amagades* to unhide; docs (README,
  CHANGELOG incl. the S4 behaviour change, config.sh help if needed) — route: gentle-ai-worker; status: DONE pending
  parent verification (no commit yet)
- T2 [S2-S8] verify on the emulator and the TV: short OK launches, long OK opens the menu, add/remove/move/hide,
  unhide from the panel, restart keeps the order, full dock refused, focus recovery; measure.sh; restore
  home.conf — route: inline; commit: n/a

## Log

- L1 User: "si" to start #8, the last open v1.3 issue.
- L2 Asked how to manage the apps with the remote. User selected "Prémer OK llarg sobre l'app (recomanat)" (value:
  long-press).
- L3 Found HomeActivity.java:536 fills an incomplete dock from the grid, which would undo *Treure del dock*. Asked;
  user selected "Es queda buit si el dock l'has triat tu (recomanat)" (value: keep-empty).
- L4 T1 built (worker, no device): HomeApps (pure: dock/hidden edits returning the new lists and which lines
  change; compose() with the S4 rule — an explicit `dock=` shows only its installed, non-hidden apps, in order,
  capped at columns, with no fill, while the built-in list and dock.txt are still filled from the sorted grid)
  and OkPress (a down/repeat/up state machine with the timeout passed in: a hold fires once and swallows the
  click, a short press clicks on release). Config gained `dockSet`, so an empty `dock=` value stays an
  explicitly empty dock. Home: OK in DOCK and GRID now acts on the release (launch) or, held, opens the action
  menu (the Inputs popup generalised as showMenu(anchor, backTo); the move that would be a no-op is hidden; a
  full dock toasts and writes nothing); edits go through setInText + the atomic Config.write and reload with
  the focus following the app (pendingPkg in bindApps). Settings: the *Apps amagades* row shows the count
  ("Cap"/"N apps") and OK opens the label-sorted list (package name when not installed), unhiding by writing
  the remaining hidden line. Tests RED first (74 compile errors: HomeApps, OkPress and Config.dockSet missing),
  then GREEN: 172 tests, 0 failures, 0 errors (HomeAppsTest 23, OkPressTest 6, ConfigTest 18 with the new
  dockSet pin); `./gradlew testDebugUnitTest assembleDebug -q` passes. Docs: README (Home features, D-pad,
  dock/hidden settings rows), CHANGELOG (Added plus the S4 Changed entry saying a short `dock=` list is no
  longer filled), config.sh help line (shellcheck -x clean). Judgement calls flagged for the owner: empty
  `dock=` is now an explicit empty dock; hide writes only `hidden` (the existing filter removes a hidden app
  from the dock); OK repeats no longer re-fire in TOP/MENU; an empty hidden list writes `hidden=`.
- L5 Parent T2 part 1 on the MiTV-MSSP3 (.state/apps-menu/check.sh): short OK launched the focused dock app
  (Spotify); long OK opened the menu (dock: Treure / Moure a l'esquerra / Amagar / Informació; grid: Afegir /
  Amagar / Informació); Treure wrote a 5-app `dock=` with no fill (S4); the focus moved to the neighbour (HBO Max)
  and Moure a l'esquerra swapped it with Prime Video; a restart kept the order; home.conf restored exactly.
  Found: the menu showed at the top of the screen, far from the tile. Cause (pre-existing in the Inputs popup too):
  `setY()` works through the translation and the slide-in then reset `translationY` to 0. Fixed with
  MenuPlacement.top (below the anchor when it fits, otherwise above, never off screen; 3 tests written first and
  observed failing) and an animation of `y` itself. 175 tests pass. Part 2 (placement on the TV, the Inputs menu,
  hiding a grid app, unhiding from the panel) is blocked: the TV went `offline` before the script changed anything.
