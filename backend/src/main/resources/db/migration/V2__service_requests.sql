create sequence service_request_number start with 100;

create table service_request (
    id               uuid primary key,
    reference        varchar(20)   not null unique,
    category         varchar(30)   not null check (category in ('CARD_SERVICE', 'DIGITAL_BANKING', 'ACCOUNT_SERVICE', 'ADDITIONAL_DOCUMENTATION')),
    status           varchar(20)   not null check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED')),
    title            varchar(120)  not null,
    description      varchar(1000) not null,
    payload          jsonb         not null,
    validation       jsonb,
    requester_id     uuid          not null references app_user (id),
    source_run_id    varchar(64),
    decision         varchar(20) check (decision in ('APPROVED', 'REJECTED', 'EDITED_APPROVED')),
    decided_by       uuid references app_user (id),
    decision_reason  varchar(1000),
    decision_changes jsonb,
    decided_at       timestamptz,
    version          int           not null,
    created_at       timestamptz   not null,
    updated_at       timestamptz   not null,
    submitted_at     timestamptz,
    executed_at      timestamptz,
    check (decided_by is null or decided_by <> requester_id)
);

create index service_request_requester on service_request (requester_id, updated_at desc);
create index service_request_queue on service_request (status, submitted_at);

create table request_history (
    id          uuid primary key,
    request_id  uuid          not null references service_request (id),
    action      varchar(30)   not null,
    actor_id    uuid          not null references app_user (id),
    from_status varchar(20),
    to_status   varchar(20),
    changes     jsonb         not null,
    reason      varchar(1000),
    run_id      varchar(64),
    at          timestamptz   not null
);

create index request_history_request on request_history (request_id, at);

create table execution_record (
    id               uuid primary key,
    reference        varchar(20)  not null unique,
    request_id       uuid         not null unique references service_request (id),
    outcome          varchar(10)  not null check (outcome in ('SUCCEEDED', 'FAILED')),
    simulated_action varchar(200) not null,
    result           jsonb        not null,
    executed_by      uuid         not null references app_user (id),
    executed_at      timestamptz  not null
);
