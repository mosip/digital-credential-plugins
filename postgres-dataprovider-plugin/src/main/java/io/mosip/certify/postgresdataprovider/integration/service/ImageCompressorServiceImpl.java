package io.mosip.certify.postgresdataprovider.integration.service;

import io.mosip.image.compressor.sdk.service.ImageCompressionService;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ImageCompressorServiceImpl extends ImageCompressionService {
    public ImageCompressorServiceImpl(Environment env, BiometricRecord sample, List<BiometricType> modalitiesToExtract, Map<String, String> flags) {
        super(env, sample, modalitiesToExtract, flags);
    }

    @Override
    public byte[] resizeAndCompress(byte[] imageBytes) {
        return super.resizeAndCompress(imageBytes);
    }
}
