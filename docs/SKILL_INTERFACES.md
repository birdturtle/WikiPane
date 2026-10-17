# Skill Wiki entry points

## Existing behavior inspected

RuneLite's current [WikiPlugin](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/plugins/wiki/WikiPlugin.java) adds a RUNELITE `Wiki` entry alongside skill operations on STATS widgets parented by Stats.UNIVERSE. It derives the target from `View <skill> guide` and attaches a browser callback; the new entry does not copy widget parameters. Core Wiki disabled means this entry is absent.

The installed injected-client 1.13.2 menu dispatch was inspected read-only: MenuOptionClicked is posted first, then the attached menu callback is invoked, then consumed state is checked before the normal game action. Consuming a click alone therefore cannot suppress a core Wiki callback. No injected-client modification or runtime introspection is used by the plugin.

Current gameval interfaces include classic SKILL_GUIDE and SKILL_GUIDE_V2. The published RuneStar classic skill-guide row scripts were also inspected; they are historical evidence, not proof of current level-guide actions. Live guide behavior still requires player testing. We do not assume every entry is clickable or has a Wiki action.

## Routing

`SkillInterfaceIntegration` is enabled by `skillLookups OR (replacement AND destination == SIDEBAR)`, matching the accepted quest workflow without requiring another switch in replacement mode. The independent option is off by default and has the third-party request warning. Shift and Quick Inspect preferences do not affect these deliberate Wiki actions.

On MenuOpened, a skill is verified using explicit gameval component-to-Skill mappings, visible widget parent, and the actual View-skill-guide operation. A matching existing RUNELITE Wiki entry gets a sidebar callback, preserving its label, position and original browser callback for opt-out. It must match this menu's verified skill target; arbitrary same-named links elsewhere are not handled. No duplicate entry is added. If core Wiki is disabled, one equivalent local Wiki action is provided; normal skill operations are untouched.

Native level-guide Wiki actions are restricted to CC_OP/CC_OP_LOW_PRIORITY in the two skill-guide interfaces and the exact Wiki/Open wiki/Wiki lookup labels, optionally with a colon. An unambiguous HTTPS Wiki article URL supplied by the widget's operation listener is preserved, including training-page destinations. Otherwise a genuine item ID from the entry/widget resolves through existing canonical entity lookup. Unknown destinations, multiple URLs, untrusted hosts, missing IDs, category controls and prose-only entries are not guessed. No automatic browser interception, crawling or fetching occurs.

For handled native guide clicks, the attached callback is cleared and the event consumed so only the chosen WikiPane route executes. Skills-tab callbacks are rebound when the menu opens because core Wiki's existing callbacks have no reliable widget parameters. The full reader, search/history, requirement annotations and pins are unchanged.

## Lifecycle and privacy

All interface reads occur on the client thread, sidebar mutations use the existing guarded EDT pipeline, and networking/parsing remain on existing workers. Callbacks check the plugin lifecycle generation, login state and current opt-in; stale callbacks cannot navigate into a new plugin instance. Existing core callbacks retain browser behavior on opt-out/shutdown; WikiPane-owned stale actions do nothing and disappear with normal menu reconstruction. No widgets, listeners, overlays, persistent menu registry or secondary cache are introduced.

Only the selected skill/article/item identity is sent to the Wiki. No skill levels, inventory snapshots, account information or credentials are sent. Diagnostic logging is DEBUG and records only interface/routing metadata for deliberate menu use.

## Player tests required

1. With the usual replacement + Sidebar settings, right-click Farming and choose Wiki. Confirm Farming opens in the sidebar without a browser.
2. Repeat for Herblore and Cooking. Ensure the existing View guide and View experience actions are unchanged.
3. If testing standard mode: disable minimap replacement, enable Skill Wiki links in WikiPane, and enable core Wiki. Confirm there is only one Wiki action and it opens the sidebar. Turn off skill routing and confirm the core Wiki browser behavior returns.
4. Open the game's Farming and Herblore level guides. Test any existing Wiki item/training links. Confirm destinations and variants. Report entries that still open a browser; diagnostic logs can identify unsupported paths without assuming the labels or IDs.
5. Navigate Back, pin recipe content, reopen menus, log out/in, and disable/re-enable the plugin. Confirm no duplicate entries or stale article opens.

Current level-guide template/menu coverage is explicitly unverified. Entries without a Wiki action or verified identity are not given speculative new links. No claim of complete guide coverage is made.


## Local progression in level-up tables

`SkillGuideProgress` identifies a supported skill from the canonical `<skill>/Level up table` article title and uses only labeled numeric Level identities from the shared record model. General skill pages and unrelated tables are unaffected. The existing read-only client-thread adapter has a separate skill watch so tables without quest requirements can receive immutable local snapshots without fabricated requirements. Context watching stops when the reader closes; logout clears it, and unknown levels do not dim or position anything. Check your requirements also controls this local context.

Earlier levels are painted at 72% opacity without disabling their text, links or selection. The current row remains at full strength. State refreshes repaint existing rows rather than recreating source panes. No source data or cached article is modified.

Fresh, unanchored article visits position once at the base level, never the boosted level. Missing rows choose the next available level, or the last row if none is later. Lazy batches are materialized through that row before alignment. History entries track that initial positioning has been handled; restored visits and explicit section anchors bypass it. Wheel scrolling, scrollbar interaction and scroll-key bindings cancel pending initial positioning so a late snapshot cannot pull the reader away from their chosen position. Subsequent skill updates only change dimming.

Player checks: open Farming and Sailing level-up tables with known base levels; confirm the appropriate row is at the top and earlier rows are dimmed but links/selection still work. Navigate away and Back, check boosts do not change positioning, and scroll before delayed player state becomes available. Log out/in and toggle local checks. The screenshot preview uses synthetic level 5 Farming state and is not gameplay verification.

Repeated-level regression: separate free-to-play/members rows at the same level all receive local progression styling. Navigation still targets the first row at that level. A synthetic multi-row Magic table verifies both rows update together on base-level changes and return to normal on logout.

The player accepted the current skill-link/table/progression checkpoint on 2026-10-17, including the repeated-level dimming fix. Broader optional-interface and lifecycle cases remain documented above for further testing.
