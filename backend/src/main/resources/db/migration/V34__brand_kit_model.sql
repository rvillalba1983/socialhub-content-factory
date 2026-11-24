-- Kit de marca: diseño que se pide una vez por marca (paleta y plantillas SVG); por defecto con el modelo más capaz
insert into ai_setting (key, value) values ('model.BRAND_KIT', 'OPUS') on conflict (key) do nothing;
