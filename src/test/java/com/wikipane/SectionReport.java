package com.wikipane;
import com.wikipane.view.*;
/** Fixture-only inspection of the shared navigation/content tree. */
public final class SectionReport {
    public static void main(String[] args)throws Exception {
        WikiPage page=new WikiPage(DocumentTest.document(args.length==0?"Vorkath":args[0]));
        for(ArticleNavigation.ReadingSection root:page.navigation.roots)print(root,"");
    }
    private static void print(ArticleNavigation.ReadingSection node,String indent){
        System.out.println(indent+node.destination.area+" / "+node.destination.anchor+" / "+node.destination.label+" ("+node.components.size()+")");
        for(ArticleViewModel.Component c:node.components)System.out.println(indent+"  · "+c.type+" / "+c.source.section+" / "+c.node.role);
        for(ArticleNavigation.ReadingSection child:node.children)print(child,indent+"  ");
    }
}
