# Quest guide routing

Enable **Open Wiki links in WikiPane**, then use the game quest list's existing **Open wiki guide** or **Open wiki quick guide** action. Each opens its corresponding Wiki article in the sidebar with normal history, requirement annotations and pinning.

No extra WikiPane quest entry is added. Read journal, Show on map and unrelated browser links remain unchanged. Quest routing is enabled by `replacement` (the unified Open Wiki links in WikiPane setting). Quick Inspect and Shift preferences do not affect it. Existing config keys are retained. Browser behavior applies when neither opt-in is active; an explicit quest opt-in still routes to the sidebar when the minimap destination is Browser.

## Resolution and safety

`QuestInterfaceLookup` accepts only the two informational guide actions (with or without the game’s trailing colon), with CC_OP or CC_OP_LOW_PRIORITY in the gameval quest-list LIST or TEXT_CONTAINER. The referenced dynamic child must be visible and match a full RuneLite quest-catalog name; a nonempty menu target must agree. Formatting, whitespace and apostrophe normalization do not infer aliases. Child indexes are never interpreted as quest IDs.

On a supported click, the menu event is consumed before the game widget's browser action runs. Full guides use the canonical quest title; quick guides use its `/Quick guide` subpage. Unknown names, missing rows, unrelated actions/interfaces, already-consumed events, logout and widget target selection are left untouched. No global browser interception, reflection, widget mutation, game input or server action is involved.

The plugin's active/lifecycle guards protect deferred sidebar navigation. Article fetching and parsing reuse the existing worker pipeline; only the selected title is sent to the Wiki. No player-state data is sent.

Current RuneLite QuestListPlugin, WikiPlugin, Quest, gameval.InterfaceID and rejected-features guidance were inspected for the original entry point. The player confirmed the live list exposed the quest row and its existing guide actions. Live MediaWiki query on 2026-10-17 verified Cook's Assistant/Quick guide and Recipe for Disaster/Quick guide exist. The player subsequently confirmed the corrected routing works in the development client on 2026-10-17.

## Validation

Mocked-client tests cover full/quick destinations, event consumption, repeated consumption prevention, original menu preservation, no added entries, colored names, apostrophes, RFD, child identity independent of order, unrelated widgets/headers, conflicting targets, missing/hidden rows, opt-out, logout and target selection. They do not establish live browser suppression.

Player checks:

1. Enable Quest guides in WikiPane; use ordinary right-click on Cook's Assistant, then Open wiki guide. Confirm the article opens and no browser launches.
2. Choose Open wiki quick guide. Confirm the Quick guide article opens; Back restores the previous article and position.
3. Repeat both actions on Recipe for Disaster, including with the quest list filtered/reordered.
4. Confirm Read journal and Show on map retain normal behavior, no extra WikiPane quest entry appears, and the guide actions work without Shift.
5. Disable both quest routing and minimap replacement (or select Browser for the minimap with quest routing off) and confirm normal browser behavior returns. Repeat enable/disable and logout/login.

Detailed quest-journal and skill-guide entry points remain separate follow-ups.

Menu-label regression: the player reported browser launches with the integration enabled. The screenshot’s guide labels include a trailing colon, which the initial exact matcher rejected. Tests now exercise both game punctuation and unpunctuated labels, plus colored labels, while journal/map actions remain unconsumed. Debug diagnostics record only routing flags and interface IDs for deliberate guide clicks, without quest names or player data. The subsequent configuration-flow correction was accepted in-game.

Live-flow investigation: both reported clicks reached MenuOptionClicked as CC_OP on Questlist.LIST (component 26148871), with target selection inactive. Logs showed the handler rejecting them at the independent quest opt-in; the active replacement/sidebar configuration was not consulted. Replacement/sidebar now enables quest routing directly. A matrix test covers all eight combinations of replacement, separate quest opt-in and sidebar/browser destination, with both observed colon-suffixed guide actions. This fixes the evidenced gate failure; the player confirmed the corrected build works in-game on 2026-10-17.
