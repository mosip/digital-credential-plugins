package io.mosip.certify.util

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*


class CertificateGenerator {
    companion object {
        var rootCaCertificate: X509Certificate? = null
        val rootKeyPair: KeyPair = Keypair().generate()

        fun caCertificate(): X509Certificate? {
            if (rootCaCertificate == null) {
                val startDate = Date()

                val endDate = Date(startDate.time + 365 * 24 * 60 * 60 * 1000L)


                val issuerName = X500Name("CN=Mock CA")
                val serialNumber = BigInteger.valueOf(System.currentTimeMillis())

                val certificateBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
                    issuerName,
                    serialNumber,
                    startDate,
                    endDate,
                    issuerName,
                    rootKeyPair.public
                )

                certificateBuilder.addExtension(
                    Extension.basicConstraints,
                    true,
                    BasicConstraints(true)
                )

                val contentSigner = JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider("BC")
                    .build(rootKeyPair.private)

                rootCaCertificate = JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certificateBuilder.build(contentSigner))
            }
            println("root certificate generated")
            return rootCaCertificate
        }

        fun issuerCertificate(issuerKeyPair: KeyPair): X509Certificate {
            val issuerCertificate = X509v3CertificateBuilder(
                X500Name("CN=MDOC ROOT CA"),
                BigInteger.valueOf(SecureRandom().nextLong()),
                Date(),
                Date(System.currentTimeMillis() + 24L * 3600 * 1000),
                X500Name("CN=MDOC Test Issuer"),
                SubjectPublicKeyInfo.getInstance(issuerKeyPair.public.encoded)
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
                .addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
                .build(JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(rootKeyPair.private)).let {
                    JcaX509CertificateConverter().setProvider("BC").getCertificate(it)
                }
            println("issuer certificate generated")
            return issuerCertificate
        }
    }
}
