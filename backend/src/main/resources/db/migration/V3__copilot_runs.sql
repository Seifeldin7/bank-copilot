create table copilot_run (
    run_id             varchar(64)      primary key,
    user_id            uuid             not null references app_user (id),
    question           varchar(1000)    not null,
    mode               varchar(12)      not null check (mode in ('ORCHESTRATED', 'FALLBACK')),
    refused            boolean          not null,
    refusal_reason     varchar(500),
    answer             text,
    citations          jsonb            not null,
    verdict            jsonb,
    suggested_category varchar(30),
    prompt_tokens      int              not null,
    completion_tokens  int              not null,
    cost_usd           double precision not null,
    latency_ms         bigint           not null,
    created_at         timestamptz      not null
);

create index copilot_run_created on copilot_run (created_at desc);

create table agent_step (
    id                uuid primary key,
    run_id            varchar(64)      not null references copilot_run (run_id),
    step_no           int              not null,
    agent             varchar(30)      not null,
    status            varchar(12)      not null,
    summary           varchar(500)     not null,
    iterations        int              not null,
    duration_ms       bigint           not null,
    prompt_tokens     int              not null,
    completion_tokens int              not null,
    cost_usd          double precision not null,
    started_at        timestamptz      not null,
    unique (run_id, step_no)
);

create table ai_usage (
    id                bigserial primary key,
    run_id            varchar(64)      not null references copilot_run (run_id),
    step_no           int              not null,
    agent             varchar(30)      not null,
    provider          varchar(30)      not null,
    model             varchar(100)     not null,
    prompt_tokens     int              not null,
    completion_tokens int              not null,
    cost_usd          double precision not null,
    latency_ms        bigint           not null,
    prompt            text,
    response          text,
    at                timestamptz      not null
);

create index ai_usage_run on ai_usage (run_id);
create index ai_usage_at on ai_usage (at);

create unique index service_request_source_run on service_request (source_run_id) where source_run_id is not null;
