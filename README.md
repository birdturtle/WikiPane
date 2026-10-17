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

The search field and navigation buttons stay above the reader. Articles are organized by their content: monsters have Combat & strategy and Drops; items have Stats and Sources; quests have Requirements, Walkthrough, and Rewards. Remaining material stays under Other sections. Choose an area, then scroll through its content.

Requirements keep starting and full-completion conditions separate. Short facts use aligned rows; drops and other table entries have their own labeled records. Tables become labeled records with all columns retained; **Show more** reveals additional entries. The variant selector switches between source item or monster variants. Links to separate strategy and full-guide articles open those articles in the same reader.

Back and Forward restore your area, variant, expanded tables, and reading position. The **Browser ↗** button opens the current article in your browser. Right-click a section heading to open its source on the Wiki. Images load as they enter view.

## Replace the Wiki button

1. Disable RuneLite's core **Wiki** plugin.
2. Open WikiPane settings and enable **Replace Wiki button**.
3. Leave **Lookup destination** set to **SIDEBAR**.
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

See [testing](docs/TESTING.md) for the player test checklist and [implementation notes](docs/IMPLEMENTATION.md) for limits and Plugin Hub preparation.
