-- Control de calidad (Fase 6)
-- Avisos que Raúl acepta a mano: dejan de bloquear «Aprobada» y «Programada». Queda cuándo y por qué.
create table publication_quality_ack (
    publication_id  text not null references publication (id) on delete cascade,
    issue_key       text not null,
    reason          text not null,
    accepted_at     timestamptz not null default now(),
    primary key (publication_id, issue_key)
);

-- Avisos de la última revisión de calidad de Claude para cada publicación
create table publication_review_issue (
    publication_id  text not null references publication (id) on delete cascade,
    issue_key       text not null,
    message         text not null,
    task_id         text,
    created_at      timestamptz not null default now(),
    primary key (publication_id, issue_key)
);
