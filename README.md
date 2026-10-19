# WikiPane

Read the Old School RuneScape Wiki inside RuneLite.

- Search with suggestions or press Enter for full search results.
- Read articles, quest requirements, item bonuses, NPC information, and walkthroughs.
- Follow Wiki links and section anchors, and navigate Back and Forward.
- Read complete tables as labeled records that fit the sidebar.
- Open any article in your browser.
- Optionally use the in-game Wiki button to open entity lookups in the sidebar.

## Run locally

Requires a Java 11 JDK. From this directory:

```sh
./gradlew run
```

On this machine, where the default Java is newer than the Gradle wrapper supports:

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew run
```

Enable **WikiPane** in RuneLite's plugin settings, then select its **W** sidebar tab.
You can search while logged out. This development client is separate from the RuneLite launched by Cabbage; the plugin does not change Cabbage or handle login credentials.
For logging into the development client with a Jagex Account, follow [RuneLite's instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

An optional isolated RuneLite profile keeps development settings and caches in this project:

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew run -PdevHome="$PWD/.dev/home"
```

## Search and navigation

Type a title or search term. Suggestions appear after a short pause. Press Enter for broader results; click a result to open it. Down Arrow moves from the search field to the results; Up/Down and Enter navigate the result buttons.

While reading, the article title and navigation stay above the content. The **Overview** dropdown and the scrollable article show the same section hierarchy. Overview starts open; other sections start collapsed. Click a section header to expand/collapse it. In the dropdown, use the arrows to browse branches or click a label to open that section and jump to its header. Filtering finds nested sections directly. The selector follows your reading position; Back and Forward preserve both expansion state and position.

Use the magnifying glass to open search. Back returns to your previous results or article; Forward restores where you were reading. The arrow-out icon opens the current article in your browser. All four controls have tooltips.

Recognized recipes show ingredients, quantities, output, skill level, and XP. **Costs & source details** keeps the complete recipe tables available.

Short facts use aligned rows; table entries have their own labeled records. **Show more** reveals additional entries; **Show fewer entries** compacts an expanded table. Technical IDs and MOID links stay under **Technical details**, while release/editorial metadata stays under **Article details**. The variant selector switches between source item or monster variants. Separate strategy and full-guide links open inside the reader. Citation markers open reference cards with a return link to the cited passage. Right-click a section heading to open its Wiki source.

## Quick Inspect

Enable **Right-click lookup → Add WikiPane to entity menus** in WikiPane settings. Choose **Quick Inspect** under **Entity menus: open in** for a temporary overlay; the default opens the full article. Hold Shift and right-click an item, NPC, or object, then choose **WikiPane**. Bank and equipment item menus are supported where RuneLite exposes the item ID.

The small translucent panel leaves your sidebar article in place. Its footer has **Read article**, paging arrows, and **×** to dismiss. Hold Shift and right-click the panel for **Open full article**, related articles, page navigation, retry, or **Dismiss**. Hold Alt to drag it. Choosing the full article adds it to ordinary sidebar history, so Back returns to your previous reading position.

Settings also offer ordinary right-click access and direct full-article lookups. Contextual lookup works alongside RuneLite's Wiki plugin; replacing its minimap button is separate.

**Open Wiki links in WikiPane** also opens the quest list’s existing **Open wiki guide** and **Open wiki quick guide** actions inside WikiPane. Full and quick guides open their respective pages with ordinary Back navigation; Quick Inspect and Shift settings do not affect them.

Compact facts come from the same Wiki data as the reader. Unresolved variants are labeled and their specific stats are hidden. Monster inspections show static statistics, without combat preparation or prayer recommendations.

## Skill Wiki links

With **Open Wiki links in WikiPane** active, right-click a skill in the Skills tab and choose **Wiki** to read it in WikiPane. The existing entry is reused when present.

Supported Wiki actions inside the level-guide window also open in WikiPane when the interface supplies an explicit Wiki destination or item ID. Ordinary guide, experience, category and material-check actions keep their normal behavior.

Skill level-up tables start at your base level when local player state is available. Earlier levels are subtly dimmed and remain readable and clickable. Back/Forward restores your reading position; explicit section links and manual scrolling take priority. This uses **Show character checks** and sends no character levels to the Wiki. Turn **Jump to your skill level** off to retain dimming and checks without automatic positioning.

## Wiki locations on the map

Supported locations have contextual actions such as **Show quest start**, **Show spawn**, or **Show on map**, beside the associated information. The action opens the native floating map, centers the destination, and uses the game's red destination flag. Several verified points in one record form one selection; another selection replaces all previous WikiPane markers. Other plugins' markers stay untouched.

Marker tooltips identify the destination and source article/section. Right-click a WikiPane flag and choose **Read in WikiPane** to return to that source through the existing sidebar navigation. RuneLite's **Focus on** action remains available. Map actions preserve the sidebar's article and reading position.

The persistent **Map ▾** control beside the section selector lists this article's maps and locations. Expand a group to show an individual destination or **Show all on game map**. Map images remain available even when their coordinates cannot be shown natively.

Wiki map previews are reconstructed from the Wiki's own map tiles and explicit feature data. Click a preview to open the larger image viewer with Fit, zoom and pan; the viewer fetches a higher-detail crop where available. Numbered markers match the legend. An incomplete feature notice identifies maps that cannot be fully reproduced. Ordinary map images continue to use the full-resolution image viewer.

Only explicit Wiki map links or structured map features with supported coordinates and a known plane qualify for game markers. A map's viewing center never becomes a destination. General map centers, prose locations and unsupported instance maps keep the usual browser fallback. Markers are cleared on logout, world hopping and plugin shutdown. Map images use HTTPS requests to the Wiki-controlled `maps.runescape.wiki`; no player state is included.

## Check your requirements

Supported requirements show a check, cross or question mark alongside your local skill levels, quest completion or quest points. The original Wiki text stays intact. Boosts count only when the requirement explicitly allows them; unsupported conditions remain unknown.

Turn **Show character checks** off in WikiPane settings if you prefer the plain reader. These comparisons stay on your computer. Inventory, equipment and bank contents are not checked.

## Pin a reference

Highlight text in an article, right-click, and choose **Pin selection**. The selection becomes a compact, translucent overlay immediately. Lists, numbering, emphasis, and field labels are kept.

Click the footer arrows to turn pages. Hold Alt to move a pin. Shift-right-click it for **Open source** or **Unpin**. **Open source** returns to the source of the currently displayed page. Pinning the same selection again highlights the existing pin. Pins stay visible when the sidebar closes. Up to five pins can be kept for the session; disabling WikiPane or restarting RuneLite clears them.

## Wiki integration

Enable **Wiki integration → Open Wiki links in WikiPane** to route the minimap Wiki button, quest guides and supported skill Wiki links into the sidebar. This is the one routing switch; there is no separate browser destination or redundant quest/skill toggle. Browser access stays available through the reader's explicit Open in Browser command.

RuneLite's core **Wiki** plugin can stay enabled. WikiPane temporarily supplies the visible lookup/search button while keeping the core widget intact; its DPS action is forwarded to the original core callback. Turning integration off or disabling WikiPane restores the core widget without changing the core plugin's settings. If core Wiki hides its minimap button, that preference is respected. With core Wiki disabled, WikiPane supplies its own button; DPS is available only through the enabled core plugin.

By default, left-click selects an entity. **Wiki button: left-click search** makes it open sidebar search instead. Right-click lookup is an independent optional entry point, with Shift-right-click and full-article defaults. **Show character checks** controls requirement indicators and skill-guide progression locally.

Existing quest/skill opt-ins migrate into the unified routing switch. Existing right-click destination and player-check preferences are retained.

## Privacy and attribution

Search terms, requested article titles, and selected entity lookup details are sent over HTTPS to the [OSRS Wiki](https://oldschool.runescape.wiki/). Entity requests contain type, ID, name, and, for NPCs or objects, location. The Wiki sees your IP address. Skill levels, quest states and quest points used for requirement checks stay local and are not sent to the Wiki. There is no telemetry, account tracking, credential handling, or external backend.

Wiki text is attributed to its contributors under [CC BY-NC-SA 3.0](https://oldschool.runescape.wiki/w/RuneScape:Copyrights). Rendering changes the layout, not the source information. Images have their own applicable terms, including Jagex copyright. Each article links back to the original.

## Development and testing

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew clean test build
```

The tests use captured Wiki responses and mocked network responses. An explicit live check is available:

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew liveCheck
```

An isolated reader preview requires no game client or login and makes no network requests:

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew preview -Particle="Recipe for Disaster"
```

Right-click the article title, or the WikiPane heading in search, and choose **Composition audit** for local diagnostics. See [presentation validation](docs/PRESENTATION_VALIDATION.md) for the checks and their limits.

See the [UX refinement notes](docs/UX_REFINEMENT.md) for presentation decisions and the manual acceptance checklist.

See [testing](docs/TESTING.md) for the player test checklist and [implementation notes](docs/IMPLEMENTATION.md) for limits and Plugin Hub preparation.

Article images open in a resizable, non-modal Swing viewer when clicked. Press Escape or close the window to dismiss it. Use **Fit**, **100%**, or **−/+** to zoom; drag to pan, or Ctrl-scroll to zoom. Failed downloads offer Retry and Open in browser. Larger images load on demand with bounded decoding and caching; decorative table icons remain non-interactive.

For private testing through Cabbage's developer-plugin mode, run `./gradlew verifyDeveloperPluginJar` and send testers `build/libs/wikipane-0.1.0-developer-plugin.jar`. This includes jsoup but excludes RuneLite and test code. The manifest records the RuneLite version used to compile it, so incompatible client updates require rebuilding the package. Testers select the JAR under Cabbage's **Settings → Advanced → Developer plugins**, enable the mode, then launch their character normally. This local package has not been reviewed by the Plugin Hub.
