package com.wikipane.view;
import com.wikipane.document.WikiDocument;
/** Request result: source/semantic data plus an independently constructed presentation model. */
public final class WikiPage {
    public final WikiDocument document;
    public final ArticleViewModel view;
    public final ReaderLayout layout;
    public final ArticleNavigation navigation;
    public final java.util.List<com.wikipane.knowledge.RelatedInformation.Group> relatedInformation;
    public WikiPage(WikiDocument document) {
        this.document = document;
        this.view = ArticleViewModel.create(document.semantic);
        this.layout = new ReaderLayout(view);
        this.navigation = new ArticleNavigation(view);
        this.relatedInformation = com.wikipane.knowledge.RelatedInformation.of(
            new com.wikipane.knowledge.KnowledgeArticle(document, "", document.redirectAnchor));
    }
}
