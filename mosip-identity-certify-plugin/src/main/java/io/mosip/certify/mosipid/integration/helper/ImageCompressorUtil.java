package io.mosip.certify.mosipid.integration.helper;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.certify.api.exception.DataProviderExchangeException;
import io.mosip.certify.mosipid.integration.service.ImageCompressorServiceImpl;
import io.mosip.kernel.biometrics.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@Slf4j
public class ImageCompressorUtil {
    private final ImageCompressorServiceImpl service;

    @Autowired
    public ImageCompressorUtil(ImageCompressorServiceImpl service) {
        this.service = service;
    }

    @Value("${mosip.certify.image-compressor.image.max-allowed-size:4096}")
    private int maxAllowedImageSize;

    @Value("${mosip.certify.image-compressor.image.max-retry-attempts:3}")
    private int maxRetryAttempts;


    public byte[] compressImage(byte[] imageBytes) {
        return service.doResizeAndCompress(imageBytes);
    }

    public String extractAndCompressImage(String imageData) throws DataProviderExchangeException {
        try {
            // --- Require Data URI with prefix only ---
            if (imageData == null || imageData.isBlank() || !imageData.startsWith("data:") || !imageData.contains(";")
                    || !imageData.contains(",")) {
                throw new IllegalArgumentException("Invalid image format. Upload a proper image type.");
            }

            // Basic structure guards
            int colon = imageData.indexOf(':');   // should be 4 ("data:")
            int semi  = imageData.indexOf(';');
            int comma = imageData.indexOf(',');
            if (colon < 0 || semi < 0 || comma < 0 || colon >= semi || semi >= comma) {
                throw new IllegalArgumentException("Invalid image format. Upload a proper image type.");
            }

            // Extract MIME (e.g., image/png, image/jpeg)
            String mimeType = imageData.substring(colon + 1, semi).trim();

            // Extract the format (e.g., "png", "jpeg", "jpg"); default "" if malformed
            int slash = mimeType.indexOf('/');
            String formatName = (slash >= 0 && slash < mimeType.length() - 1)
                    ? mimeType.substring(slash + 1).toLowerCase()
                    : "";

            // Fallback rule: anything other than png/jpeg/jpg → force JPEG
            boolean isPng  = "png".equals(formatName);
            boolean usePng = isPng;         // only true when explicitly PNG

            // Extract Base64 payload and decode
            String base64Data = imageData.substring(comma + 1).trim();
            byte[] inputBytes = Base64.getDecoder().decode(base64Data);

            // Compress (assumed JP2 output)
            int attempts = 0;
            byte[] jp2Bytes;

            while (true) {
                jp2Bytes = compressImage(inputBytes);
                attempts++;

                if (jp2Bytes.length <= maxAllowedImageSize) {
                    break;
                }
                if (attempts >= maxRetryAttempts) {
                    throw new DataProviderExchangeException(
                            "FACE_IMAGE_TOO_LARGE",
                            "Unable to compress image with available compression. Check size or quality of the input image."
                    );
                }

                // use the last compressed output as the next input
                inputBytes = jp2Bytes;
            }

            // Convert JP2 → desired output format
            final byte[] outBytes;
            final String outMime;
            if (usePng) {
                outBytes = CommonUtil.convertJP2ToPNGBytes(jp2Bytes);
                outMime  = "image/png";
            } else {
                outBytes = CommonUtil.convertJP2ToJPEGBytes(jp2Bytes);
                outMime  = "image/jpeg";
            }

            // Encode and return as Data URI
            final String b64 = Base64.getEncoder().encodeToString(outBytes);
            return "data:" + outMime + ";base64," + b64;

        } catch (IllegalArgumentException iae) {
            log.error("ERROR_PARSING_IMAGE_DATA", iae);
            throw new DataProviderExchangeException("ERROR_PARSING_IMAGE_DATA", iae.getMessage());
        } catch (Exception e) {
            log.error("Image compression failed", e);
            throw new DataProviderExchangeException(
                    "ERROR_COMPRESSING_IMAGE",
                    "Failed to compress image data. Check the image format and other properties."
            );
        }
    }
}