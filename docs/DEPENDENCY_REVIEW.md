# jsoup scan findings — Plugin Hub PR #18901

The automated scan reported three prohibited API categories in the submitted artifact. Source search and `javap --multi-release 11 -p -c` on the exact Maven Central jsoup 1.23.2 JAR locate them as follows:

| Finding | Bundled class / method | Purpose |
| --- | --- | --- |
| `URL.openConnection` (both overloads) | `org.jsoup.helper.UrlConnectionExecutor.createConnection` | jsoup's URLConnection HTTP transport |
| `HttpClient.newBuilder` | `org.jsoup.helper.HttpClientExecutor.newClient` (Java 11 multi-release class) | jsoup's Java HTTP transport |
| `System.err` | `org.jsoup.helper.Regex.hasRe2j` | optional re2j/module-access diagnostic |
| `System.err` | `org.jsoup.select.Evaluator$MatchText` constructor | deprecated `:matchText` selector warning |

The jsoup JAR SHA-256 remains `e9d8c856856680427f096d156f02a414289985c930998fcb519ce5be39b42003`, independently checked against Maven Central during submission preparation.

WikiPane production code calls `Jsoup.parse(String)` and `Jsoup.parseBodyFragment(String[, String])`, plus DOM traversal/selectors. It does not call jsoup `connect`, `newSession`, URL/file parsing, `Document.connection`, or form submission. `WikiClient` downloads via the injected RuneLite OkHttp client with bounded response sizes/timeouts and restricted destinations. Searches of `src/main/java` found no calls to the three reported API categories. No `:matchText` selectors or re2j dependency are used by WikiPane.

The automated report nevertheless describes real references in the shipped dependency. The earlier green packaging/dependency checks do not establish security-review approval. Network transport methods are unused by WikiPane; the diagnostic methods belong to jsoup. No exception or acceptance is assumed.

The PR requests maintainer assessment of the declared dependency and its unused transport APIs. Removing scattered library classes, replacing parser versions, or changing scan permissions without maintainer agreement would be a separate compatibility/security decision. The distributed jsoup JAR is unchanged and the findings remain pending that review.

Sources:

- https://github.com/jhy/jsoup/blob/jsoup-1.23.2/src/main/java/org/jsoup/helper/UrlConnectionExecutor.java
- https://github.com/jhy/jsoup/blob/jsoup-1.23.2/src/main/java11/org/jsoup/helper/HttpClientExecutor.java
- https://github.com/jhy/jsoup/blob/jsoup-1.23.2/src/main/java/org/jsoup/helper/Regex.java
- https://github.com/jhy/jsoup/blob/jsoup-1.23.2/src/main/java/org/jsoup/select/Evaluator.java
- https://github.com/runelite/runelite/wiki/Plugin-Hub-Review
