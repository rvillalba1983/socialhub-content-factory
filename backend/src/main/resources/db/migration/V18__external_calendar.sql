-- Calendario con las tres vías (Fase 7, bloque 4): lo programado o publicado fuera de SocialHub.
-- Publora se lee por su API cada hora; Publer y lo publicado en la red, con un encargo diario a Claude con Chrome.
create table external_post (
    id              bigint generated always as identity primary key,
    brand_id        text not null references brand (id) on delete cascade,
    source          text not null,          -- PUBLORA, PUBLER o NETWORK
    platform        text not null,          -- INSTAGRAM o TIKTOK
    at              timestamptz not null,   -- hora programada o de publicación
    status          text not null,          -- SCHEDULED, PUBLISHED o FAILED
    text            text,
    url             text,
    external_id     text,
    publication_id  text references publication (id) on delete set null,
    seen_at         timestamptz not null
);
create index external_post_brand_at_idx on external_post (brand_id, at);

-- Última lectura de cada fuente por marca
create table external_calendar_state (
    brand_id        text not null references brand (id) on delete cascade,
    source          text not null,
    requested_on    date,
    task_id         text,
    synced_at       timestamptz,
    error           text,
    primary key (brand_id, source)
);

insert into ai_setting (key, value) values ('model.CALENDAR_READ', 'SONNET') on conflict (key) do nothing;
