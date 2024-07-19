package io.mosip.certify.mock.integration.mocks

import COSE.AlgorithmID
import COSE.OneKey
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.toDE
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import io.mosip.certify.util.CertificateGenerator
import io.mosip.certify.util.IssuerKeyPairAndCertificate
import io.mosip.certify.util.Keypair
import io.mosip.certify.util.PKCS12Reader
import kotlinx.datetime.Clock
import java.io.ByteArrayInputStream
import java.security.KeyPair
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*


class MdocMdLBuilder {
    @Throws(JsonProcessingException::class)
    fun getEncodedMdocData(holderId: String, certificate: String): String {
        val mDocBuilder: MDoc = buildMockMDoc(holderId, certificate)
        val mapper =
            ObjectMapper(CBORFactory())
        val cborBytes = mapper.writeValueAsBytes(mDocBuilder)

        // Encode to Base64 string
        return Base64.getUrlEncoder().encodeToString(cborBytes)
    }


    private fun certificateFromPemFormat(pem: String): X509Certificate? {
        val encoded = pem.replace("-----BEGIN CERTIFICATE-----\n", "").replace("\n-----END CERTIFICATE-----", "")
        val decoded = Base64.getDecoder().decode(encoded)
        val inputStream = ByteArrayInputStream(decoded)

        return CertificateFactory.getInstance("X.509").generateCertificate(inputStream) as? X509Certificate
    }

    fun buildMockMDoc(holderId: String, rootCertificate: String): MDoc {

        val drivingPrivilegeObject = mapOf(
            "vehicle_category_code" to "A".toDE(),
            "issue_date" to "2023-01-01".toDE(),
            "expiry_date" to "2043-01-01".toDE()
        )
        val drivingPrivilegeArray = listOf(
            drivingPrivilegeObject.toDE()
        )
        val validityInfo =
            ValidityInfo(Clock.System.now(), Clock.System.now(), kotlinx.datetime.Instant.Companion.DISTANT_FUTURE)
//        val devicePublicKey = JwkToKeyConverter().convertJwkToPublicKey(
//            Base64.getUrlDecoder().decode(holderId.replace("did:jwk:", "")).contentToString()
//        )
        //TODO: This is taken from public key passed from issuer
        val devicePublicKey = Keypair().generate().public
        val deviceKeyInfo = DeviceKeyInfo(DataElement.fromCBOR(OneKey(devicePublicKey, null).AsCBOR().EncodeToBytes()))
        val ISSUER_KEY_ID = "ISSUER"
        val issuerKeyPairAndCertificate: IssuerKeyPairAndCertificate = PKCS12Reader.read()
//        val issuerKeyPair: KeyPair = Keypair().generate()
        val issuerKeyPair: KeyPair = issuerKeyPairAndCertificate.keyPair
//        val issuerCertificate = CertificateGenerator.issuerCertificate(issuerKeyPair)
        val issuerCertificate = issuerKeyPairAndCertificate.x509Certificate
        val caCertificate = CertificateGenerator.caCertificate()!!
        //TODO: convert pem to X509 certificate instance and use it
//        val caCertificate = certificateFromPemFormat((rootCertificate))!!
        val coseCryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    ISSUER_KEY_ID,
                    AlgorithmID.RSA_PSS_512,
                    issuerKeyPair.public,
                    issuerKeyPair.private,
                    listOf(issuerCertificate),
                    listOf(caCertificate)
                )
            )
        )

        val mdoc = MDocBuilder("org.iso.18013.5.1.mDL")
            .addItemToSign("org.iso.18013.5.1", "family_name", "Doe".toDE())
            .addItemToSign("org.iso.18013.5.1", "given_name", "John".toDE())
            .addItemToSign("org.iso.18013.5.1", "issuing_country", "US".toDE())
            .addItemToSign("org.iso.18013.5.1", "document_number", "123456789".toDE())
            .addItemToSign("org.iso.18013.5.1", "issuing_authority", "XXX".toDE())

            .addItemToSign("org.iso.18013.5.1", "issue_date", "2023-01-01".toDE())
            .addItemToSign("org.iso.18013.5.1", "expiry_date", "2043-01-01".toDE())
            .addItemToSign("org.iso.18013.5.1", "birth_date", "2003-01-01".toDE())
            .addItemToSign("org.iso.18013.5.1", "driving_privileges", drivingPrivilegeArray.toDE())
            .addItemToSign("org.iso.18013.5.1", "id", holderId.toDE())
            .sign(validityInfo, deviceKeyInfo, coseCryptoProvider, ISSUER_KEY_ID)

        return mdoc
    }
}
