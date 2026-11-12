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
-- Cuándo se comprobó todo (clave, cuenta, archivos y enlaces) en el corte previo, cuando Publer aún llega.
alter table publish_job add column preflight_at timestamptz;

-- Cambia el orden de las vías: antes Publer, Publora y API oficial (índices 0, 1, 2); ahora API oficial, Publer y
-- Publora. Los trabajos en curso conservan la vía que tenían: se traduce su índice por el nombre de su vía.
update publish_job set route_index = case route
        when 'API oficial' then 0
        when 'Publer' then 1
        when 'Publora' then 2
        else route_index + 1 end
    where status in ('WORKING', 'SCHEDULED', 'MANUAL', 'CHECKING');

insert into ai_setting (key, value) values ('model.PUBLIC_LINK', 'HAIKU') on conflict (key) do nothing;
