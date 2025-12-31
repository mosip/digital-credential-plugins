package io.mosip.certify.util;

import io.mosip.certify.api.exception.DataProviderExchangeException;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.Assert.*;

public class ImageCompressorUtilTest {

    // Helper to create a dummy image for testing
    private byte[] createDummyImage(int width, int height, String format) throws IOException {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, format, baos);
        return baos.toByteArray();
    }

    @Test
    public void testCompressLargeImage_ShouldBeUnder1KB() throws Exception {
        // Create a large 500x500 image (definitely > 1KB)
        byte[] largeImage = createDummyImage(500, 500, "jpg");

        byte[] result = ImageCompressorUtil.validateAndCompressFaceImage(largeImage, ".jpg");

        assertNotNull(result);
        assertTrue("Image should be compressed to under 1024 bytes", result.length <= 1024);
    }

    @Test
    public void testCompressImageData_WithDataUrl() throws Exception {
        byte[] rawImage = createDummyImage(100, 100, "png");
        String base64Image = Base64.getEncoder().encodeToString(rawImage);
        String dataUrl = "data:image/png;base64," + base64Image;

        String result = ImageCompressorUtil.compressImageData(dataUrl);

        assertTrue("Result should still be a Data URL", result.startsWith("data:image/"));
        assertTrue("Result should contain base64 separator", result.contains(","));

        // Extract base64 part and check size
        String resultBase64 = result.substring(result.indexOf(",") + 1);
        byte[] decoded = Base64.getDecoder().decode(resultBase64);
        assertTrue("Decoded bytes should be <= 1024", decoded.length <= 1024);
    }

    @Test
    public void testCompressImageData_PlainBase64() throws Exception {
        byte[] rawImage = createDummyImage(200, 200, "jpg");
        String base64Image = Base64.getEncoder().encodeToString(rawImage);

        String result = ImageCompressorUtil.compressImageData(base64Image);

        assertFalse("Result should not be a Data URL", result.startsWith("data:"));
        byte[] decoded = Base64.getDecoder().decode(result);
        assertTrue("Decoded bytes should be <= 1024", decoded.length <= 1024);
    }

    @Test(expected = DataProviderExchangeException.class)
    public void testCompressInvalidImage_ShouldThrowException() throws Exception {
        byte[] invalidData = "not-an-image".getBytes();
        ImageCompressorUtil.validateAndCompressFaceImage(invalidData, ".jpg");
    }

    @Test
    public void testMimeTypeDetection_PNG() throws Exception {
        byte[] pngBytes = createDummyImage(10, 10, "png");
        String base64 = Base64.getEncoder().encodeToString(pngBytes);

        // This triggers detectMimeType inside the utility
        String result = ImageCompressorUtil.compressImageData(base64);

        // Since it's plain base64 input, it returns plain base64 output.
        // We check if it processed without error.
        assertNotNull(result);
    }

    @Test(expected = DataProviderExchangeException.class)
    public void testMalformedDataUrl_ShouldThrowException() throws Exception {
        String malformedUrl = "data:image/png;base64WithoutComma";
        ImageCompressorUtil.compressImageData(malformedUrl);
    }
}