-- API oficial de Instagram (Fase 12): cuenta de cada marca, imágenes públicas por publicación y marca de «publicando».
-- La clave de usuario del sistema no va aquí: está en un archivo de la carpeta de datos, legible solo por su usuario.
create table brand_instagram_account (
    brand_id     text primary key references brand (id) on delete cascade,
    ig_user_id   text not null,
    username     text not null
);

-- Imágenes que SocialHub deja en la carpeta pública de Drive para que Meta las descargue, y el enlace que Claude
-- (en la nube) encuentra para cada una. Se borran al publicar o descartar.
create table publication_public_media (
    publication_id  text not null references publication (id) on delete cascade,
    position        integer not null,
    source_path     text not null,
    file_name       text not null unique,
    url             text,
    primary key (publication_id, position)
);

-- Cuándo empezó SocialHub a publicar por la API oficial. Si hay marca pero no resultado (cierre, corte), no se sabe si
-- Meta llegó a publicar: se revisa a mano, nunca se reintenta.
alter table publish_job add column publish_started_at timestamptz;

insert into ai_setting (key, value) values ('model.PUBLIC_LINK', 'HAIKU') on conflict (key) do nothing;
