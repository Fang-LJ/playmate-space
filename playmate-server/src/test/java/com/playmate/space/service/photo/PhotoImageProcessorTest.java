package com.playmate.space.service.photo;

import com.playmate.space.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoImageProcessorTest {
    @Test
    void detectsAndBuildsPngDerivatives() throws Exception {
        PhotoProperties properties = new PhotoProperties();
        properties.setThumbnailMaxEdge(20);
        properties.setPreviewMaxEdge(40);
        PhotoImageProcessor.ProcessedPhoto result = new PhotoImageProcessor(properties).process(png(80, 40), "image/png");

        assertEquals("image/png", result.contentType());
        assertEquals("png", result.extension());
        assertEquals(80, result.width());
        assertTrue(result.thumbnail().length > 0);
        assertTrue(result.preview().length > 0);
    }

    @Test
    void rejectsMismatchedDeclaredMime() throws Exception {
        assertThrows(BusinessException.class, () -> new PhotoImageProcessor(new PhotoProperties()).process(png(2, 2), "image/jpeg"));
    }

    @Test
    void rejectsUndecodableOrUnsupportedBytes() {
        assertThrows(BusinessException.class, () -> new PhotoImageProcessor(new PhotoProperties()).process(new byte[]{1, 2, 3}, "image/png"));
    }

    @Test
    void rotatesDisplayPixelsForExifOrientationSix() {
        BufferedImage source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xFF0000);
        BufferedImage result = PhotoImageProcessor.applyOrientation(source, 6);
        assertEquals(3, result.getWidth());
        assertEquals(2, result.getHeight());
        assertEquals(0xFF0000, result.getRGB(2, 0) & 0xFFFFFF);
    }

    @Test
    void rejectsOverLimitBeforeFullDecode() throws Exception {
        PhotoProperties properties = new PhotoProperties(); properties.setMaxWidth(10);
        assertThrows(BusinessException.class, () -> new PhotoImageProcessor(properties).process(png(11, 1), "image/png"));
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", stream);
            return stream.toByteArray();
        }
    }
}
