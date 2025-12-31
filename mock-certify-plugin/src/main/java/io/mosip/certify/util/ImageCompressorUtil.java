package io.mosip.certify.util;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.util.Base64;

@Slf4j
public class ImageCompressorUtil {

    static {
        Loader.load(opencv_java.class);
    }

    private static final int TARGET_SIZE = 1024;

    public static byte[] validateAndCompressFaceImage(byte[] imageBytes, String extension) throws Exception {

        Mat src = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);
        if (src.empty()) {
            throw new DataProviderExchangeException(
                    "INVALID_IMAGE",
                    "Unable to decode image"
            );
        }

        try {
            // 1. Pre-calculate a sensible starting scale.
            // If image is huge, 1KB is impossible without shrinking dimensions first.
            // A 1KB image is usually around 60x60 or 80x80 pixels.
            double area = src.width() * src.height();
            double targetArea = 80 * 80;
            double scale = (area > targetArea) ? Math.sqrt(targetArea / area) : 1.0;

            byte[] result = null;
            boolean isPng = ".png".equalsIgnoreCase(extension);

            // 2. The "Irrespective of Quality" Loop
            // We drop quality fast, then drop scale further if target isn't met.
            while (scale > 0.01) {
                int w = (int) Math.max(16, src.width() * scale);
                int h = (int) Math.max(16, src.height() * scale);

                Mat resized = new Mat();
                Imgproc.resize(src, resized, new Size(w, h), 0, 0, Imgproc.INTER_AREA);

                // Force JPEG for small sizes even if input was PNG (PNG is lossless/heavy)
                String outputExt = isPng ? ".png" : ".jpg";
                MatOfInt params = isPng
                        ? new MatOfInt(Imgcodecs.IMWRITE_PNG_COMPRESSION, 9)
                        : new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 40); // Start at lower quality

                MatOfByte buf = new MatOfByte();
                Imgcodecs.imencode(outputExt, resized, buf, params);
                result = buf.toArray();

                // Cleanup loop memory
                resized.release();
                buf.release();
                params.release();

                if (result.length <= TARGET_SIZE) {
                    return result;
                }

                // Aggressive reduction for next iteration
                scale *= 0.7;
            }

            return result; // Returns the smallest possible version found
        } finally {
            src.release();
        }
    }


    public static String compressImageData(String imageData) throws Exception {

        boolean isDataUrl = imageData != null && imageData.startsWith("data:");
        String base64;
        String mimeType = null;

        if (isDataUrl) {
            int comma = imageData.indexOf(',');
            if (comma < 0) {
                throw new DataProviderExchangeException(
                            "INVALID_DATA_URL",
                            "Malformed data URL"
                    );
            }

            mimeType = imageData.substring(5, comma);
            base64 = imageData.substring(comma + 1);

            if (mimeType.endsWith(";base64")) {
                mimeType = mimeType.substring(0, mimeType.length() - 7);
            }
        } else {
            base64 = imageData;
        }

        byte[] imageBytes;
        try {
            imageBytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new DataProviderExchangeException(
                    "INVALID_BASE64",
                    "The provided string is not valid Base64."
            );
        }

        if (mimeType == null) {
            mimeType = detectMimeType(imageBytes);
        }

        String extension = mimeType.contains("png") ? ".png" : ".jpg";
        mimeType = extension.equals(".png") ? "image/png" : "image/jpeg";

        byte[] compressed = validateAndCompressFaceImage(imageBytes, extension);

        if (compressed.length > TARGET_SIZE) {
            throw new DataProviderExchangeException(
                        "FACE_IMAGE_TOO_LARGE",
                        "Compressed image exceeds 1 KB."
                );
        }

        String encoded = Base64.getEncoder().encodeToString(compressed);
        return isDataUrl
                ? "data:" + mimeType + ";base64," + encoded
                : encoded;
    }

    private static String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return "image/jpeg";
        }

        // PNG
        if ((bytes[0] & 0xFF) == 0x89 &&
                bytes[1] == 0x50 &&
                bytes[2] == 0x4E &&
                bytes[3] == 0x47) {
            return "image/png";
        }

        // WebP → fallback to JPEG
        if (bytes[0] == 'R' && bytes[1] == 'I' &&
                bytes[2] == 'F' && bytes[3] == 'F' &&
                bytes[8] == 'W' && bytes[9] == 'E' &&
                bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }

        return "image/jpeg";
    }
}
