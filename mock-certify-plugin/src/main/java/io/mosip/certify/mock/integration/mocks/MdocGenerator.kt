package io.mosip.certify.mock.integration.mocks

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.util.Timestamp
import io.mosip.certify.util.*
import java.io.ByteArrayOutputStream
import io.mosip.certify.util.IssuerKeyPairAndCertificate
import java.util.*


class MdocGenerator {
    companion object {
        const val NAMESPACE: String = "org.iso.18013.5.1"
        const val DOCTYPE: String = "$NAMESPACE.mDL"
        const val DIGEST_ALGORITHM = "SHA-256"
        const val ECDSA_ALGORITHM = "SHA256withECDSA"
        const val SEED = 42L
    }

    fun generate(
        data: MutableMap<String, out Any>,
        holderId: String,
        caKeyAndCertificate: String,
        issuerKeyAndCertificate: String
    ): String? {
        val issuerKeyPairAndCertificate: IssuerKeyPairAndCertificate? = readKeypairAndCertificates(
            caKeyAndCertificate,issuerKeyAndCertificate
        )
        if(issuerKeyPairAndCertificate == null) {
            throw RuntimeException("Unable to load Crypto details")
        }
        val devicePublicKey = JwkToKeyConverter().convertToPublicKey(holderId.replace("did:jwk:", ""))
        val issuerKeypair = issuerKeyPairAndCertificate.issuerKeypair()

        val nameSpacedDataBuilder: NameSpacedData.Builder = NameSpacedData.Builder()
        data.keys.forEach { key ->
            nameSpacedDataBuilder.putEntryString(NAMESPACE, key, data[key].toString())
        }
        val nameSpacedData: NameSpacedData =
            nameSpacedDataBuilder
                .build()
        val generatedIssuerNameSpaces: MutableMap<String, MutableList<ByteArray>> =
            MdocUtil.generateIssuerNameSpaces(nameSpacedData, Random(SEED), 16)
        val calculateDigestsForNameSpace =
            MdocUtil.calculateDigestsForNameSpace(NAMESPACE, generatedIssuerNameSpaces, DIGEST_ALGORITHM)

        val mobileSecurityObjectGenerator = MobileSecurityObjectGenerator(DIGEST_ALGORITHM, NAMESPACE, devicePublicKey)
        mobileSecurityObjectGenerator.addDigestIdsForNamespace(NAMESPACE, calculateDigestsForNameSpace)
        val expirationTime: Long = kotlinx.datetime.Instant.Companion.DISTANT_FUTURE.toEpochMilliseconds()
        mobileSecurityObjectGenerator.setValidityInfo(
            Timestamp.now(),
            Timestamp.now(),
            Timestamp.ofEpochMilli(expirationTime),
            null
        )
        val mso: ByteArray = mobileSecurityObjectGenerator.generate()

        val coseSign1Sign: DataItem = Util.coseSign1Sign(
            issuerKeypair.private,
            ECDSA_ALGORITHM,
            mso.copyOf(),
            null,
            listOf(issuerKeyPairAndCertificate.caCertificate(), issuerKeyPairAndCertificate.issuerCertificate())
        )

        return construct(generatedIssuerNameSpaces, coseSign1Sign)
    }

    @Throws(Exception::class)
    private fun readKeypairAndCertificates(caKeyAndCertificate: String,issuerKeyAndCertificate: String): IssuerKeyPairAndCertificate? {
        val pkcS12Reader = PKCS12Reader()
        val caDetails: KeyPairAndCertificate = pkcS12Reader.extract(caKeyAndCertificate)
        val issuerDetails: KeyPairAndCertificate = pkcS12Reader.extract(issuerKeyAndCertificate)
        if (issuerDetails != null && caDetails != null) {
            return IssuerKeyPairAndCertificate(
                issuerDetails.keyPair,
                issuerDetails.certificate,
                caDetails.certificate
            )
        }
        return null
    }

    private fun construct(nameSpaces: MutableMap<String, MutableList<ByteArray>>, issuerAuth: DataItem): String? {
        val mDoc = MDoc(DOCTYPE, IssuerSigned(nameSpaces, issuerAuth))
        val cbor = mDoc.toCBOR()
        return Base64.getUrlEncoder().encodeToString(cbor)
    }
}

data class MDoc(val docType: String, val issuerSigned: IssuerSigned) {
    fun toCBOR(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        CborEncoder(byteArrayOutputStream).encode(
            CborBuilder().addMap()
                .put("docType", docType)
                .put(CBORConverter.toDataItem("issuerSigned"), CBORConverter.toDataItem(issuerSigned.toMap()))
                .end()
                .build()
        )
        return byteArrayOutputStream.toByteArray()

    }
}

data class IssuerSigned(val nameSpaces: MutableMap<String, MutableList<ByteArray>>, val issuerAuth: DataItem) {
    fun toMap(): Map<String, Any> {
        return buildMap {
            put("nameSpaces", CBORConverter.toDataItem(nameSpaces))
            put("issuerAuth", issuerAuth)
        }
    }
}
