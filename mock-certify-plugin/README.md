# Mock Certify Plugin

The Mock Certify Plugin is a combination implementation of three plugins.

1. MockVCIssuancePlugin - A Mock-IDA plugin exposes a mock VC(unverifiable) based on the MOCK-IDA Stack, it's implemented to be closer to the MOSIP-IDA stack and is mostly for development & testing purposes. It returns a VC in a JSON-LD format.
2. MockCSVDataProviderPlugin - A CSV plugin which exposes data based on some valid claims, it is a small plugin which enables teams to get a headstart for generating VC credentials of any type. It returns the user data, which can be used by the Issuer to generate a VC.
3. MDocMockVCIssuancePlugin - A Mock-Driving license plugin which returns a Mock "Mobile Driving License"(mDL) with some hardcoded data, it returns the VC in an [mDoc format](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-13.html#name-iso-mdl).

4. MockMDocDataProviderPlugin - A mock data provider plugin that returns claim data formatted for mDL issuance. It injects metadata such as `_issuedAt`, `_validUntil`, `_issuer`, and `_docType`. Works in tandem with `MDocMockVCIssuancePlugin`.

## Configuration Options

### MockVCIssuancePlugin

```properties
mosip.certify.integration.vci-plugin=MockVCIssuancePlugin
# URL of the JWKS endpoint of the AuthZ service
mosip.certify.mock.vciplugin.verification-method=
# URL of the mock-identity-service
mosip.certify.mock.authenticator.get-identity-url=
mosip.certify.cache.security.secretkey.reference-id=TRANSACTION_CACHE
mosip.certify.cache.security.algorithm-name=AES/ECB/PKCS5Padding
mosip.certify.cache.secure.individual-id=false
mosip.certify.cache.store.individual-id=true
```


### MDocMockVCIssuancePlugin

To use this plugin set the below properties

```properties
mosip.certify.integration.vci-plugin=MDocMockVCIssuancePlugin
mosip.certify.cache.security.secretkey.reference-id=TRANSACTION_CACHE
mosip.certify.cache.security.algorithm-name=AES/ECB/PKCS5Padding
mosip.certify.cache.secure.individual-id=false
mosip.certify.cache.store.individual-id=true
# update the value as obtained from the below script
mosip.certify.mock.vciplugin.mdoc.issuer-key-cert=
```

This mDoc script can be used to generate the secrets

```bash
#!/bin/bash

generate_keys() {
    local filename_prefix=$1
    local password=$2

    PRIVATE_KEY_FILE="${filename_prefix}_private_key.pem"
    PRIVATE_KEY_FILE_IN_DER_FORMAT="${filename_prefix}_private.der"
    CERTIFICATE_FILE="${filename_prefix}_certificate.pem"

    openssl ecparam -name prime256v1 -genkey -noout -out $PRIVATE_KEY_FILE
    # Generate an EC k1 private key
    # openssl ecparam -name secp256k1 -genkey -noout -out $PRIVATE_KEY_FILE
    #
    # Generate an Ed25519 private key
    # openssl genpkey -algorithm ED25519 -out $PRIVATE_KEY_FILE
    openssl pkcs8 -topk8 -inform PEM -outform DER -in "${PRIVATE_KEY_FILE}" -nocrypt -out "${PRIVATE_KEY_FILE_IN_DER_FORMAT}"
    echo "Encoding the private key to Base64..."
    base64EncodedPrivateKey=$(base64 -i $PRIVATE_KEY_FILE_IN_DER_FORMAT)

    echo "Creating certificate..."
    openssl req -new -key $PRIVATE_KEY_FILE -out cert_request.csr
    openssl x509 -req -days 365 -in cert_request.csr -signkey $PRIVATE_KEY_FILE -out $CERTIFICATE_FILE -passin pass:$password -extensions v3_req
    echo "Encoding the certificate to Base64..."
    base64EncodedCertificate=$(base64 -i $CERTIFICATE_FILE)

    echo "Secret created successfully..."
    echo "Adding the contents into a file"
    echo "Note: Make sure to add the generated secret without any newlines in the value, as new lines presence would cause issues when service loads the secret"
    echo -n "$base64EncodedPrivateKey||$base64EncodedCertificate" > issuerSecret.txt

    echo "------------------------------------------------------------------------------"
    echo "secret is now available in issuerSecret.txt"
    echo "------------------------------------------------------------------------------"

    echo "removing the files created"
    rm $PRIVATE_KEY_FILE
    rm $PRIVATE_KEY_FILE_IN_DER_FORMAT
    rm $CERTIFICATE_FILE
    rm cert_request.csr

}

#Inputs that needs to be added
# Country Name (2 letter code) [AU]:
# State or Province Name (full name) [Some-State]:
# Locality Name (eg, city) []:
# Organization Name (eg, company) [Internet Widgits Pty Ltd]:
# Organizational Unit Name (eg, section) []:
# Common Name (e.g. server FQDN or YOUR name) []:
# Email Address []:
echo "Secret creation process started"
generate_keys "mock_issuer" "password"
```

#### Limitation of mock mDL as of now

- Certificate chain is not used for the credential signing corresponding to the  X.509 certificate available in the credential


### MockCSVDataProviderPlugin

To use this plugin set the below properties

```properties
mosip.certify.integration.data-provider-plugin=MockCSVDataProviderPlugin
# file path or HTTP(S) URL of the CSV file with the data
mosip.certify.mock.data-provider.csv-registry-uri=
# CSV file's column key for matching data row for a VC download event
mosip.certify.mock.data-provider.csv.identifier-column=
# CSV's header columns key
mosip.certify.mock.data-provider.csv.data-columns=
```

### MockMDocDataProviderPlugin

To use this plugin set the below properties

```properties
mosip.certify.integration.data-provider-plugin=MockMDocDataProviderPlugin
mosip.certify.mock.data-provider.mdoc.issuer-id=https://example-issuer.org
mosip.certify.mock.data-provider.mdoc.default-doctype=org.iso.18013.5.1.mDL
mosip.certify.mock.data-provider.mdoc.validity-days=365
```

This plugin automatically constructs mock claim data based on incoming `identityDetails`, populates metadata such as:

* `_issuer` – The Issuer ID (`issuer-id`)
* `_docType` – Document Type (`default-doctype`)
* `_issuedAt` – Current UTC Timestamp
* `_validUntil` – UTC Timestamp + `validity-days`