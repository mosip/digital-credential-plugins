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
//import io.mosip.certify.util.JwkToKeyConverter
import io.mosip.certify.util.Keypair
import kotlinx.datetime.Clock
import org.bouncycastle.cert.X509v3CertificateBuilder
import java.security.KeyPair
import java.util.*


class MdocMdLBuilder {
    @Throws(JsonProcessingException::class)
    fun getEncodedMdocData(holderId: String): String {
        val mDocBuilder = buildMockMDoc(holderId)
        val mapper =
            ObjectMapper(CBORFactory())
        val cborBytes = mapper.writeValueAsBytes(mDocBuilder)

        // Encode to Base64 string
        return Base64.getUrlEncoder().encodeToString(cborBytes)
    }

    fun buildMockMDoc(holderId: String): MDoc {

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
        val issuerKeyPair: KeyPair = Keypair().generate()
//        val issuerCertificate = CertificateGenerator.issuerCertificate(issuerKeyPair)
        val issuerCertificate = CertificateGenerator.issuerCertificate(Keypair().generate())
        val caCertificate = CertificateGenerator.caCertificate()!!
        val coseCryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    ISSUER_KEY_ID,
                    AlgorithmID.ECDSA_256,
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
            .sign(validityInfo,deviceKeyInfo,coseCryptoProvider,ISSUER_KEY_ID)

        return mdoc
    }
}
