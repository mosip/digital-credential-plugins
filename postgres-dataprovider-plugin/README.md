## Configuration docs for Postgres Plugin

- Pre-requisites
  - Authorisation Provider should expose the unique identifier in the `sub` field of the JWT token.
      Eg. If one is using eSignet with mock-identity-system:0.10.0 and above it can be achieved by setting:
      ```mosip.mock.ida.kyc.psut.field=individualId```
      where individualId will be the identifier to locate the identity in the expected identity registry.
  - Refer to an existing table or create a new table referring data of a specific usecase.

1. The schema context containing all the required fields should be hosted in a public url.
    - Refer this link for an existing context: [Registration Receipt Schema](https://mosip.github.io/inji-config/dev1/registration-receipt-context.json)
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
    - When the authentication is done using this particular identifier then the record from certify tables can be fetched by the postgres plugin and returned as a JSON Object.

2. For referring the table creation and template insertion, see the sql scripts under db_scripts/mosip_certify/ddl folder of inji_certify: [db_scripts](https://github.com/mosip/inji-certify/tree/master/db_scripts/mosip_certify/ddl)

3. inji-config changes:
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

4. Authorization Server config changes:
    - Add the required scopes to the supported credential scopes which refers to the scope in the scope-query-mapping in the 3rd point.
    - Also add the above scopes wherever necessary so that authentication can be carried out with the particular scopes.