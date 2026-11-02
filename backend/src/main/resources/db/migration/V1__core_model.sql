-- SocialHub · modelo inicial (Fase 2)
-- Convenciones:
--   * Los enumerados se guardan con su nombre (UPPER_SNAKE_CASE), igual que en la API.
--   * Las listas de textos simples van en columnas text[] (menos tablas y menos consultas).
--   * Los datos de demostración llevan is_demo = true.
--   * Toda clave foránea tiene índice (la PK lo cubre cuando la columna va primera).

-- ---------------------------------------------------------------- Marcas y promociones
create table brand (
    id          text primary key,
    name        text not null,
    handle      text not null,
    color       text not null,
    position    int  not null default 0,
    is_demo     boolean not null default false
);

create table promotion (
    id              text primary key,
    brand_id        text not null references brand (id) on delete cascade,
    name            text not null,
    description     text not null default '',
    scope           text not null default '',
    cadence         text not null default '',
    starts_on       date not null,
    ends_on         date not null,
    status          text not null default 'ACTIVE' check (status in ('PLANNED', 'ACTIVE', 'PAUSED', 'FINISHED')),
    metrics_source  text not null default '',
    goals           text[] not null default '{}',
    rules           text[] not null default '{}',
    human_todos     text[] not null default '{}',
    position        int  not null default 0,
    is_demo         boolean not null default false,
    check (ends_on >= starts_on)
);
create index promotion_brand_idx on promotion (brand_id);

create table promotion_milestone (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    starts_on    date not null,
    ends_on      date,
    label        text not null,
    primary key (promotion_id, position)
);

create table promotion_resource (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    kind         text not null check (kind in ('DRIVE', 'DOC')),
    location     text not null,
    description  text not null default '',
    primary key (promotion_id, position)
);

create table promotion_automation (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    name         text not null,
    schedule     text not null,
    model        text not null check (model in ('HAIKU', 'SONNET', 'OPUS')),
    runs_on      text not null check (runs_on in ('PC', 'CLOUD')),
    status       text not null,
    primary key (promotion_id, position)
);

create table promotion_route (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    name         text not null,
    status       text not null,
    label        text not null,
    notes        text not null default '',
    primary key (promotion_id, position)
);

create table promotion_setting (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    label        text not null,
    value        text not null,
    origin       text not null check (origin in ('BRAND', 'PROMOTION')),
    primary key (promotion_id, position)
);

-- Reglas comunes a todas las promociones
create table common_rule (
    position int primary key,
    text     text not null
);

-- ---------------------------------------------------------------- Producción
-- Estaciones del anillo. Su estado es independiente del de sus tareas.
create table station (
    id               text primary key,
    position         int  not null unique,
    name             text not null,
    description      text not null,
    status           text not null,
    previous_status  text,
    paused           boolean not null default false,
    note             text,
    limit_note       text not null default '',
    tools            text[] not null default '{}',
    version          bigint not null default 0
);

create table task (
    id            text primary key,
    station_id    text not null references station (id),
    promotion_id  text references promotion (id) on delete cascade,
    type          text not null default 'GENERIC',
    title         text not null,
    goal          text not null default '',
    why           text,
    inputs        text[] not null default '{}',
    status        text not null check (status in ('IDLE', 'QUEUED', 'RUNNING', 'WAITING', 'BLOCKED', 'FAILED', 'COMPLETED', 'CANCELLED')),
    paused        boolean not null default false,
    progress      double precision not null default 0 check (progress between 0 and 100),
    agent_kind    text not null check (agent_kind in ('AI', 'LOCAL', 'HUMAN', 'BROWSER', 'CONNECTOR')),
    agent_name    text not null,
    reason        text,
    error         text,
    result        text,
    human         boolean not null default false,
    attempts      int not null default 1 check (attempts >= 1),
    max_attempts  int not null default 2 check (max_attempts >= 1),
    started_at    timestamptz,
    updated_at    timestamptz not null default now(),
    version       bigint not null default 0,
    is_demo       boolean not null default false,
    check (not paused or status = 'WAITING')
);
create index task_station_idx on task (station_id);
create index task_promotion_idx on task (promotion_id);
-- El motor recorre a menudo lo que está en cola o en curso: índice parcial pequeño
create index task_active_idx on task (status) where status in ('QUEUED', 'RUNNING', 'BLOCKED');

create table task_step (
    task_id  text not null references task (id) on delete cascade,
    position int  not null,
    label    text not null,
    state    text not null check (state in ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED')),
    primary key (task_id, position)
);

create table task_check (
    task_id  text not null references task (id) on delete cascade,
    position int  not null,
    label    text not null,
    passed   boolean not null default false,
    primary key (task_id, position)
);

create table task_dependency (
    task_id     text not null references task (id) on delete cascade,
    depends_on  text not null references task (id) on delete cascade,
    primary key (task_id, depends_on),
    check (task_id <> depends_on)
);
create index task_dependency_depends_on_idx on task_dependency (depends_on);

-- Historial visible de cada tarea (solo se añade)
create table task_event (
    id       bigint generated always as identity primary key,
    task_id  text not null references task (id) on delete cascade,
    at       timestamptz not null default now(),
    message  text not null
);
create index task_event_task_idx on task_event (task_id, at);

-- ---------------------------------------------------------------- Publicaciones
create table publication (
    id             text primary key,
    promotion_id   text not null references promotion (id) on delete cascade,
    platform       text not null check (platform in ('INSTAGRAM', 'TIKTOK')),
    type           text not null,
    title          text not null,
    scheduled_on   date not null,
    scheduled_time time not null,
    timezone       text not null default 'Europe/Madrid',
    status         text not null check (status in ('IDEA', 'PROPOSAL', 'IN_PRODUCTION', 'DRAFT', 'IN_REVIEW', 'APPROVED',
                                                    'SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'PUBLISH_ERROR', 'CANCELLED')),
    route          text not null default 'Publer',
    flag           text,
    note           text,
    external_id    text,
    confirmation   text,
    published_at   timestamptz,
    version        bigint not null default 0,
    is_demo        boolean not null default false,
    -- Nunca «publicada» sin confirmación de la plataforma o de la vista de Publer
    check (status <> 'PUBLISHED' or confirmation is not null)
);
-- Calendario y filtros por estado dentro de una promoción
create index publication_calendar_idx on publication (promotion_id, scheduled_on, scheduled_time);
create index publication_status_idx on publication (promotion_id, status);

-- ---------------------------------------------------------------- Armería
create table armory_item (
    id            text primary key,
    kind          text not null check (kind in ('AGENT', 'TOOL', 'SERVICE', 'DEVICE')),
    name          text not null,
    description   text not null,
    status        text not null,
    note          text not null default '',
    capabilities  text[] not null default '{}',
    position      int not null,
    is_demo       boolean not null default false
);

-- ---------------------------------------------------------------- Operaciones externas
-- Cada llamada a un sistema externo (PostgreSQL, Drive, Publer, APIs…) con su resultado.
create table external_operation (
    id             bigint generated always as identity primary key,
    system         text not null,
    operation      text not null,
    outcome        text not null check (outcome in ('SUCCESS', 'FAILURE')),
    started_at     timestamptz not null,
    duration_ms    bigint not null,
    trace_id       text,
    error_type     text,
    error_message  text,
    detail         text
);
create index external_operation_started_idx on external_operation (started_at desc);
create index external_operation_system_idx on external_operation (system, started_at desc);
-- Los fallos son lo que más se consulta: índice parcial
create index external_operation_failures_idx on external_operation (started_at desc) where outcome = 'FAILURE';

-- ---------------------------------------------------------------- Auditoría (Hibernate Envers)
create sequence revinfo_seq start with 1 increment by 50;

create table revinfo (
    rev       int primary key,
    revtstmp  bigint
);

create table task_aud (
    id          text not null,
    rev         int not null references revinfo (rev),
    revtype     smallint,
    status      text,
    paused      boolean,
    agent_kind  text,
    agent_name  text,
    reason      text,
    error       text,
    result      text,
    human       boolean,
    attempts    int,
    primary key (id, rev)
);
create index task_aud_rev_idx on task_aud (rev);

create table station_aud (
    id       text not null,
    rev      int not null references revinfo (rev),
    revtype  smallint,
    status   text,
    paused   boolean,
    note     text,
    primary key (id, rev)
);
create index station_aud_rev_idx on station_aud (rev);

create table publication_aud (
    id            text not null,
    rev           int not null references revinfo (rev),
    revtype       smallint,
    status        text,
    flag          text,
    external_id   text,
    confirmation  text,
    published_at  timestamptz,
    primary key (id, rev)
);
create index publication_aud_rev_idx on publication_aud (rev);
