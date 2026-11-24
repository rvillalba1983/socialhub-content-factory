-- Kit de marca: piezas que puede llevar el kit de cada marca (decisiones 13, 50 y 51) y versión de fila para que dos
-- cambios a la vez sobre el mismo kit (por ejemplo, aprobar mientras llega un kit nuevo) no se pisen.
alter table brand add column kit_pieces text[] not null default '{cierre,historia}';
-- El DeLito: cita, cierre del carrusel e historia (en uso real el id es «el-delito»; en el modo demo, «delito»)
update brand set kit_pieces = '{cita,cierre,historia}' where id in ('delito', 'el-delito');
-- Litoques y las demás marcas: cierre del carrusel e historia (sin cita)
update brand set kit_pieces = '{cierre,historia}' where id <> all ('{delito,el-delito}');
alter table brand_kit add column row_version bigint not null default 0;
