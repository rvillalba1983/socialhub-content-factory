-- Papeles de carpeta (Fase 4): qué carpeta de Drive hace cada papel.
-- Se fijan en la marca y cada promoción puede cambiar los que necesite.
create table brand_folder (
    brand_id  text not null references brand (id) on delete cascade,
    role      text not null check (role in ('MATERIAL', 'DRAFTS', 'GENERATED', 'APPROVED', 'PUBLISHED', 'REPORTS', 'ORDERS')),
    location  text not null,
    primary key (brand_id, role)
);

create table promotion_folder (
    promotion_id  text not null references promotion (id) on delete cascade,
    role          text not null check (role in ('MATERIAL', 'DRAFTS', 'GENERATED', 'APPROVED', 'PUBLISHED', 'REPORTS', 'ORDERS')),
    location      text not null,
    primary key (promotion_id, role)
);
