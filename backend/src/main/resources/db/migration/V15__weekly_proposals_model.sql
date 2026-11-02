-- Propuestas de la semana (Fase 6, bloque 3): Sonnet por defecto
insert into ai_setting (key, value) values ('model.WEEKLY_PROPOSALS', 'SONNET') on conflict (key) do nothing;
