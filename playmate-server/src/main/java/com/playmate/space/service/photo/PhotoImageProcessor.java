package com.playmate.space.service.photo;

import com.playmate.space.common.ErrorCode;
import com.playmate.space.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

@Component
public class PhotoImageProcessor {
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private final PhotoProperties properties;
    public PhotoImageProcessor(PhotoProperties properties) { this.properties = properties; }

    public ProcessedPhoto process(byte[] bytes, String declaredContentType) {
        String contentType = detectContentType(bytes);
        if (declaredContentType != null && !declaredContentType.isBlank() && !contentType.equals(declaredContentType.trim().toLowerCase())) {
            throw param("文件 MIME 类型与实际图片内容不一致");
        }
        BufferedImage source;
        try { source = ImageIO.read(new ByteArrayInputStream(bytes)); }
        catch (IOException exception) { throw param("图片解码失败"); }
        if (source == null) throw param("只支持可安全解码的 JPEG 或 PNG 图片");
        int width = source.getWidth(), height = source.getHeight();
        if (width <= 0 || height <= 0 || width > properties.getMaxWidth() || height > properties.getMaxHeight()
                || (long) width * height > properties.getMaxPixels()) throw param("图片尺寸超出限制");
        String format = "image/png".equals(contentType) ? "png" : "jpg";
        return new ProcessedPhoto(contentType, format, width, height, bytes,
                resize(source, properties.getThumbnailMaxEdge(), format), resize(source, properties.getPreviewMaxEdge(), format));
    }

    private String detectContentType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (bytes.length >= PNG_MAGIC.length && Arrays.equals(Arrays.copyOf(bytes, PNG_MAGIC.length), PNG_MAGIC)) return "image/png";
        throw param("PHOTO 目前只支持经过服务器解码验证的 JPEG 或 PNG 图片");
    }

    private byte[] resize(BufferedImage source, int maxEdge, String format) {
        int sourceMax = Math.max(source.getWidth(), source.getHeight());
        BufferedImage output = source;
        if (sourceMax > maxEdge) {
            double scale = (double) maxEdge / sourceMax;
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            output = new BufferedImage(width, height, "png".equals(format) && source.getTransparency() != Transparency.OPAQUE
                    ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally { graphics.dispose(); }
        }
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(output, format, stream)) throw param("图片派生版本编码失败");
            return stream.toByteArray();
        } catch (IOException exception) { throw new BusinessException("图片派生版本生成失败"); }
    }

    private BusinessException param(String message) { return new BusinessException(ErrorCode.PARAM_ERROR.code(), message); }
    public record ProcessedPhoto(String contentType, String extension, int width, int height, byte[] original, byte[] thumbnail, byte[] preview) {}
}
