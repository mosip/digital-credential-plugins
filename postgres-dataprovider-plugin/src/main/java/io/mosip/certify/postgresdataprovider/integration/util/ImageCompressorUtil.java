package io.mosip.certify.postgresdataprovider.integration.util;

import io.mosip.certify.postgresdataprovider.integration.service.ImageCompressorServiceImpl;
import io.mosip.image.compressor.sdk.service.ImageCompressionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ImageCompressorUtil {
    @Autowired
    private ImageCompressorServiceImpl imageCompressionService;

    public byte[] compressImage(byte[] imageBytes) {
        return this.imageCompressionService.resizeAndCompress(imageBytes);
    }
}
