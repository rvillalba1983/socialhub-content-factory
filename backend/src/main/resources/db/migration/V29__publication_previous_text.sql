-- Texto de Raúl sustituido por un texto que él mismo pidió a Claude: queda guardado para recuperarlo
alter table publication add column previous_caption text;
alter table publication add column previous_hashtags text;
