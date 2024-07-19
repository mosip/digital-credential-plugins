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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.certify.api.dto.VCRequestDto;
import io.mosip.certify.api.dto.VCResult;
import io.mosip.certify.api.exception.VCIExchangeException;
import io.mosip.certify.api.spi.VCIssuancePlugin;
import io.mosip.certify.api.util.ErrorConstants;
import io.mosip.certify.core.dto.ParsedAccessToken;
import io.mosip.certify.core.exception.CertifyException;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.kernel.keygenerator.bouncycastle.KeyGenerator;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
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
	KeyGenerator keyGenerator;

	@Autowired
	private CacheManager cacheManager;


	@Autowired
	private KeyStore keyStore;

	@Autowired
	private KeymanagerDBHelper dbHelper;

	@Autowired
	private KeymanagerService keymanagerService;

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
		System.out.println("individualId from transaction is: " + individualId);
		if (individualId!=null){
			Map<String, Object> res = new RestTemplate().getForObject(
					getIdentityUrl+"/"+individualId,
					HashMap.class);
			System.out.println("result of API: get mock data of individualID "+res);
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
		if(!storeIndividualId){
			System.out.println("returning null for getting indivisual id as its not storeIndividualId");
			return null;
		}
		return secureIndividualId ? decryptIndividualId(transaction.getIndividualId()) : transaction.getIndividualId();
	}

	private String decryptIndividualId(String encryptedIndividualId) {
		System.out.println("decrypting indivisualID from encryptedIndividualId");
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
		System.out.println("AccessToken hash in getVerifiableCredential: " + accessTokenHash);
		String individualId = getIndividualId(getUserInfoTransaction(accessTokenHash));
        System.out.println("individualId: " + individualId);

        System.out.println("inside getVC");
		System.out.println("keymanagerService.toString() "+keymanagerService.toString()+" |END");
		System.out.println("keyGenerator.getAsymmetricKey() "+keyGenerator.getAsymmetricKey()+" |END");
		KeyPairGenerateResponseDto certificateResponse = keymanagerService.getCertificate(CERTIFY_SERVICE_APP_ID, Optional.empty());
		System.out.println("Certificate is "+ certificateResponse);
		System.out.println("----");
		if(vcRequestDto.getFormat().equals("mso_mdoc")){
			System.out.println("getVerifiableCredential: inside msomdoc");
			VCResult<String> vcResult = new VCResult<>();
//			java.lang.String mdocVc = "b900036776657273696f6e63312e3069646f63756d656e747381b9000367646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c6973737565725369676e6564b900026a6e616d65537061636573b90001716f72672e69736f2e31383031332e352e318cd8185867b90004686469676573744944006672616e646f6d5820e489ebe4d99b1edbaabae79bd495eff428c7fdd20a75ddb1eef50e89cbe8abea71656c656d656e744964656e7469666965726b66616d696c795f6e616d656c656c656d656e7456616c756565536d697468d8185865b90004686469676573744944016672616e646f6d5820e0ee8716f75224b2e390c1af93814a6b2e64a4297bea2e0fe0fe27215e0642bd71656c656d656e744964656e7469666965726a676976656e5f6e616d656c656c656d656e7456616c7565644a6f686ed818586eb90004686469676573744944026672616e646f6d5820765c155e4a2e3f5e26aca6dc2af1059e762ca71d9fea700622b1a0cd9f4a5f7771656c656d656e744964656e7469666965726a62697274685f646174656c656c656d656e7456616c7565d903ec6a313938302d30362d3135d818586eb90004686469676573744944036672616e646f6d5820736033cbe053f4d2b28a9e5d48a0299b821b38af7052e2c519ac9e33a5377c5d71656c656d656e744964656e7469666965726a69737375655f646174656c656c656d656e7456616c7565d903ec6a323032332d30332d3031d818586fb90004686469676573744944046672616e646f6d5820c12f1094c34862f43ba502b8094d36571ec0df4ebe7f6814b6ec074695161faa71656c656d656e744964656e7469666965726b6578706972795f646174656c656c656d656e7456616c7565d903ec6a323032382d30332d3331d8185868b90004686469676573744944056672616e646f6d58207f2860fc7e2673e7b41c23fda4ead48b4db8f510a1af5e6d65bca8ecda07ec2f71656c656d656e744964656e7469666965726f69737375696e675f636f756e7472796c656c656d656e7456616c7565625553d818586eb90004686469676573744944066672616e646f6d582054d91d9de971d05bd611a4b60d43d3d4b88eeb495bf9983febb1ba047967121771656c656d656e744964656e7469666965727169737375696e675f617574686f726974796c656c656d656e7456616c7565664e5920444d56d8185873b90004686469676573744944076672616e646f6d5820aee2dd131c8a000cd234ed842ce1b4cf13d5abd801278ff76b7cd35a4610850571656c656d656e744964656e7469666965727469737375696e675f6a7572697364696374696f6e6c656c656d656e7456616c7565684e657720596f726bd8185871b90004686469676573744944086672616e646f6d5820694798e9920715f2821ed477e08bec5884d8c73aa06ec59b083e62db81bce69671656c656d656e744964656e7469666965726f646f63756d656e745f6e756d6265726c656c656d656e7456616c75656b30312d3333332d37303730d8185863b90004686469676573744944096672616e646f6d58207f3e751dd77f62af8719abb1dd547a34dff4536ce5dc7a0dfbce5bea64b5dc2871656c656d656e744964656e74696669657268706f7274726169746c656c656d656e7456616c75656462737472d81858b1b900046864696765737449440a6672616e646f6d5820c974f2750b1108e3aa424e0f2f2d75862b4fe3905926c4ef38a9049830a794e671656c656d656e744964656e7469666965727264726976696e675f70726976696c656765736c656c656d656e7456616c756581b900037576656869636c655f63617465676f72795f636f646561436a69737375655f646174656a323032332d30332d30316b6578706972795f646174656a323032382d30332d3331d818587ab900046864696765737449440b6672616e646f6d5820732a551ca3a640f54225fb327f8b2be11ef5a6abc4e84b11a120abc27189425d71656c656d656e744964656e74696669657276756e5f64697374696e6775697368696e675f7369676e6c656c656d656e7456616c75656d7462642d75732e6e792e646d766a697373756572417574688443a10126a20442313118218159022e3082022a308201d0a003020102021457c6ccd308bde43eca3744f2a87138dabbb884e8300a06082a8648ce3d0403023053310b30090603550406130255533111300f06035504080c084e657720596f726b310f300d06035504070c06416c62616e79310f300d060355040a0c064e5920444d56310f300d060355040b0c064e5920444d56301e170d3233303931343134353531385a170d3333303931313134353531385a3053310b30090603550406130255533111300f06035504080c084e657720596f726b310f300d06035504070c06416c62616e79310f300d060355040a0c064e5920444d56310f300d060355040b0c064e5920444d563059301306072a8648ce3d020106082a8648ce3d03010703420004893c2d8347906dc6cd69b7f636af4bfd533f96184f0aadacd10830da4471dbdb60ac170d1cfc534fae2d9dcd488f7747fdf978d925ea31e9e9083c382ba9ed53a38181307f301d0603551d0e04160414ab6d2e03b91d492240338fbccadefd9333eaf6c7301f0603551d23041830168014ab6d2e03b91d492240338fbccadefd9333eaf6c7300f0603551d130101ff040530030101ff302c06096086480186f842010d041f161d4f70656e53534c2047656e657261746564204365727469666963617465300a06082a8648ce3d0403020348003045022009fd0cab97b03e78f64e74d7dcee88668c476a0afc5aa2cebffe07d3be772ea9022100da38abc98a080f49f24ffece1fffc8a6cdd5b2c0b5da8fc7b767ac3a95dcb83e590319d818590314b900066776657273696f6e63312e306f646967657374416c676f726974686d675348412d3235366c76616c756544696765737473b900026f6f72672e637573746f6d2e74657374a10058209a5d928fdfd31cd8856843d4eb7697d2562dcc47d428067e81700ea3ada7cac4716f72672e69736f2e31383031332e352e31ac0058205c204814d6d07f82254f9a168cec60aba73d0519a29f695da989bcbfc4678e7201582017cbff2ffef61cd276e0b76a9370d56e1c4aef284a79f5bc0a5d1faa5fd0148602582021e81173edf1ea33735093b33e486b6b2dc84b5735856ad022512eeda240a9fc035820c902c1d90f5583f223f353938c460c5fa11553ee77034e69e78c992e1a94bc06045820820caae12904ac2e1220b4b5def9732f46f3e7ecb74a047f853c98420377ea08055820e0ae15e29966cb09cbfe17bde19be10742cf268c3d232f049a198569571c4d7206582065240ccb8948c8a369d81365e7d55e2e5ef8a42f11a135d9532919600ab5f5d6075820820917290ab590c12c5183f250599c7b28610428a7fe02d5f7bfba5e0b7dd6ad0858205c2826bb301e3e984345679cd9de551627a8c9d8b8f3fd929f7e9b65139e46c2095820356abf222eff02e18792888c695a4d39180f8ae3af4e6abaea3c298312445c950a582079d4076a45e341ce9b2ef73a028c7e1d0de3bd41911fa5fc1e5a6f2225472d8c0b5820bb28a9c7003d5dadf82f56944636ff68ffd44686169f0afe597cea95d6bfd3d26d6465766963654b6579496e666fb90001696465766963654b6579a40102215820881879ca7a238b19bf0f4c1f8c00e9a2e19ba7a6f73eae92b851d4de1b508559225820a314b538039127b5cd50735f54519e33c134450545c5603ad9f263facc56d377200167646f6354797065756f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fb90003667369676e6564c074323032332d30392d32365431373a31313a31385a6976616c696446726f6dc074323032332d30392d32365431373a31313a31385a6a76616c6964556e74696cc074323037332d30392d32365431373a31313a31385a5840a93de9b2b72375033066c4f3a5eb135e6866ce425c4d7a9de334a2fff06fc79c324081824b3bcd471a32936a52ba4fa2e47666461841f26b027e88de41becb946c6465766963655369676e6564b900026a6e616d65537061636573d81843b900006a64657669636541757468b900016f6465766963655369676e61747572658443a10126a10442313158d4d81858d0847444657669636541757468656e7469636174696f6e83f6f68466313233343536782b437131616e506238765a55356a354330643768637362754a4c4270496177554a4944515269324562776234785c687474703a2f2f6c6f63616c686f73743a343030302f6170692f70726573656e746174696f6e5f726571756573742f64633839393964662d643665612d346338342d393938352d3337613862383161383265632f63616c6c6261636b6761626364656667756f72672e69736f2e31383031332e352e312e6d444cd81843b900005840c944b61ef99e49251a8660da564eb9188dc19530dec7dda6fe57aebf768257bea761fba4c9555f4d3d0d3832fc2a5a714818143858eaa9072b6592e78e52c7006673746174757300";
			String mdocVc = null;
			try {
				mdocVc = new io.mosip.certify.mock.integration.mocks.MdocMdLBuilder().getEncodedMdocData(holderId,certificateResponse.getCertificate());
			} catch (JsonProcessingException e) {
				log.error("Exception on mdoc creation "+ mdocVc);
			}
			vcResult.setCredential(mdocVc);
			vcResult.setFormat("mso_mdoc");
			return  vcResult;
		}
		System.out.println("not implemented the format "+vcRequestDto);
		throw new VCIExchangeException(ErrorConstants.NOT_IMPLEMENTED);
	}

	public OIDCTransaction getUserInfoTransaction(String accessTokenHash) {
		Cache cache = cacheManager.getCache(USERINFO_CACHE);
		System.out.println("testing");
		System.out.println("cache name "+cache.getName());
		System.out.println("Getting cache for accessTokenHash "+accessTokenHash);
		System.out.println("cache native cache "+cache.getNativeCache().toString());
		System.out.println("cache values "+cache.toString());
        System.out.println("before storing in var "+ cache.get(accessTokenHash));
		OIDCTransaction oidcTransaction = cache.get(accessTokenHash, OIDCTransaction.class);
        System.out.println("got oidcTransaction from cache successfully");
		System.out.println("oidcTransaction from cahce is "+oidcTransaction.toString());
		return oidcTransaction;
	}
}
