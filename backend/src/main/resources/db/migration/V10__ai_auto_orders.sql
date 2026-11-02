-- Encargos que SocialHub crea solo cada semana (spec del domingo, balance del lunes), para no repetirlos
create table ai_auto_order (
    promotion_id  text not null references promotion (id) on delete cascade,
    type          text not null,
    week_start    date not null,
    task_id       text references task (id) on delete set null,
    created_at    timestamptz not null default now(),
    primary key (promotion_id, type, week_start)
);
