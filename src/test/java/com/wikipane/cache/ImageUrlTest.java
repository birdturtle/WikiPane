package com.wikipane.cache;
import org.junit.Test;
import static org.junit.Assert.*;
public class ImageUrlTest {
    @Test public void resolvesActualWikiFlatThumbnailAndLegacyHashedThumbnail() {
        assertEquals("https://oldschool.runescape.wiki/images/Cook%27s_Assistant.png",
            ImageCache.originalUrl("https://oldschool.runescape.wiki/images/thumb/Cook%27s_Assistant.png/300px-Cook%27s_Assistant.png?2e488"));
        assertEquals("https://oldschool.runescape.wiki/images/a/ab/Example.png",
            ImageCache.originalUrl("https://oldschool.runescape.wiki/images/thumb/a/ab/Example.png/200px-Example.png"));
    }
    @Test public void preservesDirectOriginalAndUsesRasterForVector() {
        String original="https://oldschool.runescape.wiki/images/Example.png?abc";
        assertEquals(original, ImageCache.originalUrl(original));
        assertEquals("https://oldschool.runescape.wiki/images/thumb/Example.svg/1280px-Example.svg.png",
            ImageCache.originalUrl("https://oldschool.runescape.wiki/images/thumb/Example.svg/20px-Example.svg.png"));
    }
}
