package org.letsemploy.ojobpub_publisher.user;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;

/**
 * Turns whatever was uploaded or fetched into the one picture format kept (spec
 * 7.27): a square JPEG of at most {@value #SIZE} pixels, centre-cropped.
 *
 * <p>Re-encoding is the point, not a side effect. What is stored is pixels the
 * JDK drew, never the bytes that arrived, so no metadata survives - a phone
 * photo's GPS position included - and nothing but an image can be served back.
 *
 * <p>JPEG, PNG and GIF, which the JDK reads; WebP and HEIC are refused rather
 * than bringing in a library for them. The size is read from the header before
 * any pixel is decoded, so an image that would unpack to gigabytes is refused
 * from a few bytes. EXIF orientation is not applied: a phone photo stored
 * sideways comes out sideways.
 */
public final class PictureProcessor {

    /** The edge of the stored square: sharp at the largest size shown, on a dense screen. */
    public static final int SIZE = 256;
    /** What is accepted at all, in bytes; the form's limit is the same. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    /** The largest edge decoded; a camera's 50 megapixels fit, a bomb does not. */
    static final int MAX_EDGE = 10_000;
    static final String CONTENT_TYPE = "image/jpeg";

    private static final Set<String> FORMATS = Set.of("jpeg", "png", "gif");
    private static final float QUALITY = 0.85f;

    /** Why a picture was refused; each is a bundle key, {@code validation.picture.*}. */
    public enum Reason {
        EMPTY("empty"),
        FILE_TOO_BIG("fileTooBig"),
        UNSUPPORTED("unsupported"),
        UNREADABLE("unreadable"),
        TOO_MANY_PIXELS("tooManyPixels");

        private final String key;

        Reason(String key) {
            this.key = "validation.picture." + key;
        }

        public String key() {
            return key;
        }
    }

    /** A refused picture: a field error on {@code picture}, with the reason's key. */
    public static final class Rejected extends ValidationFailure {

        private final Reason reason;

        Rejected(Reason reason) {
            super("picture", reason.key());
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    private PictureProcessor() {
    }

    /** The picture to store, as JPEG bytes, or {@link Rejected}. */
    public static byte[] process(byte[] input) {
        if (input == null || input.length == 0) {
            throw new Rejected(Reason.EMPTY);
        }
        if (input.length > MAX_BYTES) {
            throw new Rejected(Reason.FILE_TOO_BIG);
        }
        return encode(square(decode(input)));
    }

    private static BufferedImage decode(byte[] input) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw new Rejected(Reason.UNSUPPORTED);
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new Rejected(Reason.UNSUPPORTED);
                }
                reader.setInput(in, true, true);
                // From the header alone: nothing is decoded yet.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1) {
                    throw new Rejected(Reason.UNREADABLE);
                }
                if (width > MAX_EDGE || height > MAX_EDGE) {
                    throw new Rejected(Reason.TOO_MANY_PIXELS);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new Rejected(Reason.UNREADABLE);
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (Rejected e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // A truncated file, a CMYK JPEG, a reader's own bug: all one answer.
            throw new Rejected(Reason.UNREADABLE);
        }
    }

    /** Centre-cropped and scaled down to at most {@link #SIZE}, on white (JPEG has no alpha). */
    static BufferedImage square(BufferedImage image) {
        int edge = Math.min(image.getWidth(), image.getHeight());
        BufferedImage current = image.getSubimage((image.getWidth() - edge) / 2,
                (image.getHeight() - edge) / 2, edge, edge);
        int target = Math.min(edge, SIZE);
        // Halving first: one bicubic step from far above the target skips pixels
        // and shimmers; repeated halves average them.
        while (edge / 2 >= target * 2) {
            edge /= 2;
            current = draw(current, edge, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, target, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage draw(BufferedImage source, int edge, Object interpolation) {
        BufferedImage out = new BufferedImage(edge, edge, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, edge, edge);
            g.drawImage(source, 0, 0, edge, edge, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encode(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }
}
