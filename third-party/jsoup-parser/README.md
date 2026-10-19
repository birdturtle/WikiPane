# Private, in-memory jsoup subset

This is an auditable source adaptation of **jsoup 1.23.2**, not a new HTML parser or a binary patch. The original HTML tokenizer/tree builder, DOM, entities, CSS selectors and cleaning code are compiled from the pinned Maven Central source artifact. See `third-party/jsoup-LICENSE.txt` for its MIT license.

Source: https://repo.maven.apache.org/maven2/org/jsoup/jsoup/1.23.2/jsoup-1.23.2-sources.jar

SHA-256: `f0ed21735857df93c3e461d1aecf89d28c3eda552f2e043d68959d341376aa17`

`gradle/parser-only.gradle` checks that hash before preparation. It selects only nodes/parser/select/internal/safety, Progress, SerializationException and validation helpers. It applies the checked-in unified patch with exact old-line matching, copies the two readable overrides, removes compile-only nullness annotations, and relocates everything to `com.wikipane.vendor.jsoup`. Generated files live only under `build/generated/sources/wikiParser`. No code is downloaded, generated or loaded at runtime.

## Exact capability changes

- The facade accepts already-loaded strings only. There are no HTTP sessions, URL/file loading APIs or upload APIs.
- Network transports, dispatch, authentication, cookies, URL builder and DataUtil are absent from the selected sources. No transport class is included and no missing transport class is referenced by supported parser code.
- Document connection state/accessors and FormElement submission/form-data methods are removed. HTML forms and their controls still parse as DOM content; they cannot be submitted by this parser.
- DataUtil's UTF-8 constant is replaced by StandardCharsets.UTF_8.
- W3CDom/JAXP integration is absent. XPath selection explicitly reports UnsupportedOperationException; WikiPane uses CSS selectors.
- Regex uses the same JDK Pattern path that upstream uses without optional re2j. Reflective module/class probing and optional re2j loading are absent.
- The deprecated MatchText selector keeps its DOM behavior, but its once-per-process deprecation diagnostic uses SLF4J debug logging rather than System.err.
- Type-use nullness annotations and package-info declarations are compile-time metadata only; they are omitted to avoid another runtime or build dependency.

The namespace relocation prevents collisions with other plugins' jsoup versions. WikiPane production and tests both use this subset. No unrestricted jsoup binary is a compile or runtime dependency. The source artifact is a build input only.

## Verification

`verifyPackagedApis` inspects every class constant pool in the finished developer artifact, including the private parser. The same inspection runs on the official Hub archive task. It rejects the reported URL/Java HTTP/console calls, reflective access, direct file IO and process/native loading APIs, without exempting dependency packages. `verifyApiAudit` verifies rejection using valid classfile fixtures containing each original prohibited reference, outside WikiPane's package.

The complete existing article/table/requirement/navigation/pin/image/map suite runs against the subset. ParserSubsetTest additionally checks malformed tables, entities, relative links, ordered lists, readable form controls and JDK-regex selectors. The source patch intentionally preserves parsing/presentation behavior; user gameplay regression remains distinct from compilation and automated tests.
