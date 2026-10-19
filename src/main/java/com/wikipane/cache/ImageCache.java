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
    public final MapImageCache maps;
    private final WikiCache<String, BufferedImage> cache = new WikiCache<>(48, 600_000);
    public ImageCache(WikiClient client) {
        this.client = client;
        this.maps = new MapImageCache(client);
    }
    private final WikiCache<String, BufferedImage> previews = new WikiCache<>(2, 600_000);
    public BufferedImage load(String url) throws IOException {
        return load(url, 200, 220, cache);
    }
    public BufferedImage preview(String url) throws IOException {
        return load(originalUrl(url), 8192, 8192, previews);
    }
    static String originalUrl(String url) {
        // MediaWiki thumbnails contain the original file path followed by a size-specific filename.
        java.net.URI uri = java.net.URI.create(url);
        String path = uri.getRawPath();
        int thumb = path.indexOf("/images/thumb/");
        if (thumb < 0) return url;
        String tail = path.substring(thumb + "/images/thumb/".length());
        String[] parts = tail.split("/");
        if (parts.length != 2 && parts.length != 4) return url;
        String filename = parts[parts.length - 2];
        String original = path.substring(0, thumb) + "/images/" + tail.substring(0, tail.lastIndexOf('/'));
        // SVG originals need a raster thumbnail for ImageIO.
        if (filename.toLowerCase(java.util.Locale.ROOT).endsWith(".svg"))
            original = path.substring(0, path.lastIndexOf('/') + 1) + "1280px-" + filename + ".png";
        return uri.getScheme() + "://" + uri.getRawAuthority() + original;
    }
    private BufferedImage load(String url, int maxWidth, int maxHeight,
        WikiCache<String, BufferedImage> cache) throws IOException {
        BufferedImage old = cache.get(url);
        if (old != null)
            return old;
        byte[] bytes = maxWidth > 200 ? client.originalImage(url) : client.image(url);
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
                int sample = Math.max(1, Math.max(w / maxWidth, h / maxHeight));
                param.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage source = reader.read(0, param);
                double scale = Math.min(1, Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight()));
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
        previews.clear();
        maps.clear();
    }
}
