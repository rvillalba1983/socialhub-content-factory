-- Métricas (Fase 8, bloque 3): cada publicación se mide una vez, a los 7 días de publicarse.
-- Sin Metricool: las lee Claude con Chrome en el PC de Raúl. Lo que no se ve queda sin dato (null), nunca inventado.
create table publication_metric (
    publication_id  text primary key references publication (id) on delete cascade,
    status          text not null,          -- REQUESTED, DONE o FAILED
    task_id         text,
    attempts        integer not null default 1,
    requested_at    timestamptz not null,
    measured_at     timestamptz,
    views           bigint,
    reach           bigint,
    likes           bigint,
    comments        bigint,
    saves           bigint,
    shares          bigint,
    note            text
);

insert into ai_setting (key, value) values ('model.METRICS_READ', 'SONNET') on conflict (key) do nothing;
