package com.lumoxu.cof.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmissionImageProcessorTest {

    @Test
    void compressesToJpegUnderTargetSize() throws Exception {
        BufferedImage large = new BufferedImage(2000, 3000, BufferedImage.TYPE_INT_RGB);
        var g = large.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 2000, 3000);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(large, "png", png);

        SubmissionImageProcessor processor = new SubmissionImageProcessor();
        byte[] jpeg = processor.processJpeg(new ByteArrayInputStream(png.toByteArray()), null, null, null, null);

        assertTrue(jpeg.length > 1000);
        assertTrue(jpeg.length <= SubmissionImageProcessor.TARGET_MAX_BYTES * 2);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertTrue(out.getWidth() == SubmissionImageProcessor.TARGET_WIDTH);
        assertTrue(out.getHeight() == SubmissionImageProcessor.TARGET_HEIGHT);
    }
}
