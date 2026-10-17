# Selection pins

Interaction: highlight article text, right-click, choose **Pin selection**. No editor, confirmation, automatic summary, or additional setup.

## Presentation rules

- RuneLite's native small font and translucent overlay background.
- 210-pixel width, 6-pixel margins, six body lines per page.
- Selected paragraphs, explicit line breaks, bold/italic spans, ordered-list numbers, nested list depth and source links retained as structured data.
- Table and infobox selections carry their field label, group and record identity. Selecting a partial field value labels it as an excerpt rather than displaying it as the complete statistic.
- Variant-specific information identifies the source variant. No values from another variant are substituted.
- Long selections use pages; selected content is not silently clipped. Limits are 12,000 selected characters, 128 fragments and 256 paragraphs. Oversized selections are refused with an explanation.
- Right-clicking selected text exposes Pin selection. The overlay has clickable previous/next footer arrows and a page count for multi-page selections. Arrow clicks are consumed locally, including at page boundaries. RuneLite's Shift-right-click menu retains Open source, paging and Unpin. Positioning uses Alt-drag.

## Components

`PinContext` binds a reader fragment to its existing semantic node, source unit, field, requirement expression, group and record. It references the Wiki document; it is not a second extraction engine.

`SelectionCapture` reads the existing Swing HTMLDocument's selected character ranges and formatting on the EDT. It does not fetch or reparse Wiki HTML. `ReaderSelection` coordinates user-driven selection across paragraphs and field panes, adds the context menu and removes its listeners when the reader is cleared. It does not synthesize game input. Search controls and unrelated application text are excluded.

`PinSelection` is an immutable structured selection with source article, revision, section, semantic references and exact selected ranges. Source ownership is checked. It contains no Swing components, screenshots or arbitrary HTML fragments.

`PinLayout` translates the selection into native text layouts with semantic labels and readable pages. Layout runs on a dedicated bounded worker so a slow Wiki request cannot delay pin formatting. Rendering only paints prepared layouts. A RuneLite MouseManager listener handles the footer hit areas and dispatches paging to the client thread. It leaves modified clicks available for native overlay movement and menus and is removed with its overlay.

`PinnedReferences` owns up to five session pins and pending requests. Duplicate selections do not create duplicate overlays. Slots are reused after unpinning. Shutdown cancels pending work, ignores late completions and removes all overlays. `PinnedOverlay` uses RuneLite's native positioning, background and local menu actions. Pins survive sidebar navigation, closing, logout and world hopping within the running session, and are hidden outside gameplay.

## Boundaries

Pins are local session snapshots. They do not update the Wiki, read account state, send selected text anywhere, or persist their contents. RuneLite manages overlay positions normally. Open source explicitly returns to the relevant article section through ordinary sidebar navigation. No browser is opened automatically.

Combat strategy text remains in the reader under the repository's overlay restrictions. Static monster statistics can be pinned. Restricted selections remain selectable and copyable; their Pin action explains why it is unavailable. No automated combat guidance or player actions are introduced.

Images and arbitrary website layouts are not part of text selection. The complete article remains available.

## Checks

Automated checks cover native Swing range capture, cross-paragraph highlight → right-click → Pin, numbered and nested lists, explicit line breaks, emphasis, source links, field-label association, partial statistics, exact monster variants, Unicode spaces, long-selection coverage, bounds, transparency, duplicate pins, slot limits, paging, queue rejection, shutdown and stale completions. A real-reader regression checks that pinning Recipe for Disaster requirements preserves its article, scroll position and request count.

Manual RuneLite testing is still required:

1. Highlight a requirement, right-click and Pin. It should appear without another dialog.
2. Drag a selection across several paragraphs or requirement rows, including linked text. Confirm the selection is pinned and no link opens accidentally.
3. Select an item statistic and a table record. Check labels and variants against the sidebar.
4. Close the sidebar, Alt-drag the pin, reopen the sidebar and navigate elsewhere. The pin should remain.
5. Test long selections, page navigation, Open source and Unpin.
6. Log out, hop worlds and disable/re-enable WikiPane. No duplicate or orphaned overlays should remain.

Do not automate these game interactions.

```sh
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew test build
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk ./gradlew pinPreview -Pcapture=/tmp/wikipane-pin.png
```

The preview paints a selection from the captured Recipe for Disaster reader. It does not run or capture RuneScape.
