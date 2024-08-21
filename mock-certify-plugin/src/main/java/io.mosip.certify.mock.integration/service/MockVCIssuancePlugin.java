/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.certify.mock.integration.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.crypto.Cipher;

import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.api.spi.VCIssuancePlugin;
import io.mosip.certify.api.util.ErrorConstants;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import foundation.identity.jsonld.ConfigurableDocumentLoader;
import foundation.identity.jsonld.JsonLDException;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.canonicalizer.URDNA2015Canonicalizer;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnProperty(value = "mosip.certify.integration.vci-plugin", havingValue = "MockVCIssuancePlugin")
@Component
@Slf4j
public class MockVCIssuancePlugin implements VCIssuancePlugin {

	private static final String AES_CIPHER_FAILED = "aes_cipher_failed";
	private static final String NO_UNIQUE_ALIAS = "no_unique_alias";
	private static final String USERINFO_CACHE = "userinfo";

	@Autowired
	private SignatureService signatureService;

	@Autowired
	private CacheManager cacheManager;

	@Autowired
	private KeyStore keyStore;

	@Autowired
	private KeymanagerDBHelper dbHelper;

	private ConfigurableDocumentLoader confDocumentLoader = null;

	@Value("${mosip.certify.mock.vciplugin.verification-method}")
	private String verificationMethod;

	@Value("${mosip.certify.mock.authenticator.get-identity-url}")
	private String getIdentityUrl;

	@Value("${mosip.certify.cache.security.secretkey.reference-id}")
	private String cacheSecretKeyRefId;

	@Value("${mosip.certify.cache.security.algorithm-name}")
	private String aesECBTransformation;

	@Value("${mosip.certify.cache.secure.individual-id}")
	private boolean secureIndividualId;

	@Value("${mosip.certify.cache.store.individual-id}")
	private boolean storeIndividualId;

	@Value("#{${mosip.certify.mock.vciplugin.vc-credential-contexts:{'https://www.w3.org/2018/credentials/v1','https://schema.org/'}}}")
	private List<String> vcCredentialContexts;

    @Value("${mosip.certify.mock.vciplugin.issuer.key-cert:empty}")
	private String issuerKeyAndCertificate = null;

	@Value("${mosip.certify.mock.vciplugin.ca.key-cert:empty}")
	private String caKeyAndCertificate = null;

    private static final String ACCESS_TOKEN_HASH = "accessTokenHash";

	public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

	public static final String CERTIFY_SERVICE_APP_ID = "CERTIFY_SERVICE";

	@Override
	public VCResult<JsonLDObject> getVerifiableCredentialWithLinkedDataProof(VCRequestDto vcRequestDto, String holderId,
																			 Map<String, Object> identityDetails) throws VCIExchangeException {
		JsonLDObject vcJsonLdObject = null;
		try {
			VCResult<JsonLDObject> vcResult = new VCResult<>();
			vcJsonLdObject = buildJsonLDWithLDProof(identityDetails.get(ACCESS_TOKEN_HASH).toString());
			vcResult.setCredential(vcJsonLdObject);
			vcResult.setFormat("ldp_vc");
			return vcResult;
		} catch (Exception e) {
			log.error("Failed to build mock VC", e);
		}
		throw new VCIExchangeException();
	}

	private JsonLDObject buildJsonLDWithLDProof(String accessTokenHash)
			throws IOException, GeneralSecurityException, JsonLDException, URISyntaxException {
		OIDCTransaction transaction = getUserInfoTransaction(accessTokenHash);
		Map<String, Object> formattedMap = null;
		try{
			formattedMap = getIndividualData(transaction);
		} catch(Exception e) {
			log.error("Unable to get KYC exchange data from MOCK", e);
		}

		Map<String, Object> verCredJsonObject = new HashMap<>();
		verCredJsonObject.put("@context", vcCredentialContexts);
		verCredJsonObject.put("type", Arrays.asList("VerifiableCredential", "MOSIPVerifiableCredential"));
		verCredJsonObject.put("id", "urn:uuid:3978344f-8596-4c3a-a978-8fcaba3903c5");
		verCredJsonObject.put("issuer", "did:example:123456789");
		verCredJsonObject.put("issuanceDate", getUTCDateTime());
		verCredJsonObject.put("credentialSubject", formattedMap);

		JsonLDObject vcJsonLdObject = JsonLDObject.fromJsonObject(verCredJsonObject);
		vcJsonLdObject.setDocumentLoader(confDocumentLoader);
		// vc proof
		Date created = Date
				.from(LocalDateTime
						.parse((String) verCredJsonObject.get("issuanceDate"),
								DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN))
						.atZone(ZoneId.systemDefault()).toInstant());
		LdProof vcLdProof = LdProof.builder().defaultContexts(false).defaultTypes(false).type("RsaSignature2018")
				.created(created).proofPurpose("assertionMethod")
				.verificationMethod(URI.create(verificationMethod))
				.build();

		URDNA2015Canonicalizer canonicalizer = new URDNA2015Canonicalizer();
		byte[] vcSignBytes = canonicalizer.canonicalize(vcLdProof, vcJsonLdObject);
		String vcEncodedData = CryptoUtil.encodeToURLSafeBase64(vcSignBytes);

		JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
		jwtSignatureRequestDto.setApplicationId(CERTIFY_SERVICE_APP_ID);
		jwtSignatureRequestDto.setReferenceId("");
		jwtSignatureRequestDto.setIncludePayload(false);
		jwtSignatureRequestDto.setIncludeCertificate(true);
		jwtSignatureRequestDto.setIncludeCertHash(true);
		jwtSignatureRequestDto.setDataToSign(vcEncodedData);
		JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
		LdProof ldProofWithJWS = LdProof.builder().base(vcLdProof).defaultContexts(false)
				.jws(responseDto.getJwtSignedData()).build();
		ldProofWithJWS.addToJsonLDObject(vcJsonLdObject);
		return vcJsonLdObject;
	}

	private Map<String, Object> getIndividualData(OIDCTransaction transaction){
		String individualId = getIndividualId(transaction);
		if (individualId!=null){
			Map<String, Object> res = new RestTemplate().getForObject(
					getIdentityUrl+"/"+individualId,
					HashMap.class);
			res = (Map<String, Object>)res.get("response");
			Map<String, Object> ret = new HashMap<>();
			ret.put("vcVer", "VC-V1");
			ret.put("id", getIdentityUrl+"/"+individualId);
			ret.put("UIN", individualId);
			ret.put("name", res.get("name"));
			ret.put("fullName", res.get("fullName"));
			ret.put("gender", res.get("gender"));
			ret.put("dateOfBirth", res.get("dateOfBirth"));
			ret.put("email", res.get("email"));
			ret.put("phone", res.get("phone"));
			ret.put("addressLine1", res.get("streetAddress"));
			ret.put("province", res.get("locality"));
			ret.put("region", res.get("region"));
			ret.put("postalCode", res.get("postalCode"));
			ret.put("face", res.get("encodedPhoto"));
			return ret;
		} else {
			return new HashMap<>();
		}
	}

	protected String getIndividualId(OIDCTransaction transaction) {
		if(!storeIndividualId)
			return null;
		return secureIndividualId ? decryptIndividualId(transaction.getIndividualId()) : transaction.getIndividualId();
	}

	private String decryptIndividualId(String encryptedIndividualId) {
		try {
			Cipher cipher = Cipher.getInstance(aesECBTransformation);
			byte[] decodedBytes = Base64.getUrlDecoder().decode(encryptedIndividualId);
			cipher.init(Cipher.DECRYPT_MODE, getSecretKeyFromHSM());
            return new String(cipher.doFinal(decodedBytes, 0, decodedBytes.length));
		} catch(Exception e) {
			log.error("Error Cipher Operations of provided secret data.", e);
			throw new CertifyException(AES_CIPHER_FAILED);
		}
	}

	private Key getSecretKeyFromHSM() {
		String keyAlias = getKeyAlias(CERTIFY_SERVICE_APP_ID, cacheSecretKeyRefId);
		if (Objects.nonNull(keyAlias)) {
			return keyStore.getSymmetricKey(keyAlias);
		}
		throw new CertifyException(NO_UNIQUE_ALIAS);
	}

	private String getKeyAlias(String keyAppId, String keyRefId) {
		Map<String, List<KeyAlias>> keyAliasMap = dbHelper.getKeyAliases(keyAppId, keyRefId, LocalDateTime.now(ZoneOffset.UTC));
		List<KeyAlias> currentKeyAliases = keyAliasMap.get(KeymanagerConstant.CURRENTKEYALIAS);
		if (!currentKeyAliases.isEmpty() && currentKeyAliases.size() == 1) {
			return currentKeyAliases.get(0).getAlias();
		}
		log.error("CurrentKeyAlias is not unique. KeyAlias count: {}", currentKeyAliases.size());
		throw new CertifyException(NO_UNIQUE_ALIAS);
	}

	private static String getUTCDateTime() {
		return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
	}

	@Override
	public VCResult<String> getVerifiableCredential(VCRequestDto vcRequestDto, String holderId,
													Map<String, Object> identityDetails) throws VCIExchangeException {
		String accessTokenHash = identityDetails.get(ACCESS_TOKEN_HASH).toString();
        Map<String, Object> data = new HashMap<>();
        String documentNumber;
        try {
			documentNumber = getIndividualId(getUserInfoTransaction(accessTokenHash));
		} catch (Exception e) {
			log.error("Error getting documentNumber", e);
			throw new VCIExchangeException(ErrorConstants.VCI_EXCHANGE_FAILED);
		}

        log.info("Setting up the data for mDoc");
		data.put("issue_date", "2024-01-12");
		data.put("expiry_date", "2025-01-12");
		data.put("family_name","Agatha");
		data.put("given_name","Joseph");
		data.put("birth_date", "1994-11-06");
		data.put("issuing_country", "Island");
		data.put("document_number",documentNumber);
		data.put("driving_privileges",new HashMap<>(){{
			put("vehicle_category_code","A");
			put("issue_date","2023-01-01");
			put("expiry_date","2043-01-01");
		}});

		if(vcRequestDto.getFormat().equals("mso_mdoc")){
			VCResult<String> vcResult = new VCResult<>();
			String mdocVc = null;
			try {
				 mdocVc = new io.mosip.certify.mock.integration.mocks.MdocGenerator().generate(data,holderId, caKeyAndCertificate,issuerKeyAndCertificate);
			} catch (Exception e) {
                log.error("Exception on mdoc creation", e);
				throw new VCIExchangeException(ErrorConstants.VCI_EXCHANGE_FAILED);
			}
			vcResult.setCredential(mdocVc);
			vcResult.setFormat("mso_mdoc");
			return  vcResult;
		}
        log.error("not implemented the format {}", vcRequestDto);
		throw new VCIExchangeException(ErrorConstants.NOT_IMPLEMENTED);
	}

	public OIDCTransaction getUserInfoTransaction(String accessTokenHash) {
        return cacheManager.getCache(USERINFO_CACHE).get(accessTokenHash, OIDCTransaction.class);
	}
}
