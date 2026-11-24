-- Kit de marca (paleta y plantillas SVG) que diseña Claude y aprueba Raúl. Una sola versión aprobada por marca.
create table brand_kit (
    id              bigint generated always as identity primary key,
    brand_id        text        not null references brand (id) on delete cascade,
    version         int         not null,
    status          text        not null check (status in ('PENDING', 'APPROVED', 'SUPERSEDED', 'REJECTED')),
    kit_json        text        not null,
    comment         text,
    source_task_id  text        references task (id) on delete set null,
    created_at      timestamptz not null default now(),
    approved_at     timestamptz,
    unique (brand_id, version)
);
create index brand_kit_brand_idx on brand_kit (brand_id);
-- Como mucho un kit aprobado y uno pendiente por marca
create unique index brand_kit_one_approved on brand_kit (brand_id) where status = 'APPROVED';
create unique index brand_kit_one_pending on brand_kit (brand_id) where status = 'PENDING';
