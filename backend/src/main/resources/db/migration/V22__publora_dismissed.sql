-- «No uso Publora» (Fase 10): marcas para las que Raúl ha descartado la recomendación de conectar Publora.
-- Se puede deshacer: basta con borrar la fila. No toca las cuentas elegidas.
create table brand_publora_dismissed (
    brand_id  text primary key references brand (id) on delete cascade
);
