package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The one picture format kept (spec 7.27): square, small, re-encoded - and what
 * is refused, before it is decoded where it matters. No Spring, no database.
 */
class PictureProcessorTest {

    @Test
    void aWidePictureIsCroppedToItsCentreAndShrunk() throws IOException {
        // Red at both ends, blue in the middle square: the crop keeps only blue.
        BufferedImage wide = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = wide.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 800, 400);
        g.setColor(Color.BLUE);
        g.fillRect(200, 0, 400, 400);
        g.dispose();

        BufferedImage out = read(PictureProcessor.process(encode(wide, "png")));

        assertThat(out.getWidth()).isEqualTo(PictureProcessor.SIZE);
        assertThat(out.getHeight()).isEqualTo(PictureProcessor.SIZE);
        for (int[] at : new int[][] {{2, 2}, {253, 2}, {128, 128}, {2, 253}, {253, 253}}) {
            Color c = new Color(out.getRGB(at[0], at[1]));
            assertThat(c.getBlue()).as("pixel %s,%s", at[0], at[1]).isGreaterThan(200);
            assertThat(c.getRed()).as("pixel %s,%s", at[0], at[1]).isLessThan(60);
        }
    }

    @Test
    void aSmallPictureIsNotBlownUp() throws IOException {
        BufferedImage small = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = read(PictureProcessor.process(encode(small, "gif")));
        assertThat(out.getWidth()).isEqualTo(60);
        assertThat(out.getHeight()).isEqualTo(60);
    }

    @Test
    void transparencyBecomesWhite() throws IOException {
        BufferedImage clear = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        Color c = new Color(read(PictureProcessor.process(encode(clear, "png"))).getRGB(25, 25));
        assertThat(c.getRed()).isGreaterThan(245);
        assertThat(c.getGreen()).isGreaterThan(245);
        assertThat(c.getBlue()).isGreaterThan(245);
    }

    @Test
    void nothingButPixelsSurvives() throws IOException {
        byte[] jpeg = encode(new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB), "jpeg");
        // A comment and an EXIF block, such as a phone writes its position into.
        byte[] tagged = withSegments(jpeg,
                segment(0xFE, "GPS 47.3769 N 8.5417 E".getBytes(StandardCharsets.US_ASCII)),
                segment(0xE1, "Exif\0\0secret-camera-serial".getBytes(StandardCharsets.US_ASCII)));
        assertThat(new String(tagged, StandardCharsets.ISO_8859_1)).contains("GPS 47.3769", "secret-camera-serial");

        String out = new String(PictureProcessor.process(tagged), StandardCharsets.ISO_8859_1);

        assertThat(out).doesNotContain("GPS", "Exif", "secret-camera-serial");
    }

    @Test
    void anImageClaimingHugeDimensionsIsRefusedFromItsHeader() {
        // A PNG header alone, saying 20000 x 20000, and no pixel data at all:
        // refused for its size, which proves nothing was decoded.
        assertRefused(pngHeader(20_000, 20_000), PictureProcessor.Reason.TOO_MANY_PIXELS);
    }

    @Test
    void otherFormatsAreRefused() throws IOException {
        assertRefused(encode(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "bmp"),
                PictureProcessor.Reason.UNSUPPORTED);
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
        assertRefused(webp, PictureProcessor.Reason.UNSUPPORTED);
        assertRefused("<html><body>not a picture</body></html>".getBytes(StandardCharsets.UTF_8),
                PictureProcessor.Reason.UNSUPPORTED);
    }

    @Test
    void aBrokenFileIsUnreadable() {
        byte[] broken = ByteBuffer.allocate(40).put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})
                .put("garbage, not an IHDR chunk".getBytes(StandardCharsets.US_ASCII)).array();
        assertRefused(broken, PictureProcessor.Reason.UNREADABLE);
    }

    @Test
    void emptyAndOversizedAreRefusedUnread() {
        assertRefused(new byte[0], PictureProcessor.Reason.EMPTY);
        assertRefused(null, PictureProcessor.Reason.EMPTY);
        assertRefused(new byte[PictureProcessor.MAX_BYTES + 1], PictureProcessor.Reason.FILE_TOO_BIG);
    }

    @Test
    void everyReasonHasItsOwnKey() {
        assertThat(PictureProcessor.Reason.FILE_TOO_BIG.key()).isEqualTo("validation.picture.fileTooBig");
        assertThat(PictureProcessor.Reason.TOO_MANY_PIXELS.key()).isEqualTo("validation.picture.tooManyPixels");
    }

    private static void assertRefused(byte[] input, PictureProcessor.Reason reason) {
        assertThatThrownBy(() -> PictureProcessor.process(input))
                .isInstanceOfSatisfying(PictureProcessor.Rejected.class, e -> {
                    assertThat(e.reason()).isEqualTo(reason);
                    assertThat(e.getFieldErrors()).containsEntry("picture", reason.key());
                });
    }

    private static BufferedImage read(byte[] jpeg) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertThat(image).as("a readable JPEG").isNotNull();
        return image;
    }

    static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, out)).as("a %s writer", format).isTrue();
        return out.toByteArray();
    }

    /** A JPEG marker segment: FF, the marker, a length that counts itself, the payload. */
    private static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        return ByteBuffer.allocate(payload.length + 4).put((byte) 0xFF).put((byte) marker)
                .put((byte) (length >> 8)).put((byte) length).put(payload).array();
    }

    /** The segments inserted right after the start-of-image marker. */
    private static byte[] withSegments(byte[] jpeg, byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        for (byte[] s : segments) {
            out.writeBytes(s);
        }
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(17).put("IHDR".getBytes(StandardCharsets.US_ASCII))
                .putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        return ByteBuffer.allocate(8 + 4 + 17 + 4)
                .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})
                .putInt(13).put(ihdr.array()).putInt((int) crc.getValue()).array();
    }
}
