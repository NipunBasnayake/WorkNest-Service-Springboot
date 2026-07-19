package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One decoded-image validation and transformation pipeline shared by master
 * branding assets and tenant-owned employee avatars.
 */
@Component
public class ImageAssetProcessor {

    private static final int MAX_SIDE = 4096;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    public enum Profile {
        LOGO(new int[] {64, 128, 256, 512}, false),
        AVATAR(new int[] {32, 64, 128, 256}, true);

        private final int[] variantSizes;
        private final boolean squareCrop;

        Profile(int[] variantSizes, boolean squareCrop) {
            this.variantSizes = variantSizes;
            this.squareCrop = squareCrop;
        }
    }

    public record ImageVariant(
            String name,
            int width,
            int height,
            String extension,
            String contentType,
            byte[] bytes,
            String sha256) {
    }

    public record ProcessedImage(
            String originalFilename,
            int width,
            int height,
            String extension,
            String contentType,
            byte[] bytes,
            String sha256,
            List<ImageVariant> variants) {
    }

    private record DecodedImage(BufferedImage image, String extension) {
    }

    private final StorageProperties storageProperties;
    private final AssetObservability observability;

    public ImageAssetProcessor(StorageProperties storageProperties) {
        this(storageProperties, null);
    }

    @Autowired
    public ImageAssetProcessor(StorageProperties storageProperties, AssetObservability observability) {
        this.storageProperties = storageProperties;
        this.observability = observability;
    }

    public ProcessedImage process(MultipartFile file, Profile profile) {
        long started = System.nanoTime();
        boolean success = false;
        try {
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("Image file is required");
            }
            byte[] source = readBytes(file);
            if (source.length > storageProperties.maxImageSizeBytes()) {
                throw new BadRequestException("Image size exceeds 2MB limit");
            }
            String originalFilename = normalizeFilename(file.getOriginalFilename());
            String extension = extensionOf(originalFilename);
            DecodedImage decoded = decode(source, extension);
            BufferedImage normalized = profile.squareCrop
                    ? centerCropSquare(decoded.image())
                    : decoded.image();
            ProcessedImage processed = buildProcessedImage(originalFilename, normalized, profile);
            success = true;
            return processed;
        } finally {
            if (observability != null) {
                observability.recordImageProcessing(profile, System.nanoTime() - started, success);
            }
        }
    }

    /** Used by the existing generic upload service to enforce the same decode policy. */
    public void validateDecoded(byte[] source, String extension) {
        decode(source, extension);
    }

    private ProcessedImage buildProcessedImage(String originalFilename, BufferedImage image, Profile profile) {
        String outputExtension = image.getColorModel().hasAlpha() ? "png" : "jpg";
        String contentType = outputExtension.equals("png") ? "image/png" : "image/jpeg";
        byte[] normalizedBytes = encode(image, outputExtension);
        List<ImageVariant> variants = new ArrayList<>();
        for (int size : profile.variantSizes) {
            BufferedImage variantImage = profile.squareCrop
                    ? resize(image, size, size)
                    : resizeContained(image, size);
            byte[] variantBytes = encode(variantImage, outputExtension);
            variants.add(new ImageVariant(
                    Integer.toString(size),
                    variantImage.getWidth(),
                    variantImage.getHeight(),
                    outputExtension,
                    contentType,
                    variantBytes,
                    sha256(variantBytes)
            ));
        }
        return new ProcessedImage(
                originalFilename,
                image.getWidth(),
                image.getHeight(),
                outputExtension,
                contentType,
                normalizedBytes,
                sha256(normalizedBytes),
                List.copyOf(variants)
        );
    }

    private DecodedImage decode(byte[] source, String requestedExtension) {
        String extension = normalizeExtension(requestedExtension);
        validateMagicBytes(source, extension);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (input == null) throw invalidImage();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalidImage();
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                BufferedImage image = reader.read(0);
                if (image == null) throw invalidImage();
                validateDimensions(image.getWidth(), image.getHeight());
                BufferedImage safelyDecoded = toSafeColorModel(image);
                BufferedImage oriented = applyExifOrientation(
                        safelyDecoded,
                        extension.equals("jpg") || extension.equals("jpeg") ? readJpegExifOrientation(source) : 1
                );
                return new DecodedImage(oriented, extension);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BadRequestException badRequestException) {
                throw badRequestException;
            }
            throw new BadRequestException("Image could not be decoded safely", exception);
        }
    }

    private BufferedImage toSafeColorModel(BufferedImage source) {
        boolean alpha = source.getColorModel().hasAlpha();
        BufferedImage result = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = result.createGraphics();
        try {
            if (!alpha) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            }
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private BufferedImage applyExifOrientation(BufferedImage source, int orientation) {
        if (orientation <= 1 || orientation > 8) return source;

        int width = source.getWidth();
        int height = source.getHeight();
        boolean swapSides = orientation >= 5;
        BufferedImage result = new BufferedImage(
                swapSides ? height : width,
                swapSides ? width : height,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        AffineTransform transform = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, transform, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /**
     * Reads the orientation tag from the first JPEG EXIF APP1 segment. Malformed
     * or unrelated metadata is ignored; image decoding remains the authority on
     * whether the payload itself is valid.
     */
    private int readJpegExifOrientation(byte[] source) {
        int segmentOffset = 2;
        while (segmentOffset + 4 <= source.length && (source[segmentOffset] & 0xFF) == 0xFF) {
            int marker = source[segmentOffset + 1] & 0xFF;
            if (marker == 0xDA || marker == 0xD9) break;
            int segmentLength = readUnsignedShort(source, segmentOffset + 2, false);
            if (segmentLength < 2 || segmentOffset + 2L + segmentLength > source.length) break;
            int dataOffset = segmentOffset + 4;
            int dataLength = segmentLength - 2;
            if (marker == 0xE1 && dataLength >= 14 && hasExifHeader(source, dataOffset)) {
                return readTiffOrientation(source, dataOffset + 6, dataLength - 6);
            }
            segmentOffset += segmentLength + 2;
        }
        return 1;
    }

    private boolean hasExifHeader(byte[] source, int offset) {
        return offset + 6 <= source.length
                && source[offset] == 'E' && source[offset + 1] == 'x'
                && source[offset + 2] == 'i' && source[offset + 3] == 'f'
                && source[offset + 4] == 0 && source[offset + 5] == 0;
    }

    private int readTiffOrientation(byte[] source, int tiffOffset, int tiffLength) {
        if (tiffLength < 8 || tiffOffset < 0 || tiffOffset + (long) tiffLength > source.length) return 1;
        boolean littleEndian;
        if (source[tiffOffset] == 'I' && source[tiffOffset + 1] == 'I') {
            littleEndian = true;
        } else if (source[tiffOffset] == 'M' && source[tiffOffset + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        if (readUnsignedShort(source, tiffOffset + 2, littleEndian) != 42) return 1;
        long firstIfdRelative = readUnsignedInt(source, tiffOffset + 4, littleEndian);
        long firstIfd = tiffOffset + firstIfdRelative;
        long tiffEnd = tiffOffset + (long) tiffLength;
        if (firstIfd < tiffOffset || firstIfd + 2 > tiffEnd) return 1;

        int entryCount = readUnsignedShort(source, (int) firstIfd, littleEndian);
        long entryOffset = firstIfd + 2;
        for (int index = 0; index < entryCount; index++, entryOffset += 12) {
            if (entryOffset + 12 > tiffEnd) return 1;
            int offset = (int) entryOffset;
            int tag = readUnsignedShort(source, offset, littleEndian);
            int type = readUnsignedShort(source, offset + 2, littleEndian);
            long count = readUnsignedInt(source, offset + 4, littleEndian);
            if (tag == 0x0112 && type == 3 && count == 1) {
                int orientation = readUnsignedShort(source, offset + 8, littleEndian);
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
        }
        return 1;
    }

    private int readUnsignedShort(byte[] source, int offset, boolean littleEndian) {
        if (offset < 0 || offset + 2 > source.length) return 0;
        int first = source[offset] & 0xFF;
        int second = source[offset + 1] & 0xFF;
        return littleEndian ? first | (second << 8) : (first << 8) | second;
    }

    private long readUnsignedInt(byte[] source, int offset, boolean littleEndian) {
        if (offset < 0 || offset + 4 > source.length) return 0;
        long first = source[offset] & 0xFFL;
        long second = source[offset + 1] & 0xFFL;
        long third = source[offset + 2] & 0xFFL;
        long fourth = source[offset + 3] & 0xFFL;
        return littleEndian
                ? first | (second << 8) | (third << 16) | (fourth << 24)
                : (first << 24) | (second << 16) | (third << 8) | fourth;
    }

    private BufferedImage centerCropSquare(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = Math.max(0, (source.getWidth() - side) / 2);
        int y = Math.max(0, (source.getHeight() - side) / 2);
        BufferedImage cropped = source.getSubimage(x, y, side, side);
        return toSafeColorModel(cropped);
    }

    private BufferedImage resizeContained(BufferedImage source, int maxSide) {
        double scale = Math.min(1d, (double) maxSide / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return resize(source, width, height);
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, type);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (type == BufferedImage.TYPE_INT_RGB) {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private byte[] encode(BufferedImage image, String extension) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (extension.equals("jpg")) {
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (!writers.hasNext()) throw new IllegalStateException("JPEG encoder is unavailable");
                ImageWriter writer = writers.next();
                try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
                    writer.setOutput(imageOutput);
                    ImageWriteParam params = writer.getDefaultWriteParam();
                    if (params.canWriteCompressed()) {
                        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        params.setCompressionQuality(0.88f);
                    }
                    writer.write(null, new IIOImage(image, null, null), params);
                } finally {
                    writer.dispose();
                }
            } else if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("PNG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BadRequestException("Image could not be normalized", exception);
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                || (long) width * height > MAX_PIXELS) {
            throw new BadRequestException("Image dimensions exceed the 4096px / 25 megapixel limit");
        }
    }

    private void validateMagicBytes(byte[] source, String extension) {
        boolean valid = switch (extension) {
            case "jpg", "jpeg" -> source.length >= 3
                    && (source[0] & 0xFF) == 0xFF
                    && (source[1] & 0xFF) == 0xD8
                    && (source[2] & 0xFF) == 0xFF;
            case "png" -> startsWith(source, new byte[] {
                    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            });
            case "webp" -> source.length >= 12
                    && source[0] == 'R' && source[1] == 'I' && source[2] == 'F' && source[3] == 'F'
                    && source[8] == 'W' && source[9] == 'E' && source[10] == 'B' && source[11] == 'P';
            default -> false;
        };
        if (!valid) throw new BadRequestException("Uploaded file content does not match its image extension");
    }

    private boolean startsWith(byte[] source, byte[] signature) {
        if (source.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (source[i] != signature[i]) return false;
        }
        return true;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Failed to read image", exception);
        }
    }

    private String normalizeFilename(String filename) {
        String normalized = filename == null ? "image" : filename.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        normalized = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (normalized.isBlank() || normalized.length() > 255) {
            throw new BadRequestException("Image filename is invalid");
        }
        return normalized;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            throw new BadRequestException("Image filename must have an extension");
        }
        return normalizeExtension(filename.substring(dot + 1));
    }

    private String normalizeExtension(String extension) {
        String normalized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(normalized)) {
            throw new BadRequestException("Only JPEG, PNG, and WebP images are allowed");
        }
        return normalized;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private BadRequestException invalidImage() {
        return new BadRequestException("Image could not be decoded safely");
    }
}
