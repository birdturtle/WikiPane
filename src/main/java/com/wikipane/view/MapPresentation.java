package com.wikipane.view;

import com.wikipane.semantic.SemanticDocument.Location;
import com.wikipane.semantic.SemanticDocument.Source;
import com.wikipane.integration.WikiMapIntegration;
import com.wikipane.html.WikiHtml;
import com.wikipane.html.Element;

/** Coordinates remain in the semantic model; a supported map action replaces their display text. */
public final class MapPresentation {
    private MapPresentation() {}
    public static String html(String html, Source source) {
        if(!html.contains("data-wikipane-map"))return html;
        Element body=WikiHtml.parseBodyFragment(html).body();
        for(Element map:body.select("span[data-wikipane-map]")) {
            if(WikiMapIntegration.destination(new Location(map.attr("data-x"),map.attr("data-y"),
                map.attr("data-plane"),source,true,map.attr("data-mapid")))!=null) map.remove();
            else map.text("Location available on the Wiki map");
        }
        return body.html();
    }
}
