# Dependency removal

The original binary jsoup dependency introduced prohibited URL/HTTP and stderr APIs. The subsequent source-subset build removed those APIs, but required a custom Gradle build and additional review. Both approaches are superseded.

WikiPane now uses `build=standard` and has no additional production dependency. No jsoup code, source artifact, adaptation patch, generated classes or parser license is distributed. `com.wikipane.html` is a small, independently written, in-memory reader for the server-rendered MediaWiki HTML already downloaded by the injected RuneLite OkHttp client. It does not fetch resources or expand wikitext/templates. JDK HTML entity data handles named entities; structural queries cover only the constructs used by Wiki normalization.

Unknown container elements retain their content. Common omitted table/list end tags are handled; this is not a general HTML5 browser engine. Input size, nesting and node count are bounded. The normalized document, semantic extraction and native presentation pipeline remain separate and unchanged in responsibility.

Local developer builds scan every packaged class for the original prohibited API categories and other unsupported operations. The official standard build is separately tested; project-specific Gradle tasks are not required by the Hub.
