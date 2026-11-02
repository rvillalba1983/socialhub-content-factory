-- Memoria editorial (Fase 6, bloque 2)

-- Decisiones editoriales que escribe Raúl para cada marca
create table brand_decision (
    id          bigint generated always as identity primary key,
    brand_id    text not null references brand (id) on delete cascade,
    text        text not null,
    created_at  timestamptz not null default now()
);
create index brand_decision_brand_idx on brand_decision (brand_id, created_at);

-- Lo que Raúl rechaza (publicaciones canceladas, textos de Claude descartados) y por qué
create table editorial_rejection (
    id              bigint generated always as identity primary key,
    brand_id        text not null references brand (id) on delete cascade,
    promotion_id    text references promotion (id) on delete set null,
    publication_id  text references publication (id) on delete set null,
    kind            text not null check (kind in ('PUBLICATION', 'TEXT')),
    reason          text not null check (reason in ('TONE', 'REPEATED', 'OFF_BRAND', 'WRONG_DATA', 'FORMAT', 'OTHER')),
    note            text,
    title           text not null,
    content         text,
    created_at      timestamptz not null default now()
);
create index editorial_rejection_brand_idx on editorial_rejection (brand_id, created_at desc);

-- Correcciones de Raúl a los textos que propuso Claude
create table text_correction (
    id               bigint generated always as identity primary key,
    brand_id         text not null references brand (id) on delete cascade,
    promotion_id     text references promotion (id) on delete set null,
    publication_id   text references publication (id) on delete set null,
    before_caption   text,
    after_caption    text,
    before_hashtags  text,
    after_hashtags   text,
    created_at       timestamptz not null default now()
);
create index text_correction_brand_idx on text_correction (brand_id, created_at desc);

-- Última propuesta de Claude en cada publicación, para detectar las correcciones
alter table publication add column ai_caption text;
alter table publication add column ai_hashtags text;
