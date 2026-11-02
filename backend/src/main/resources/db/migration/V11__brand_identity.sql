-- Identidad de cada marca (Fase 6): la heredan sus promociones, va en los encargos a Claude y la usa el control de calidad
create table brand_identity (
    brand_id                text primary key references brand (id) on delete cascade,
    tone                    text not null default '',
    audience                text not null default '',
    forbidden_topics        text[] not null default '{}',
    preferred_expressions   text[] not null default '{}',
    forbidden_expressions   text[] not null default '{}',
    colors                  text[] not null default '{}',
    font                    text not null default '',
    logo                    text not null default '',
    visual_style            text not null default '',
    photo_style             text not null default '',
    call_to_action          text not null default '',
    fixed_hashtags          text not null default ''
);
