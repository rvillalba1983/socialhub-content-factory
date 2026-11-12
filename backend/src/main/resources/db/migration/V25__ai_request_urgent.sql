-- Ritmo de Claude (fase 12): cada encargo se marca urgente (o no) al enviarlo. Las pasadas de las 8:00 a las 18:00 solo
-- hacen los urgentes; SocialHub necesita saberlo para calcular cuándo debería haberse recogido.
alter table ai_request add column urgent boolean not null default false;
