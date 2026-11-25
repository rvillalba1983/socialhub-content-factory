package com.socialhub.publishing.domain;

/**
 * Lo que se ve dentro de un archivo de vídeo, para las comprobaciones de calidad.
 *
 * @param readable si se pudo leer el archivo
 * @param hasVideo si tiene pista de vídeo
 */
public record VideoInfo(boolean readable, boolean hasVideo, int width, int height) {}
