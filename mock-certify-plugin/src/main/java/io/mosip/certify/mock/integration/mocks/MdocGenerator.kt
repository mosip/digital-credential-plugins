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
import io.mosip.certify.util.Keypair
import io.mosip.certify.util.PKCS12Reader
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.*

class MdocGenerator {
    companion object {
        val NAMESPACE: String = "org.iso.18013.5.1"
        val DOCTYPE: String = "$NAMESPACE.mDL"
        val ALGORITHM = "SHA-256"
        val elementValue = "Doe"
        val elementKey = "family_name"
        val keyAlias = "issuer"
    }

    fun generate(): String? {
        val issuerKeyPairAndCertificate: IssuerKeyPairAndCertificate = PKCS12Reader.read()
        val deviceKeypair = Keypair().generateECDSA()
//        val issuerKeypair = Keypair().generateECDSA()
        val issuerKeypair = issuerKeyPairAndCertificate.issuerKeypair
        val hexToString =
            CBORConverter.hexToString("a66776657273696f6e63312e306f646967657374416c676f726974686d675348412d32353667646f6354797065716f72672e69736f2e31383031332e352e316c76616c756544696765737473a1716f72672e69736f2e31383031332e352e31a1005820658b608ad1804307de30eab174115e785da248941786e8fba0f802299dce1cbb6d6465766963654b6579496e666fa1696465766963654b6579a40102200121582002626e7ce741af193705df3ef3e3e57d7ab2eade917ecea39609b14fd9a16fb7225820bc58ad63a44706db2b39754802a10257cc2dba742193af4b9aabac4ecfdd408a6c76616c6964697479496e666fa4667369676e6564c074323032342d30372d33315430353a31343a34395a6976616c696446726f6dc074323032342d30372d33315430353a31343a34395a6a76616c6964556e74696cc0763130303030302d30312d30315430303a30303a30305a6e6578706563746564557064617465c0763130303030302d30312d30315430303a30303a30305a")
        println("hexToString $hexToString")

        //IssuerSigned
        val nameSpacedData: NameSpacedData =
            NameSpacedData.Builder()
//                .putEntry(NAMESPACE, elementKey, elementValue.toByteArray())
                .putEntryString(NAMESPACE, elementKey, elementValue)
                .build()
        println("nameSpacedData: ${nameSpacedData.nameSpaceNames}")
        val generatedIssuerNameSpaces: MutableMap<String, MutableList<ByteArray>> =
            MdocUtil.generateIssuerNameSpaces(nameSpacedData, Random(42), 16)
        //TODO: Namespaced data not having random and digestId associated with it, construct it
//        val generatedIssuerNameSpaces: MutableMap<String, MutableList<ByteArray>> = MdocUtil.generateIssuerNameSpaces(nameSpacedData, Random(42), 16)
//        println("generatedIssuerNameSpaces: ${generatedIssuerNameSpaces}")
        generatedIssuerNameSpaces.forEach { (issuerNamespace, issuerBytes) ->
            run {
                println("issuerNamespace ${issuerNamespace.toString()}")
                println("issuerBytes ${issuerBytes.toString()}")
            }
        }
        val calculateDigestsForNameSpace =
            MdocUtil.calculateDigestsForNameSpace(NAMESPACE, generatedIssuerNameSpaces, ALGORITHM)
        println("calculateDigestsForNameSpace $calculateDigestsForNameSpace")

        //MSO
        val mobileSecurityObjectGenerator = MobileSecurityObjectGenerator(ALGORITHM, NAMESPACE, deviceKeypair.public)
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
        println("data in cbor hex is " + cbor.joinToString("") { "%02x".format(it) })
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

