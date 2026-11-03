-- Publora (Fase 7, bloque 2): qué cuenta conectada en Publora usa cada marca en cada red. Lo elige Raúl.
-- La clave de la API no va aquí: está en un archivo de la carpeta de datos, legible solo por su usuario.
create table brand_publora_account (
    brand_id       text not null references brand (id) on delete cascade,
    platform       text not null,
    connection_id  text not null,
    label          text not null,
    primary key (brand_id, platform)
);
