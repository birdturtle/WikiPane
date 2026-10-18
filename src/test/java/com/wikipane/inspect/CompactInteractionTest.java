package com.wikipane.inspect;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompactInteractionTest {
    @Test public void listsAndAlternativesRetainBoundaries() {
        List<String> lines=InspectionLayout.structuredText("<p>Bring one of:</p><ul><li>Milk <b>or</b> cream</li><li>Flour</li></ul><ol start='4'><li>Speak to the cook.</li></ol>");
        assertTrue(lines.contains("Bring one of:"));
        assertTrue(lines.contains("• Milk or cream")); assertTrue(lines.contains("• Flour"));
        assertTrue(lines.contains("4. Speak to the cook."));
        List<String> nested=InspectionLayout.structuredText("<ul><li>First condition<ul><li>Its prerequisite</li></ul></li><li>Alternative</li></ul>");
        assertTrue(nested.contains("• First condition")); assertTrue(nested.contains("\t• Its prerequisite"));
        assertTrue(nested.contains("• Alternative"));
    }
    @Test public void onlyVisibleControlClicksAreConsumedAndModifierGesturesPassThrough() {
        List<InspectMouseListener.Action> actions=new ArrayList<>();
        InspectMouseListener mouse=new InspectMouseListener(() -> true,actions::add);
        Rectangle button=QuickInspectOverlay.button(200,InspectMouseListener.Action.NEXT);
        mouse.bounds(Map.of(InspectMouseListener.Action.NEXT,button));
        Canvas source=new Canvas();
        MouseEvent ordinary=new MouseEvent(source,MouseEvent.MOUSE_PRESSED,0,0,10,10,1,false,MouseEvent.BUTTON1);
        mouse.mousePressed(ordinary);assertFalse(ordinary.isConsumed());
        MouseEvent modifier=new MouseEvent(source,MouseEvent.MOUSE_PRESSED,0,InputEvent.ALT_DOWN_MASK,button.x+2,button.y+2,1,false,MouseEvent.BUTTON1);
        mouse.mousePressed(modifier);assertFalse(modifier.isConsumed());
        MouseEvent next=new MouseEvent(source,MouseEvent.MOUSE_PRESSED,0,0,button.x+2,button.y+2,1,false,MouseEvent.BUTTON1);
        mouse.mousePressed(next);assertTrue(next.isConsumed());assertEquals(List.of(InspectMouseListener.Action.NEXT),actions);
        mouse.bounds(Map.of());
        MouseEvent stale=new MouseEvent(source,MouseEvent.MOUSE_PRESSED,0,0,button.x+2,button.y+2,1,false,MouseEvent.BUTTON1);
        mouse.mousePressed(stale);assertFalse(stale.isConsumed());
    }
}
