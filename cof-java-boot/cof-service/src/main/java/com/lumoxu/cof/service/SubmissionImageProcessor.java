package com.lumoxu.cof.service;

import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Normalizes submitted card/back images to 720×1087 JPEG (~100KB).
 */
@Component
public class SubmissionImageProcessor {

    public static final int TARGET_WIDTH = 720;
    public static final int TARGET_HEIGHT = 1087;
    public static final int TARGET_MAX_BYTES = 100_000;

    public byte[] processJpeg(
            InputStream input,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight) throws IOException {
        BufferedImage source = ImageIO.read(input);
        if (source == null) {
            throw new IOException("无法解析图片。");
        }
        BufferedImage cropped = applyCrop(source, cropX, cropY, cropWidth, cropHeight);
        BufferedImage scaled = scaleToTarget(cropped);
        return encodeJpegTargetSize(scaled, TARGET_MAX_BYTES);
    }

    private static BufferedImage applyCrop(
            BufferedImage source,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight) {
        if (cropX == null || cropY == null || cropWidth == null || cropHeight == null) {
            return source;
        }
        if (cropWidth <= 0 || cropHeight <= 0) {
            return source;
        }
        int x = Math.max(0, Math.min(cropX, source.getWidth() - 1));
        int y = Math.max(0, Math.min(cropY, source.getHeight() - 1));
        int w = Math.min(cropWidth, source.getWidth() - x);
        int h = Math.min(cropHeight, source.getHeight() - y);
        if (w <= 0 || h <= 0) {
            return source;
        }
        return source.getSubimage(x, y, w, h);
    }

    private static BufferedImage scaleToTarget(BufferedImage source) {
        BufferedImage target = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source.getScaledInstance(TARGET_WIDTH, TARGET_HEIGHT, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return target;
    }

    private static byte[] encodeJpegTargetSize(BufferedImage image, int maxBytes) throws IOException {
        float quality = 0.92f;
        byte[] best = encodeJpeg(image, quality);
        while (best.length > maxBytes && quality > 0.35f) {
            quality -= 0.08f;
            best = encodeJpeg(image, quality);
        }
        if (best.length > maxBytes * 1.5) {
            BufferedImage smaller = scaleToTarget(image);
            quality = 0.85f;
            best = encodeJpeg(smaller, quality);
            while (best.length > maxBytes && quality > 0.3f) {
                quality -= 0.08f;
                best = encodeJpeg(smaller, quality);
            }
        }
        return best;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("JPEG encoder unavailable");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.setOutput(new MemoryCacheImageOutputStream(out));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        return out.toByteArray();
    }

    public void writeJpegToPath(byte[] jpegBytes, java.nio.file.Path path) throws IOException {
        java.nio.file.Files.createDirectories(path.getParent());
        java.nio.file.Files.write(path, jpegBytes);
    }

    public byte[] processJpeg(byte[] bytes, Integer cropX, Integer cropY, Integer cropW, Integer cropH) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return processJpeg(in, cropX, cropY, cropW, cropH);
        }
    }
}
