-- Autonomía (Fase 8, bloque 2): encargos que SocialHub pide solo (textos y revisión de calidad).
-- Se apunta cada uno para no repetirlo: por publicación, tipo y versión del texto.
create table auto_ai_request (
    publication_id  text not null references publication (id) on delete cascade,
    kind            text not null,           -- CAPTIONS o QUALITY_REVIEW
    fingerprint     text not null,           -- versión del texto revisado ('' para los textos)
    task_id         text not null,
    created_at      timestamptz not null,
    primary key (publication_id, kind, fingerprint)
);
