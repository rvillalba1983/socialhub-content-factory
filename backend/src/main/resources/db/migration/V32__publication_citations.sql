-- Citas (versos, letras…) que Claude declara en cada publicación: texto literal, documento y ubicación.
create table publication_citation (
    publication_id text not null references publication (id) on delete cascade,
    position       int  not null,
    text           text not null,
    source_file    text not null,
    location       text,
    primary key (publication_id, position)
);
