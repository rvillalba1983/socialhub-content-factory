-- Tareas bloqueadas porque falta una carpeta (por ejemplo, G: sin montar). Vuelven a la cola solas cuando aparece.
alter table task add column waiting_for text;
create index task_waiting_for_idx on task (status) where waiting_for is not null;
