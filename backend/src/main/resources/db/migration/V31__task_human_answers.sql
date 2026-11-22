-- Respuestas de Raúl a lo que Claude pregunta (se acumulan entre intentos) y opciones propuestas para la pregunta abierta.
alter table task add column question_options text[] not null default '{}';

create table task_human_answer (
    task_id     text        not null references task (id) on delete cascade,
    position    int         not null,
    question    text        not null,
    answer      text        not null,
    answered_at timestamptz not null,
    primary key (task_id, position)
);
