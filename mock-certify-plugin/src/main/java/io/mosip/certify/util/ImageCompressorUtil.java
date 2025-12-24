package io.mosip.certify.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class ImageCompressorUtil {
    @Autowired
    private ImageCompressorServiceImpl imageCompressionService;

    public byte[] compressImage(byte[] imageBytes) {
        return this.imageCompressionService.resizeAndCompress(imageBytes);
    }
}
