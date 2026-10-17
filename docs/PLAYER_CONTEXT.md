# Local player requirement checks

Settings → WikiPane → **Check your requirements** enables the comparison. It is enabled by default and can be turned off immediately. This is a local feature: it introduces no Wiki requests or external account service.

Supported requirements retain their complete Wiki wording, links, nesting, scope headings and boost qualifiers. A separate compact line uses a small RuneLite skill icon where available, a symbol and readable current-state text. Green checks mean MET, red crosses mean UNMET, and neutral question marks mean UNKNOWN. Status is not an overall statement that a quest or activity can be completed. Recommended requirements remain under their source recommendation heading.

## Architecture

- `PlayerContextSnapshot` is an immutable account-independent schema containing local base/current skill levels, selected quest completion states and quest points. It contains no account identifier, name, inventory, bank, equipment or credentials.
- `PlayerContextService` is a client-thread adapter. It reads supported RuneLite skill APIs, `VarPlayerID.QP`, and `Quest.getState(client)` for exact quests requested by the current article. The latter uses RuneLite's supported read-only quest-status script, deferred with ClientThread. There are no packets, game actions or injected input.
- `RequirementEvaluator` is a pure three-state evaluator over the existing semantic requirements and a snapshot. It performs no client, HTTP or file operations.
- `ReaderLayout` associates existing requirement rows with their expressions. `ContentRenderer` evaluates those rows on the worker and applies results to separate `RequirementBadge` controls on the EDT. Text nodes with structured requirements can show an aggregate annotation while retaining their entire original text.
- `RequirementBadge` measures its source text at its actual width before reserving status space. Updates replace labels, not the reader, history or scroll position. Status and icons are outside selectable Wiki text, so highlight → right-click → Pin still captures source content only.

Bundled RuneLite skill icons are decoded off the EDT. No extra image server is used. No player data is mixed into cached Wiki documents or view models. Cached articles remain usable while logged out or with checks disabled.

## Rules

- A skill with explicitly allowed boosts uses its current level, including boosts or drains. A not-boostable or unspecified requirement uses base level. Unknown boost permission never permits a temporary boost to satisfy a requirement.
- Exact supported quest article links are matched against RuneLite's quest names. FINISHED satisfies completion; NOT_STARTED and IN_PROGRESS do not. Fragments and unsupported aliases/variants remain unknown. Positive started-quest milestones can be marked met when the entire quest is complete; intermediate progress remains unknown. Nested prerequisite lists show individual quest checks, retain parent relationships, and keep alternatives grouped. Structural list introductions do not receive misleading unknown checks.
- Quest points use the current value only. Explicit reward-accounting notes are retained but future rewards are never added to current points. Conditional or unfamiliar qualifications remain unknown.
- ALL is MET only if all children are MET, UNMET if any child is UNMET, otherwise UNKNOWN. ANY is MET if any child is MET, UNMET only if all children are UNMET, otherwise UNKNOWN. Evaluation is recursive; an empty expression is UNKNOWN.
- The extractor marks supported numeric/quest conditions as verifiable without changing original prose. Extra conditions and ambiguous mixed boost scopes cannot produce a false MET from just one visible number. Unsupported conditions retain their complete original text and an unknown status.
- Inventory/equipment possession, bank ownership, combat eligibility, optional route selection, partial quest milestones and unfamiliar conditional prose are not evaluated in this pass.

## Updates and bounds

The client waits for a logged-in game tick and a local player before reading state. Stats and variable events mark data dirty; game ticks coalesce refreshes. Requested quest scripts refresh at most once per five game ticks following changes, with bounded retries for an unavailable script. There are at most 64 requested quests, a 2048-node request traversal budget and a 32-level recursion bound. The fixed skill list is small. Unrequested quests are not read.

The request follows the existing sidebar activation hooks. Closing the sidebar, opening search or disabling checks stops active comparisons. Logout, world loading/hopping, disconnection and plugin shutdown clear old snapshots. A fresh logged-in tick supplies new state. Unchanged snapshots do not schedule duplicate presentation updates. Generation and snapshot guards prevent pending worker results from restoring old account values or updating disposed rows.

## Contextual menu verification

The existing direct lookup implementation was retained. Mocked API regression checks cover bank/equipment-style CC_OP entries, low-priority and item widgets, ground items, canonical IDs, transformed NPCs and objects, location identity, deduplication, modifier settings and original-menu preservation. Delayed callbacks now reject logout as well as opt-out. In-game Menu Entry Swapper coexistence and actual interface behavior require player testing.

## Manual acceptance

1. Open Recipe for Disaster while logged in. Compare starting Cooking 10 and Cook's Assistant completion with the actual character. The full-completion requirements remain a separate group.
2. Check higher-level requirements and current quest points. Confirm missing levels are red, unsupported conditions remain unknown, and future rewards are not counted.
3. Change a temporary skill boost naturally. Explicitly boostable rows should use current level; unboostable/unspecified rows must use base level. Verify a relevant quest completion after normal gameplay when practical.
4. Scroll, select Wiki text, pin it and navigate Back/Forward. Status refresh must preserve the reading position; pinned text must not include player values.
5. Toggle checks off/on. Close/reopen the sidebar, log out/in, hop worlds, and enable/disable WikiPane. No old account's status should remain. Do not automate any game interaction.
6. Enable contextual lookup and Menu Entry Swapper. Test inventory, bank, equipment, ground item, NPC and object entries, both modifier modes and both destinations. Ordinary game actions must stay unchanged.

Offline preview with synthetic skills/quest states:

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew playerContextPreview -Pcapture=/tmp/wikipane-player-context.png
```

This paints the real WikiPane Swing panel against a captured article. It neither logs into RuneScape nor reads a real character. In-game behavior remains unverified until player testing.

Official API/policy sources inspected:

- https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Client.java
- https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/Quest.java
- https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/game/SkillIconManager.java
- https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features

The installed RuneLite 1.13.2 API and core QuestPointRequirement implementation were also inspected for matching method signatures and quest-point access. This is preparation for Plugin Hub review, not a claim of maintainer approval.
