package com.wikipane.context;

import com.google.gson.Gson;
import com.wikipane.DocumentTest;
import com.wikipane.api.WikiClient;
import com.wikipane.ui.WikiPanePanel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import okhttp3.*;

/** Synthetic state in the actual sidebar with an offline Wiki fixture. Never reads or captures a game client. */
public final class PlayerContextPreview {
    private static void layout(Container root){root.doLayout();for(Component c:root.getComponents())if(c instanceof Container)layout((Container)c);}
    private static boolean scrollToQuests(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JEditorPane && ((JEditorPane)c).getText().contains("Fishing") && ((JEditorPane)c).getText().contains("Contest")) {
                Component target = c;
                Container parent = c.getParent();
                while (parent != null && !(parent instanceof JViewport)) parent = parent.getParent();
                if (parent instanceof JViewport) {
                    Point point = SwingUtilities.convertPoint(target,0,0,((JViewport)parent).getView());
                    ((JViewport)parent).setViewPosition(new Point(0,Math.max(0,point.y-30)));return true;
                }
            }
            if (c instanceof Container && scrollToQuests((Container)c)) return true;
        }
        return false;
    }
    public static void main(String[] args)throws Exception{
        System.setProperty("java.awt.headless","true");
        String fixture=DocumentTest.fixture("Recipe_for_Disaster").toString();
        ExecutorService worker=Executors.newSingleThreadExecutor();WikiClient wiki=new WikiClient(new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder()
            .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Offline fixture")
            .body(ResponseBody.create(MediaType.get("application/json"),fixture)).build()).build(),new Gson());
        WikiPanePanel[] panel={null};Map<String,Icon> icons=new HashMap<>();SkillIconManager iconManager=new SkillIconManager();
        for(Skill skill:Skill.values())if(skill!=Skill.OVERALL)try{icons.put(PlayerContextSnapshot.skillKey(skill.getName()),new ImageIcon(iconManager.getSkillImage(skill,true)));}catch(RuntimeException ignored){}
        try{
            SwingUtilities.invokeAndWait(()->{
                com.formdev.flatlaf.FlatDarkLaf.setup();panel[0]=new WikiPanePanel(wiki,worker);
                panel[0].skillIcons(icons);panel[0].playerContext(new PlayerContextSnapshot(true,true,
                    Map.of("Cooking",new PlayerContextSnapshot.Level(42,45),"Agility",new PlayerContextSnapshot.Level(28,28),"Mining",new PlayerContextSnapshot.Level(55,55)),
                    Map.of("Cook's Assistant",PlayerContextSnapshot.QuestState.COMPLETE,"Nature Spirit",PlayerContextSnapshot.QuestState.COMPLETE,"Priest in Peril",PlayerContextSnapshot.QuestState.COMPLETE,"Fishing Contest",PlayerContextSnapshot.QuestState.INCOMPLETE),100));
                panel[0].setSize(242,780);panel[0].openTitle("Recipe for Disaster","");
            });
            // Worker and EDT barriers flush article fetching, composition and local evaluation in order.
            for(int i=0;i<3;i++){worker.submit(()->{}).get(10,TimeUnit.SECONDS);SwingUtilities.invokeAndWait(()->{});}
            SwingUtilities.invokeAndWait(()->{
                for(int i=0;i<6;i++)layout(panel[0]);scrollToQuests(panel[0]);BufferedImage image=new BufferedImage(242,780,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel[0].paint(g);g.dispose();
                try{ImageIO.write(image,"png",new File(args[0]));}catch(Exception ex){throw new IllegalStateException(ex);}panel[0].stop();
            });
        }finally{wiki.close();worker.shutdownNow();}
    }
}
