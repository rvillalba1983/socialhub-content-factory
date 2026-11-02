-- Archivos de cada publicación (Fase 4): los asocia el spec.json (campo «publicacion») o Raúl desde la app.
create table publication_file (
    publication_id  text not null references publication (id) on delete cascade,
    path            text not null,
    origin          text not null check (origin in ('SPEC', 'MANUAL')),
    added_at        timestamptz not null default now(),
    primary key (publication_id, path)
);
