# Presentation validation

The compiler input is the complete semantic document. Its output is a checked page composition plus `ReaderLayout` component contracts. Swing consumes those contracts.

| Check | Controlled response |
| --- | --- |
| Source node or provenance mismatch | Reject the non-source component or restore the canonical node through generic reading |
| Missing semantic units | Add a generic source component without inventing values |
| Changed or absent fact fields | Replace specialization with the original node |
| Missing requirement groups | Preserve the original requirement-bearing content in generic reading |
| Reference definitions in prose/instructions | Route them to References while keeping citation IDs and backlinks |
| Incorrect reference component | Restore the original prose instead of rendering an empty reference card |
| Duplicate component ID | Remove an identical duplicate, or assign a distinct presentation ID without changing source identity |
| Ambiguous area labels | Distinguish the destination labels |
| Table record values or labels differ from cells | Use conservative labeled source rows |
| Record adaptation loses a field | Use the original labeled record fields |
| Fact columns too narrow | Stack label and value |
| Highlight cells too narrow | Use a vertical highlight list |
| Thumbnail wider than available space | Scale the complete bounded thumbnail to the available width |
| Measured text height insufficient | Reserve the measured height once for that width |
| Remaining clipping or overflow | Report a warning and retain the explicit browser fallback |

Source content, not generated presentation text, is authoritative. Rejected specialization does not erase its underlying Wiki data. Footnote definitions are recognized by structural Wiki classes or citation IDs, not by guessing that bracketed numbers are citations. Links between a marker, its definition, and its return location use the standard reader navigation.

## Audit

Right-click the WikiPane heading and choose Composition audit. The report includes source-unit coverage, reference-definition count, whether visual bounds have been measured, the latest measured width/component count, repairs, and warnings. Findings are bounded at 256 entries and repeated findings are deduplicated. Diagnostics stay local; nothing is uploaded.

`./gradlew semanticReport` audits the captured fixtures without network or game access. It correctly reports that visual geometry has not been measured. The standalone `preview` task can capture a PNG and print the rendered panel's audit.

## Limits

Semantic validation is done before Swing. Actual bounds are inspected after layout, with deterministic width decisions applied as components are laid out. The plugin does not create an entire long article twice or block the client to pre-render it. Only visible, materialized components are inspected at runtime, with a 1024-component budget per pass. Scrolling, resizing, and pagination make later content eligible for inspection.

Coverage starts at normalized semantic content. It cannot prove that the HTML normalizer understands every Wiki template or that the Wiki's own data is correct. Remote article destinations are not probed to validate links. Missing citation definitions remain diagnostic warnings with access to the original Wiki.

A valid composition is not an aesthetic judgment. Manual sidebar review and screenshot comparison remain necessary. Whole-page pixel-baseline tests are not currently implemented; automated native tests cover bounds, line breaks, navigation, deterministic repairs, and representative image painting.

The tests run against isolated Swing panels. No game input is automated.
