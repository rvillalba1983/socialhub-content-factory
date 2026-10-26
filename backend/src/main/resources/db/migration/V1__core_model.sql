-- SocialHub · modelo inicial (Fase 2)
-- Los datos de demostración llevan is_demo = true.

create table brand (
    id          text primary key,
    name        text not null,
    handle      text not null,
    color       text not null,
    position    int  not null default 0,
    is_demo     boolean not null default false,
    created_at  timestamptz not null default now()
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
    status          text not null default 'active' check (status in ('planned', 'active', 'paused', 'finished')),
    metrics_source  text not null default '',
    position        int  not null default 0,
    is_demo         boolean not null default false,
    check (ends_on >= starts_on)
);
create index promotion_brand_idx on promotion (brand_id);

-- Listas ordenadas de cada promoción
create table promotion_goal       (promotion_id text not null references promotion (id) on delete cascade, position int not null, text text not null, primary key (promotion_id, position));
create table promotion_rule       (promotion_id text not null references promotion (id) on delete cascade, position int not null, text text not null, primary key (promotion_id, position));
create table promotion_human_todo (promotion_id text not null references promotion (id) on delete cascade, position int not null, text text not null, primary key (promotion_id, position));

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
    kind         text not null check (kind in ('drive', 'doc')),
    location     text not null,
    description  text not null default '',
    primary key (promotion_id, position)
);

create table promotion_automation (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    name         text not null,
    schedule     text not null,
    model        text not null,
    runs_on      text not null check (runs_on in ('pc', 'cloud')),
    status       text not null check (status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    primary key (promotion_id, position)
);

create table promotion_route (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    name         text not null,
    status       text not null check (status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    label        text not null,
    notes        text not null default '',
    primary key (promotion_id, position)
);

create table promotion_setting (
    promotion_id text not null references promotion (id) on delete cascade,
    position     int  not null,
    label        text not null,
    value        text not null,
    origin       text not null check (origin in ('brand', 'promotion')),
    primary key (promotion_id, position)
);

-- Reglas comunes a todas las promociones
create table app_rule (
    position int primary key,
    text     text not null
);

-- Estaciones del anillo. Su estado es independiente del de sus tareas.
create table station (
    id               text primary key,
    position         int  not null unique,
    name             text not null,
    description      text not null,
    status           text not null check (status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    previous_status  text check (previous_status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    paused           boolean not null default false,
    note             text,
    limit_note       text not null default '',
    tools            text[] not null default '{}',
    version          int not null default 0
);

create table task (
    id            text primary key,
    station_id    text not null references station (id),
    promotion_id  text references promotion (id) on delete cascade,
    type          text not null default 'generic',
    title         text not null,
    goal          text not null default '',
    status        text not null check (status in ('idle', 'queued', 'run', 'wait', 'block', 'err', 'ok', 'cancel')),
    paused        boolean not null default false,
    progress      numeric(5, 2) not null default 0 check (progress between 0 and 100),
    agent_kind    text not null check (agent_kind in ('ai', 'local', 'human', 'browser', 'conn')),
    agent_name    text not null,
    why           text,
    reason        text,
    error         text,
    result        text,
    human         boolean not null default false,
    attempts      int not null default 1 check (attempts >= 1),
    max_attempts  int not null default 2 check (max_attempts >= 1),
    started_at    timestamptz,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    version       int not null default 0,
    is_demo       boolean not null default false,
    check (not paused or status = 'wait')
);
create index task_station_idx on task (station_id);
create index task_promotion_idx on task (promotion_id);
create index task_status_idx on task (status);

create table task_input (task_id text not null references task (id) on delete cascade, position int not null, text text not null, primary key (task_id, position));

create table task_step (
    task_id  text not null references task (id) on delete cascade,
    position int  not null,
    label    text not null,
    state    numeric(3, 2) not null default 0 check (state between -1 and 1),
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

create table task_event (
    id       bigserial primary key,
    task_id  text not null references task (id) on delete cascade,
    at       timestamptz not null default now(),
    message  text not null
);
create index task_event_task_idx on task_event (task_id, at);

create table publication (
    id             text primary key,
    promotion_id   text not null references promotion (id) on delete cascade,
    platform       text not null default 'instagram',
    type           text not null,
    title          text not null,
    scheduled_on   date not null,
    scheduled_time time not null,
    timezone       text not null default 'Europe/Madrid',
    status         text not null check (status in ('idea', 'proposal', 'production', 'draft', 'review', 'approved',
                                                    'scheduled', 'publishing', 'published', 'error', 'cancelled')),
    route          text not null default 'Publer',
    flag           text,
    note           text,
    external_id    text,
    confirmation   text,
    published_at   timestamptz,
    version        int not null default 0,
    is_demo        boolean not null default false,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    -- Nunca «publicada» sin confirmación de la plataforma o de la vista de Publer
    check (status <> 'published' or confirmation is not null)
);
create index publication_promotion_idx on publication (promotion_id, scheduled_on, scheduled_time);

create table publication_attempt (
    id               bigserial primary key,
    publication_id   text not null references publication (id) on delete cascade,
    at               timestamptz not null default now(),
    route            text not null,
    outcome          text not null check (outcome in ('scheduled', 'published', 'failed')),
    detail           text,
    idempotency_key  uuid not null unique
);

-- Armería: agentes, herramientas locales y servicios
create table ai_provider (
    id    text primary key,
    name  text not null
);

create table ai_model (
    id           text primary key,
    provider_id  text not null references ai_provider (id) on delete cascade,
    name         text not null
);

create table ai_agent (
    id           text primary key,
    provider_id  text references ai_provider (id),
    name         text not null,
    description  text not null,
    status       text not null check (status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    note         text not null default '',
    position     int not null,
    is_demo      boolean not null default false
);

create table local_tool (
    id           text primary key,
    name         text not null,
    description  text not null,
    status       text not null check (status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    note         text not null default '',
    position     int not null,
    is_demo      boolean not null default false
);

create table local_tool_capability (
    tool_id     text not null references local_tool (id) on delete cascade,
    capability  text not null,
    primary key (tool_id, capability)
);

create table external_service (
    id           text primary key,
    name         text not null,
    description  text not null,
    kind         text not null check (kind in ('service', 'device')),
    status       text not null check (status in ('ok', 'run', 'wait', 'block', 'err', 'idle', 'cancel')),
    note         text not null default '',
    position     int not null,
    is_demo      boolean not null default false
);

-- Auditoría de negocio
create table audit_event (
    id           bigserial primary key,
    at           timestamptz not null default now(),
    actor        text not null,
    action       text not null,
    target_type  text not null,
    target_id    text,
    details      text
);
create index audit_event_at_idx on audit_event (at desc);

-- Acceso local
create table app_credential (
    id             int primary key check (id = 1),
    password_hash  text not null,
    created_at     timestamptz not null default now()
);

create table app_session (
    token_hash    text primary key,
    created_at    timestamptz not null default now(),
    expires_at    timestamptz not null,
    last_seen_at  timestamptz not null default now()
);
create index app_session_expires_idx on app_session (expires_at);
