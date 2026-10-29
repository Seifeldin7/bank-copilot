create extension if not exists vector;

create table app_user (
    id            uuid primary key,
    username      varchar(30)  not null unique,
    password_hash varchar(100) not null,
    full_name     varchar(120) not null,
    role          varchar(16)  not null check (role in ('EMPLOYEE', 'SUPERVISOR')),
    branch_code   varchar(7)   not null,
    enabled       boolean      not null default true,
    created_at    timestamptz  not null
);

create table document (
    id                uuid primary key,
    title             varchar(200) not null,
    file_name         varchar(200) not null,
    format            varchar(10)  not null check (format in ('PDF', 'MARKDOWN')),
    category          varchar(60)  not null,
    version           int          not null check (version >= 1),
    content_sha256    varchar(64)  not null unique,
    status            varchar(12)  not null check (status in ('ACTIVE', 'SUPERSEDED', 'RETIRED')),
    source            varchar(8)   not null check (source in ('CORPUS', 'UPLOAD')),
    storage_path      varchar(400),
    page_count        int          not null,
    chunk_count       int          not null,
    suspicious_chunks int          not null,
    ingested_by       uuid references app_user (id),
    ingested_at       timestamptz  not null,
    superseded_at     timestamptz
);

create unique index document_active_file_name on document (file_name) where status = 'ACTIVE';

create table document_chunk (
    id          uuid primary key,
    document_id uuid          not null references document (id),
    chunk_index int           not null,
    page_number int,
    section     varchar(300),
    content     text          not null,
    embedding   vector(384)   not null,
    suspicious  boolean       not null default false,
    content_tsv tsvector generated always as (
        to_tsvector('english', coalesce(section, '') || ' ' || content)
    ) stored,
    unique (document_id, chunk_index)
);

create index document_chunk_embedding on document_chunk using hnsw (embedding vector_cosine_ops);
create index document_chunk_tsv on document_chunk using gin (content_tsv);

create table ingestion_stage_log (
    id          bigserial primary key,
    document_id uuid         not null references document (id),
    stage       varchar(12)  not null,
    status      varchar(10)  not null,
    duration_ms bigint       not null,
    detail      varchar(500) not null,
    run_id      varchar(64),
    at          timestamptz  not null
);

create index ingestion_stage_log_document on ingestion_stage_log (document_id, at);

create table audit_event (
    id            uuid primary key,
    action        varchar(40)  not null,
    actor_id      uuid references app_user (id),
    resource_type varchar(40)  not null,
    resource_id   varchar(100) not null,
    detail        varchar(1000),
    run_id        varchar(64),
    at            timestamptz  not null
);

create index audit_event_run on audit_event (run_id);
create index audit_event_at on audit_event (at desc);
