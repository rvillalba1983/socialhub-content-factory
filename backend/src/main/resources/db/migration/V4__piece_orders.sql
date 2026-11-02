-- Encargos de piezas: qué spec.json se ha convertido ya en tarea (para no repetir los de la carpeta vigilada).
create table piece_order (
    id                bigint generated always as identity primary key,
    promotion_id      text not null references promotion (id) on delete cascade,
    spec_path         text not null,
    spec_modified_at  timestamptz not null,
    task_id           text references task (id) on delete set null,
    created_at        timestamptz not null default now(),
    unique (spec_path, spec_modified_at)
);
create index piece_order_promotion_idx on piece_order (promotion_id);
create index piece_order_task_idx on piece_order (task_id);
