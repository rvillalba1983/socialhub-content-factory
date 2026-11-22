-- «Margen mínimo de publicación» (16 nov 2026): sustituye al margen fijo de 6 horas. Ajuste global en minutos
-- (por defecto 30; mínimo 5; máximo 1440) en app_setting; lo lee el backend por una única vía.
insert into app_setting (key, value) values ('publish.min_margin_minutes', '30') on conflict (key) do nothing;

update station
   set description = 'Programa por la API oficial (con SocialHub abierto), Publer o Publora, respetando el margen mínimo de publicación (30 minutos por defecto, ajustable).'
 where id = 'prog';
