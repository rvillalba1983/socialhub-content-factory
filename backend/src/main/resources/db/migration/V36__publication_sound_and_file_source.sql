-- Fase 13, bloque 3.
-- Sonido de la publicación (decisión 23): sin sonido, tema de Litoques en el vídeo o tema que se elige en la red.
-- Por ahora solo se guarda y se enseña; el montaje del audio es del bloque 5.
alter table publication add column sound_mode text not null default 'NONE';
alter table publication add column sound_track text;
alter table publication add constraint publication_sound_mode_check check (sound_mode in ('NONE', 'LITOQUES_TRACK', 'NETWORK'));
-- Un tema con nombre salvo en «sin sonido»
alter table publication add constraint publication_sound_track_check
    check ((sound_mode = 'NONE' and sound_track is null) or (sound_mode <> 'NONE' and sound_track is not null and length(btrim(sound_track)) > 0));

-- Hallazgo K: página del PDF y nombre del archivo de imagen de cada pieza o diapositiva generada
alter table publication_file add column page integer;
alter table publication_file add column source_name text;
alter table publication_file add constraint publication_file_page_check check (page is null or page >= 1);
