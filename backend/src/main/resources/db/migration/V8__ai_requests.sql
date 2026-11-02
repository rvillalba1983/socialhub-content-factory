-- Orquestador de IA (Fase 5): encargos a Claude por la carpeta de Drive de cada marca.

-- Cada envío de un encargo (un intento de una tarea) y su resultado
create table ai_request (
    id              text primary key,                      -- idempotencyKey del contrato
    task_id         text not null references task (id) on delete cascade,
    brand_id        text not null,
    promotion_id    text,
    type            text not null,
    model           text not null check (model in ('HAIKU', 'SONNET', 'OPUS')),
    attempt         int  not null check (attempt >= 1),
    status          text not null check (status in ('SENT', 'COMPLETED', 'FAILED', 'NEEDS_HUMAN', 'INVALID')),
    request_path    text not null,
    result_path     text,
    sent_at         timestamptz not null,
    finished_at     timestamptz,
    duration_ms     bigint,
    runtime         text,                                  -- agente que lo hizo, según el resultado
    result_model    text,                                  -- modelo que dice haber usado
    error           text
);
create index ai_request_task_idx on ai_request (task_id);
create index ai_request_sent_idx on ai_request (sent_at desc);

-- Resultados ya leídos de las carpetas «de-claude» (ruta y fecha de modificación), para no procesarlos dos veces
create table ai_result_file (
    id           bigint generated always as identity primary key,
    path         text not null,
    modified_at  timestamptz not null,
    request_id   text references ai_request (id) on delete set null,
    outcome      text not null check (outcome in ('APPLIED', 'INVALID', 'IGNORED')),
    note         text,
    read_at      timestamptz not null default now(),
    unique (path, modified_at)
);

-- Ajustes: límite diario y modelo de cada tipo de encargo
create table ai_setting (
    key    text primary key,
    value  text not null
);
insert into ai_setting (key, value) values
    ('daily_limit', '10'),
    ('model.WEEKLY_SPEC', 'SONNET'),
    ('model.CAPTIONS', 'SONNET'),
    ('model.WEEKLY_BALANCE', 'OPUS'),
    ('model.RESEARCH', 'SONNET');
