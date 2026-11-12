-- Encargos a Claude pedidos por Raúl desde la interfaz (o reintentados por él): salen siempre como urgentes.
alter table task add column manual boolean not null default false;
