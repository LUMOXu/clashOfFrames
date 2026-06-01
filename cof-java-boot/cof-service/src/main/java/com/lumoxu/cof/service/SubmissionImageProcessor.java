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
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 牌背 720×1087；PMV 帧 1500×1080（与 old/scripts/convert_cards_to_jpeg.py 一致）。
 */
@Component
public class SubmissionImageProcessor {

    public static final int BACK_WIDTH = 720;
    public static final int BACK_HEIGHT = 1087;
    public static final int CARD_WIDTH = 1500;
    public static final int CARD_HEIGHT = 1080;
    public static final int TARGET_MAX_BYTES = 100_000;

    /** @deprecated use BACK_* or CARD_* */
    public static final int TARGET_WIDTH = BACK_WIDTH;
    public static final int TARGET_HEIGHT = BACK_HEIGHT;

    public byte[] processDeckBack(
            InputStream input,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight) throws IOException {
        BufferedImage source = readRgb(input);
        BufferedImage cropped = applyCrop(source, cropX, cropY, cropWidth, cropHeight);
        BufferedImage scaled = scaleExact(cropped, BACK_WIDTH, BACK_HEIGHT);
        return encodeJpegTargetSize(scaled, TARGET_MAX_BYTES);
    }

    public byte[] processCardFrame(
            InputStream input,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight) throws IOException {
        BufferedImage source = readRgb(input);
        BufferedImage cropped = applyCrop(source, cropX, cropY, cropWidth, cropHeight);
        BufferedImage normalized = centerCropToLandscape(cropped, CARD_WIDTH, CARD_HEIGHT);
        return encodeJpegTargetSize(normalized, TARGET_MAX_BYTES);
    }

    public byte[] processJpeg(
            InputStream input,
            Integer cropX,
            Integer cropY,
            Integer cropWidth,
            Integer cropHeight) throws IOException {
        return processDeckBack(input, cropX, cropY, cropWidth, cropHeight);
    }

    private static BufferedImage readRgb(InputStream input) throws IOException {
        BufferedImage source = ImageIO.read(input);
        if (source == null) {
            throw new IOException("无法解析图片。");
        }
        return flattenForJpeg(source);
    }

    private static BufferedImage flattenForJpeg(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
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
        if (cropWidth >= source.getWidth() * 0.98
                && cropHeight >= source.getHeight() * 0.98
                && cropX <= 1
                && cropY <= 1) {
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

    /** 短边缩放到目标高度后居中裁切（横版 1500×1080）。 */
    static BufferedImage centerCropToLandscape(BufferedImage image, int targetWidth, int targetHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        int shortSide = Math.min(width, height);
        if (shortSide <= 0) {
            throw new IllegalArgumentException("invalid image size");
        }
        double scale = (double) targetHeight / shortSide;
        int resizedW = Math.max(1, (int) Math.round(width * scale));
        int resizedH = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = scaleExact(image, resizedW, resizedH);
        if (resized.getWidth() < targetWidth || resized.getHeight() < targetHeight) {
            resized = scaleExact(
                    resized,
                    Math.max(targetWidth, resized.getWidth()),
                    Math.max(targetHeight, resized.getHeight()));
        }
        int left = Math.max(0, (resized.getWidth() - targetWidth) / 2);
        int top = Math.max(0, (resized.getHeight() - targetHeight) / 2);
        return resized.getSubimage(left, top, targetWidth, targetHeight);
    }

    private static BufferedImage scaleExact(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
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
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
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
            return processDeckBack(in, cropX, cropY, cropW, cropH);
        }
    }
}
