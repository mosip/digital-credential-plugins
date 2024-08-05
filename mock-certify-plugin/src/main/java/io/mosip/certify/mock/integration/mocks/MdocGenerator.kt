package io.mosip.certify.mock.integration.mocks

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.model.DataItem
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.util.Timestamp
import io.mosip.certify.util.CBORConverter
import io.mosip.certify.util.IssuerKeyPairAndCertificate
import io.mosip.certify.util.JwkToKeyConverter
import io.mosip.certify.util.PKCS12Reader
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.*

class MdocGenerator {
    companion object {
        val NAMESPACE: String = "org.iso.18013.5.1"
        val DOCTYPE: String = "$NAMESPACE.mDL"
        val ALGORITHM = "SHA-256"
        val keyAlias = "issuer"
    }

    fun generate(data: Map<String, Any>,holderId:String): String? {
        println("holderId = "+holderId)
        val issuerKeyPairAndCertificate: IssuerKeyPairAndCertificate = PKCS12Reader.read()
        val devicePublicKey = JwkToKeyConverter().convertToPublicKey(holderId.replace("did:jwk:", ""))
        val issuerKeypair = issuerKeyPairAndCertificate.issuerKeypair

        val nameSpacedDataBuilder: NameSpacedData.Builder = NameSpacedData.Builder()
        data.keys.forEach { key ->
            nameSpacedDataBuilder.putEntryString(NAMESPACE, key, data.get(key).toString())
        }
        val nameSpacedData: NameSpacedData =
            nameSpacedDataBuilder
                .build()
        val generatedIssuerNameSpaces: MutableMap<String, MutableList<ByteArray>> =
            MdocUtil.generateIssuerNameSpaces(nameSpacedData, Random(42), 16)
        val calculateDigestsForNameSpace =
            MdocUtil.calculateDigestsForNameSpace(NAMESPACE, generatedIssuerNameSpaces, ALGORITHM)

        val mobileSecurityObjectGenerator = MobileSecurityObjectGenerator(ALGORITHM, NAMESPACE, devicePublicKey)
        mobileSecurityObjectGenerator.addDigestIdsForNamespace(NAMESPACE, calculateDigestsForNameSpace)
        val distantFuture: Long = kotlinx.datetime.Instant.Companion.DISTANT_FUTURE.toEpochMilliseconds()
        mobileSecurityObjectGenerator.setValidityInfo(
            Timestamp.now(),
            Timestamp.now(),
            Timestamp.ofEpochMilli(distantFuture),
            Timestamp.ofEpochMilli(distantFuture),
        )
        val mso: ByteArray = mobileSecurityObjectGenerator.generate()
        println("mso ${mso}")
        println("decoded ${Util.cborDecode(mso)}")

        val coseSign1Sign: DataItem = Util.coseSign1Sign(
            issuerKeypair.private,
            "SHA256withECDSA",
            mso.copyOf(),
            null,
            listOf(issuerKeyPairAndCertificate.caCertificate, issuerKeyPairAndCertificate.issuerCertificate)
        )
        val cborEncoded = Util.cborEncode(coseSign1Sign)
        println("coseS " + Base64.getEncoder().encode(cborEncoded))
        println("coseS " + Util.cborDecode(cborEncoded))

        return construct(generatedIssuerNameSpaces, coseSign1Sign)
    }

    private fun construct(nameSpaces: MutableMap<String, MutableList<ByteArray>>, issuerAuth: DataItem): String? {
        val mDoc = MDoc(DOCTYPE, IssuerSigned(nameSpaces, issuerAuth))
        val cbor = mDoc.toCBOR()
        println("data in cbor base64 is " + Base64.getEncoder().encodeToString(cbor))
        return Base64.getEncoder().encodeToString(cbor)
    }
}

@Serializable
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

@Serializable
data class IssuerSigned(val nameSpaces: MutableMap<String, MutableList<ByteArray>>, val issuerAuth: DataItem) {
    fun toMap(): Map<String, Any> {
        return buildMap {
            put("nameSpaces", CBORConverter.toDataItem(nameSpaces))
            put("issuerAuth", issuerAuth)
        }
    }

}

private fun NameSpacedData.toValidString(): MutableMap<String, Any> {
    val namespacesNames = this.nameSpaceNames
    println("toString: $namespacesNames")
    val nameSpacesMap: MutableMap<String, Any> = mutableMapOf()
    namespacesNames.forEach { namespace ->
        val dataElementNames: MutableList<String> = this.getDataElementNames(namespace)
        println("dataElementNames: $dataElementNames")
        val nameSpaceElements = ArrayList<Any>()
        dataElementNames.forEachIndexed { index, name ->
            val dataElementValue = this.getDataElement(namespace, name).decodeToString()
            nameSpaceElements.add(
                mutableMapOf(
                    "digestId" to index,
                    "elementIdentifier" to name,
                    "elementValue" to dataElementValue,
                    "random" to "randommm"
                )
            )
        }
        nameSpacesMap.put(namespace, nameSpaceElements)
    }

    return nameSpacesMap;
}

