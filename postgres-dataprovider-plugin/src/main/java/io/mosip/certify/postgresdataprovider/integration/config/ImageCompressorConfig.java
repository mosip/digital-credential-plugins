package io.mosip.certify.postgresdataprovider.integration.config;

import io.mosip.image.compressor.sdk.service.ImageCompressionService;
import io.mosip.image.compressor.sdk.service.SDKService;
//import io.mosip.kernel.biometrics.entities.BiometricRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

@Configuration
public class ImageCompressorConfig {
    @Bean
    public ImageCompressionService imageCompressionService(Environment env) {
        return new ImageCompressionService(env, null, List.of(), Map.of());
    }
}