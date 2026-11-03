-- Publicación (Fase 7): una tarea de publicación por publicación programada.
-- Prueba las vías por orden (Publer, Publora, API oficial), reintenta mientras quede el margen de 6 h y, si no,
-- deja un LEEME para publicar a mano. Pasada la hora, encarga la comprobación.
create table publish_job (
    publication_id  text primary key references publication (id) on delete cascade,
    status          text not null,
    route_index     integer not null default 0,
    route           text,
    task_id         text,
    late_task_ids   text not null default '',
    external_id     text,
    url             text,
    next_try_at     timestamptz,
    attempts        integer not null default 1,
    last_error      text,
    manual_file     text,
    log             text not null default '',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint not null default 0
);
create index publish_job_status_idx on publish_job (status);

-- Los encargos con Chrome en el PC: Sonnet por defecto
insert into ai_setting (key, value) values ('model.PUBLER_SCHEDULE', 'SONNET') on conflict (key) do nothing;
insert into ai_setting (key, value) values ('model.PUBLISH_CHECK', 'SONNET') on conflict (key) do nothing;
