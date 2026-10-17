package com.wikipane.knowledge;

import com.wikipane.api.*;
import com.wikipane.document.WikiDocument;
import java.io.IOException;
import java.util.Objects;
import okhttp3.HttpUrl;

/** Worker-only access shared by contextual lookups. WikiClient owns the bounded source caches. */
public final class KnowledgeService {
    private final WikiClient wiki;
    public KnowledgeService(WikiClient wiki) { this.wiki = wiki; }
    public KnowledgeArticle article(String title, String anchor) throws IOException {
        return new KnowledgeArticle(wiki.article(title), "", Objects.toString(anchor, ""));
    }
    public KnowledgeArticle entity(HttpUrl lookup) throws IOException {
        if (!WikiUrls.wiki(lookup) || !lookup.encodedPath().equals("/w/Special:Lookup"))
            throw new IOException("Unsupported entity lookup.");
        HttpUrl destination = WikiUrls.resolve(wiki.entity(lookup));
        String title = WikiUrls.title(destination);
        if (title == null || title.startsWith("Special:"))
            throw new IOException("No single Wiki article was resolved.");
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Lookup cancelled.");
        WikiDocument source = wiki.article(title);
        return new KnowledgeArticle(source, Objects.toString(lookup.queryParameter("id"), ""),
            Objects.toString(destination.fragment(), ""));
    }
}
