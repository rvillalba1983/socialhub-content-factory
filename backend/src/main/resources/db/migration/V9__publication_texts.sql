-- Texto y hashtags de cada publicación (Fase 5: los propone Claude y Raúl los revisa)
alter table publication add column caption text;
alter table publication add column hashtags text;
