package com.wikipane.cache;

import com.wikipane.api.WikiClient;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Decode limits are checked before raster allocation; no ImageIO disk cache. */
public final class ImageCache {
    private final WikiClient client;
    private final WikiCache<String, BufferedImage> cache = new WikiCache<>(48, 600_000);
    public ImageCache(WikiClient client) {
        this.client = client;
    }
    public BufferedImage load(String url) throws IOException {
        BufferedImage old = cache.get(url);
        if (old != null)
            return old;
        byte[] bytes = client.image(url);
        try (ImageInputStream input =
                 new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new IOException("Unsupported image format.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 1 || h < 1 || w > 8192 || h > 8192 || (long) w * h > 12_000_000)
                    throw new IOException("Image is too large.");
                ImageReadParam param = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(w / 200, h / 220));
                param.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage source = reader.read(0, param);
                double scale = Math.min(1, Math.min(200.0 / source.getWidth(), 220.0 / source.getHeight()));
                BufferedImage image = new BufferedImage(Math.max(1, (int) (source.getWidth() * scale)),
                    Math.max(1, (int) (source.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
                g.dispose();
                source.flush();
                cache.put(url, image);
                return image;
            } finally {
                reader.dispose();
            }
        }
    }
    public void clear() {
        cache.clear();
    }
}
