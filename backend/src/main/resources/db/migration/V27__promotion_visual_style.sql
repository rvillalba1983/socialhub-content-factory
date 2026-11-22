-- Estilo visual de la promoción (texto libre corto, p. ej. «Navidad: rojo, dorado, nieve y estrellas»).
-- Claude lo usa para diseñar los fondos de las piezas sin material. Vacío si no se ha rellenado.
alter table promotion add column visual_style text not null default '' check (char_length(visual_style) <= 300);
