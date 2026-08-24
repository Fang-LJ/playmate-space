package com.playmate.space.service.photo;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

@Component
public class PhotoImageProcessor {
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private final PhotoProperties properties;
    public PhotoImageProcessor(PhotoProperties properties) { this.properties = properties; }

    public ProcessedPhoto process(byte[] bytes, String declaredContentType) {
        String contentType = detectContentType(bytes);
        if (declaredContentType != null && !declaredContentType.isBlank() && !contentType.equals(declaredContentType.trim().toLowerCase())) throw param("文件 MIME 类型与实际图片内容不一致");
        BufferedImage source = decodeAfterHeaderCheck(bytes);
        BufferedImage oriented = applyOrientation(source, "image/jpeg".equals(contentType) ? exifOrientation(bytes) : 1);
        String format = "image/png".equals(contentType) ? "png" : "jpg";
        return new ProcessedPhoto(contentType, format, oriented.getWidth(), oriented.getHeight(), bytes,
                resize(oriented, properties.getThumbnailMaxEdge(), format), resize(oriented, properties.getPreviewMaxEdge(), format));
    }

    private BufferedImage decodeAfterHeaderCheck(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw param("只支持可安全解码的 JPEG 或 PNG 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                BufferedImage image = reader.read(0);
                if (image == null) throw param("图片解码失败");
                return image;
            } finally { reader.dispose(); }
        } catch (IOException exception) { throw param("图片解码失败"); }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > properties.getMaxWidth() || height > properties.getMaxHeight() || (long) width * height > properties.getMaxPixels()) throw param("图片尺寸超出限制");
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (bytes.length >= PNG_MAGIC.length && Arrays.equals(Arrays.copyOf(bytes, PNG_MAGIC.length), PNG_MAGIC)) return "image/png";
        throw param("PHOTO 目前只支持经过服务器解码验证的 JPEG 或 PNG 图片");
    }

    private int exifOrientation(byte[] bytes) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer value = directory == null ? null : directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return value != null && value >= 1 && value <= 8 ? value : 1;
        } catch (Exception ignored) { return 1; }
    }

    /** Applies all EXIF orientation values (1-8) before thumbnail and preview generation. */
    static BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) return source;
        int width = source.getWidth(), height = source.getHeight(); boolean swap = orientation >= 5 && orientation <= 8;
        BufferedImage output = new BufferedImage(swap ? height : width, swap ? width : height, source.getTransparency() == Transparency.OPAQUE ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int targetX, targetY;
            switch (orientation) {
                case 2 -> { targetX = width - 1 - x; targetY = y; }
                case 3 -> { targetX = width - 1 - x; targetY = height - 1 - y; }
                case 4 -> { targetX = x; targetY = height - 1 - y; }
                case 5 -> { targetX = y; targetY = x; }
                case 6 -> { targetX = height - 1 - y; targetY = x; }
                case 7 -> { targetX = height - 1 - y; targetY = width - 1 - x; }
                case 8 -> { targetX = y; targetY = width - 1 - x; }
                default -> { targetX = x; targetY = y; }
            }
            output.setRGB(targetX, targetY, source.getRGB(x, y));
        }
        return output;
    }

    private byte[] resize(BufferedImage source, int maxEdge, String format) {
        int sourceMax = Math.max(source.getWidth(), source.getHeight()); BufferedImage output = source;
        if (sourceMax > maxEdge) {
            double scale = (double) maxEdge / sourceMax; int width = Math.max(1, (int) Math.round(source.getWidth() * scale)); int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            output = new BufferedImage(width, height, "png".equals(format) && source.getTransparency() != Transparency.OPAQUE ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY); graphics.drawImage(source, 0, 0, width, height, null); } finally { graphics.dispose(); }
        }
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) { if (!ImageIO.write(output, format, stream)) throw param("图片派生版本编码失败"); return stream.toByteArray(); }
        catch (IOException exception) { throw new BusinessException("图片派生版本生成失败"); }
    }

    private BusinessException param(String message) { return new BusinessException(ErrorCode.PARAM_ERROR.code(), message); }
    public record ProcessedPhoto(String contentType, String extension, int width, int height, byte[] original, byte[] thumbnail, byte[] preview) {}
}
