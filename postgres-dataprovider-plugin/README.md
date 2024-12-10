## Configuration docs for Postgres Plugin

- Pre-requisites
  Authorisation Provider should expose the unique identifier in the `sub` field of the JWT token.
  Eg. If one is using eSignet with mock-identity-system:0.10.0 and above it can be achieved by setting:
  ```mosip.mock.ida.kyc.psut.field=individualId```
  where individualId will be the identifier to locate the identity in the expected identity registry.

1. Create the tables with all the fields that should be returned by the Postgres Data Provider Plugin within the certify postgres database.
    - Refer the following query for insertion in DB:
      ```
        CREATE TABLE certify.<table_name> (
                 attribute_1 <type> NOT NULL,
                 attribute_2 <type> NOT NULL,
                 ...
                 CONSTRAINT pk_id_code PRIMARY KEY (<identifier>)
             );
      ```

2. The schema context containing all the required fields should be hosted in a public url.
    - Refer this link for an existing context: [Registration Receipt Schema](https://piyush7034.github.io/my-files/registration_receipt.json)
      Eg: https://<username>.github.io/<project_name>/<file_name>.json
    - Also change the respective credential name:
      ```
         {
             "@context": {
                 "@version": 1.1,
                 "@protected": true,
                 "type": "@type",
                 "schema": "https://schema.org/",
                 "<credential_name>": {
                     "@id": "https://<username>.github.io/<project_name>/<file_name>.json#<credential_name>"
                 },
                 <field1>: "schema:<type>"
                 <field2>: "schema:<type>"
                 ...
             }
         }
      ```
    - The primary_key should be a identifier that is existing in the current mock_identity_system records.
      Eg: If "1234" is present in mock_identity table, then same identifier should be used for inserting records in the certify data tables
    - When the authentication is done using this particular identifier then the record from certify tables can be fetched by the postgres plugin and returned as a JSON Object.

3. Insert the templates in the DB with credential subject containing all the fields which must be the part of issued credential.
    - Eg: Find the below template for reference:
         ```
             {
               "@context": [
                 "https://www.w3.org/2018/credentials/v1",
                 "https://piyush7034.github.io/my-files/registration-receipt.json",
                 "https://w3id.org/security/suites/ed25519-2020/v1"
               ],
               "issuer": "${issuer}",
               "type": [
                 "VerifiableCredential",
                 "RegistrationReceiptCredential"
               ],
               "issuanceDate": "${validFrom}",
               "expirationDate": "${validUntil}",
               "credentialSubject": {
                   "attributeName1": "${<attribute1>}",
                   "attributeName2": "${<attribute2>}"
                   ...
               }
             }
         ```
    - For referring the table creation and template insertion, see the sql scripts under certify_init.sql file: [certify-init](https://github.com/mosip/inji-certify/blob/develop/docker-compose/docker-compose-injistack/certify_init.sql)

4. inji-config changes:
    - Refer to the properties file in [inji-config](https://github.com/mosip/inji-config) that corresponds to the postgres plugin implementation.
      [Certify Postgres Land Registry](https://github.com/mosip/inji-config/blob/develop/certify-postgres-landregistry.properties)
    - The value for the property `mosip.certify.integration.data-provider-plugin` must be set to `PostgresDataProviderPlugin`
    - Refer to the below property for setting the query value against the scope for the credential that is to be issued:
       ```
      mosip.certify.data-provider-plugin.postgres.scope-query-mapping={
         `credential_scope`: `select * from certify.<table_name> where <table_id>=:id`
       }
      ```
    - Add the scope defined above and the type of credential in the well-known config of the properties file. Refer to the property `mosip.certify.key-values` for the same.
    - Add the fields from the respective table in the well-known config.

5. mosip-config changes:
    - Refer to the [authentication-config](https://github.com/mosip/mosip-config/pull/7653) properties file in mosip-config repo(esignet-mock in this case).
    - Add the required scopes under `mosip.esignet.supported.credential.scopes` config.
    - Also add the scopes under `mosip.esignet.credential.scope-resource-mapping` config.