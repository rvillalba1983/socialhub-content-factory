-- Publer (Fase 12, bloque 2): qué cuenta de Publer usa cada marca en cada red. La escribe Raúl a mano (Publer no tiene API en el
-- plan gratuito: lo programa Claude con Chrome). Sin unique sobre el nombre: dos marcas pueden compartir una cuenta
-- (la de TikTok «Lito · Litoques · El DeLito» la usan las dos).
create table brand_publer_account (
    brand_id      text not null references brand (id) on delete cascade,
    platform      text not null,
    account_name  text not null,
    primary key (brand_id, platform)
);
