-- «Margen mínimo de publicación en TikTok» (22 nov 2026, decisión de Raúl): ajuste aparte del general, en minutos
-- (por defecto 480 = 8 horas; mínimo 5; máximo 1440) en app_setting. TikTok se publica por Publer, que programa Claude con
-- Chrome en sus pasadas, y con el margen general una publicación de TikTok casi nunca llegaría a tiempo.
insert into app_setting (key, value) values ('publish.tiktok_min_margin_minutes', '480') on conflict (key) do nothing;

update station
   set description = 'Programa por la API oficial (con SocialHub abierto), Publer o Publora, respetando el margen mínimo de publicación (30 minutos por defecto; 8 horas en TikTok; ambos ajustables).'
 where id = 'prog';
