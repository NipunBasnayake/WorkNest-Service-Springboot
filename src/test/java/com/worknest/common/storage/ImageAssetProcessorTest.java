package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageAssetProcessorTest {

    private final StorageProperties properties = new StorageProperties();
    private final ImageAssetProcessor processor = new ImageAssetProcessor(properties);

    @Test
    void avatarIsCenterCroppedAndProducesAllSquareVariants() throws Exception {
        MockMultipartFile file = image("../profile.jpg", "jpg", 400, 200, false);

        ImageAssetProcessor.ProcessedImage result = processor.process(file, ImageAssetProcessor.Profile.AVATAR);

        assertThat(result.originalFilename()).isEqualTo("profile.jpg");
        assertThat(result.width()).isEqualTo(200);
        assertThat(result.height()).isEqualTo(200);
        assertThat(result.sha256()).hasSize(64);
        assertThat(result.variants()).extracting(ImageAssetProcessor.ImageVariant::name)
                .containsExactly("32", "64", "128", "256");
        assertThat(result.variants()).allSatisfy(variant -> assertThat(variant.width()).isEqualTo(variant.height()));
    }

    @Test
    void appliesJpegExifOrientationBeforeGeneratingVariants() throws Exception {
        byte[] jpeg = imageBytes("jpg", 80, 40, false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "phone-photo.jpg", "image/jpeg", withExifOrientation(jpeg, 6));

        ImageAssetProcessor.ProcessedImage result = processor.process(file, ImageAssetProcessor.Profile.AVATAR);

        assertThat(result.width()).isEqualTo(40);
        assertThat(result.height()).isEqualTo(40);
        assertThat(result.variants().getFirst().width()).isEqualTo(32);
        assertThat(result.variants().getFirst().height()).isEqualTo(32);
    }

    @Test
    void rejectsSpoofedExtensionBeforeDecoding() throws Exception {
        MockMultipartFile pngNamedAsJpeg = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", imageBytes("png", 20, 20, true));

        assertThatThrownBy(() -> processor.process(pngNamedAsJpeg, ImageAssetProcessor.Profile.AVATAR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void rejectsMalformedAndOversizedPayloads() {
        MockMultipartFile malformed = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 1, 2, 3, 4});
        assertThatThrownBy(() -> processor.process(malformed, ImageAssetProcessor.Profile.AVATAR))
                .isInstanceOf(BadRequestException.class);

        properties.setMaxImageSize(DataSize.ofBytes(4));
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[5]);
        assertThatThrownBy(() -> processor.process(oversized, ImageAssetProcessor.Profile.AVATAR))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("2MB");
    }

    private MockMultipartFile image(
            String filename,
            String format,
            int width,
            int height,
            boolean alpha) throws Exception {
        return new MockMultipartFile(
                "file",
                filename,
                format.equals("png") ? "image/png" : "image/jpeg",
                imageBytes(format, width, height, alpha));
    }

    private byte[] imageBytes(String format, int width, int height, boolean alpha) throws Exception {
        BufferedImage image = new BufferedImage(
                width,
                height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(36, 99, 235, alpha ? 180 : 255));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, format, output);
            return output.toByteArray();
        }
    }

    private byte[] withExifOrientation(byte[] jpeg, int orientation) throws Exception {
        byte[] exifPayload = new byte[] {
                'E', 'x', 'i', 'f', 0, 0,
                'I', 'I', 42, 0,
                8, 0, 0, 0,
                1, 0,
                0x12, 0x01,
                3, 0,
                1, 0, 0, 0,
                (byte) orientation, 0, 0, 0,
                0, 0, 0, 0
        };
        int segmentLength = exifPayload.length + 2;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            output.write(jpeg, 0, 2);
            output.write(0xFF);
            output.write(0xE1);
            output.write((segmentLength >>> 8) & 0xFF);
            output.write(segmentLength & 0xFF);
            output.write(exifPayload);
            output.write(Arrays.copyOfRange(jpeg, 2, jpeg.length));
            return output.toByteArray();
        }
    }
}
