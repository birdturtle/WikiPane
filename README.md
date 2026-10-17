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

While reading, the article title and navigation stay above the content. Use **Overview** and the primary mode for that article: Walkthrough for quests, Combat for monsters, Stats for items, or Training for skills. Each mode remembers its reading position.

**Sections** opens the article's contents, including requirements, chapters, drops, rewards, and references. Filter the list or select a heading to jump directly to it. Section jumps participate in Back and Forward history.

Use the magnifying glass to open search. Back returns to your previous results or article; Forward restores where you were reading. The arrow-out icon opens the current article in your browser. All four controls have tooltips.

Short facts use aligned rows; table entries have their own labeled records. **Show more** reveals additional entries. The variant selector switches between source item or monster variants. Separate strategy and full-guide links open inside the reader. Citation markers open reference cards with a return link to the cited passage. Right-click a section heading to open its Wiki source.

## Quick Inspect

Enable **Contextual WikiPane lookup** in WikiPane settings. Hold Shift and right-click an item, NPC, or object, then choose **WikiPane**. Bank and equipment item menus are supported where RuneLite exposes the item ID.

The small translucent panel leaves your sidebar article in place. Hold Shift and right-click the panel for **Open full article**, related articles, page navigation, retry, or **Dismiss**. Hold Alt to drag it. Choosing the full article adds it to ordinary sidebar history, so Back returns to your previous reading position.

Settings also offer ordinary right-click access and direct full-article lookups. Contextual lookup works alongside RuneLite's Wiki plugin; replacing its minimap button is separate.

Compact facts come from the same Wiki data as the reader. Unresolved variants are labeled and their specific stats are hidden. Monster inspections show static statistics, without combat preparation or prayer recommendations. Player-aware requirements are planned next.

## Pin a reference

Highlight text in an article, right-click, and choose **Pin selection**. The selection becomes a compact, translucent overlay immediately. Lists, numbering, emphasis, and field labels are kept.

Click the footer arrows to turn pages. Hold Alt to move a pin. Shift-right-click it for **Open source** or **Unpin**. Pins stay visible when the sidebar closes. Up to five pins can be kept for the session; disabling WikiPane or restarting RuneLite clears them.

## Replace the Wiki button

1. Disable RuneLite's core **Wiki** plugin.
2. Open WikiPane settings and enable **Replace Wiki button**.
3. Leave **Wiki button destination** set to **WikiPane sidebar**.
4. Click the in-game Wiki button, then an item, NPC, or object.

WikiPane refuses to install its button while the core Wiki plugin is enabled. It does not disable other plugins for you. If the game has its Wiki icon disabled, enable its Wiki entity lookup setting first.

Right-click the replacement button for Search, or enable **Left-click Search**. Set the destination to **BROWSER** if preferred.

The core Wiki plugin's DPS action is not reproduced. Leave replacement mode off and use the core plugin if you need that action. WikiPane removes its own widget and restores the original icon when disabled.

## Privacy and attribution

Search terms, requested article titles, and selected entity lookup details are sent over HTTPS to the [OSRS Wiki](https://oldschool.runescape.wiki/). Entity requests contain type, ID, name, and, for NPCs or objects, location. The Wiki sees your IP address. There is no telemetry, account tracking, credential handling, or external backend.

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

See [testing](docs/TESTING.md) for the player test checklist and [implementation notes](docs/IMPLEMENTATION.md) for limits and Plugin Hub preparation.
