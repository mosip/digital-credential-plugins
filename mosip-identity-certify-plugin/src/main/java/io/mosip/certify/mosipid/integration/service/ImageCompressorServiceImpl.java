package io.mosip.certify.mosipid.integration.service;

import io.mosip.image.compressor.sdk.service.ImageCompressionService;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class ImageCompressorServiceImpl extends ImageCompressionService {
    @Autowired
    public ImageCompressorServiceImpl(Environment env) {
        super(env, new BiometricRecord(), List.of(BiometricType.FACE), new HashMap<>());
    }

    public byte[] doResizeAndCompress(byte[] imageBytes) {
        return resizeAndCompress(imageBytes);
    }
}