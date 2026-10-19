package com.wikipane;

import com.wikipane.view.*;
import com.wikipane.semantic.*;

/** Offline source-fixture audit, not an in-game acceptance test. */
public final class MapCoverageReport {
    public static void main(String[] args)throws Exception {
        for(String fixture:new String[]{"Hans_maps","Bobs_axes_maps","Farming_patches_maps","Varrock_Sewers_maps","Taverley_Dungeon_maps","Tree_Gnome_Village_maps"}) {
            WikiPage page=new WikiPage(DocumentTest.document(fixture));
            System.out.println(page.document.title+" @ "+page.document.revision+": "+page.document.maps.frames.size()+" frames, "+page.document.maps.features.size()+" features, incomplete="+page.document.maps.incomplete);
            for(ArticleMaps.Group group:page.maps.groups)print(group,"  ");
        }
    }
    private static void print(ArticleMaps.Group group,String indent) {
        System.out.println(indent+group.title+": "+group.destinations.size()+" entries"+(group.image!=null?", image":"")+(group.frame!=null?", preview":""));
        for(ArticleMaps.Group child:group.children)print(child,indent+"  ");
    }
}
