-- Revisión de calidad con Claude (Fase 6): Haiku por defecto
insert into ai_setting (key, value) values ('model.QUALITY_REVIEW', 'HAIKU') on conflict (key) do nothing;
