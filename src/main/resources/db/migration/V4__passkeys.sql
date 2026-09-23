-- Spring Security's passkey tables, copied from spring-security-web 7.1.1 with
-- tabs as spaces (org/springframework/security/user-entities-schema.sql and
-- user-credentials-schema-postgres.sql), so Flyway owns them like every other.
-- Only Spring's JdbcPublicKeyCredentialUserEntityRepository and
-- JdbcUserCredentialRepository read and write them. Upgrading Spring Security
-- means comparing this file against its new copies.
--
-- user_entities.name is the login's principal name, such as member:7 or
-- administrator:3, and links a passkey to its owner. It is no foreign key,
-- because it points at one of two tables, so PasskeyService deletes the
-- passkeys when their owner goes: docs/decisions/0016.
--
-- The timestamps are Spring's `timestamp`, not `timestamptz`, because the
-- repository writes java.sql.Timestamp in the JVM's zone and reads it back the
-- same way. The container runs in UTC.

create table user_entities
(
    id           varchar(1000) not null,
    name         varchar(100)  not null,
    display_name varchar(200),
    primary key (id)
);

create table user_credentials
(
    credential_id                varchar(1000) not null,
    user_entity_user_id          varchar(1000) not null,
    public_key                   bytea         not null,
    signature_count              bigint,
    uv_initialized               boolean,
    backup_eligible              boolean       not null,
    authenticator_transports     varchar(1000),
    public_key_credential_type   varchar(100),
    backup_state                 boolean       not null,
    attestation_object           bytea,
    attestation_client_data_json bytea,
    created                      timestamp,
    last_used                    timestamp,
    label                        varchar(1000) not null,
    primary key (credential_id)
);
