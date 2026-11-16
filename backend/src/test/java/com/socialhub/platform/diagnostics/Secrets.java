package com.socialhub.platform.diagnostics;

import java.util.List;

/** Textos con secretos (inventados) que no pueden aparecer en ningún registro, y textos normales que sí. */
final class Secrets {

    /** Cada texto lleva el secreto que debe desaparecer. */
    record Sample(String text, String secret) {}

    static final List<Sample> SAMPLES = List.of(
            new Sample("Authorization: Bearer abc123.def456.ghi789xyz", "abc123.def456.ghi789xyz"),
            new Sample("llamada con token=sk-AAAAAAAAAAAAAAAAAAAAAAAA fallida", "sk-AAAAAAAAAAAAAAAAAAAAAAAA"),
            new Sample("GET https://api.example.com/v1/files?key=AIzaSyD1234567890abcdefghijklmnopqrstu&alt=json", "AIzaSyD1234567890abcdefghijklmnopqrstu"),
            new Sample("{\"access_token\":\"ya29.a0AfH6SMBxxxxxxxxxxxxxxxx\",\"expires_in\":3599}", "ya29.a0AfH6SMBxxxxxxxxxxxxxxxx"),
            new Sample("password=hunter2 usuario=raul", "hunter2"),
            new Sample("client_secret: GOCSPX-abcdefghijkl", "GOCSPX-abcdefghijkl"),
            new Sample("refresh_token=1//0gAbCdEfGhIjKlMnOpQrStUvWx", "1//0gAbCdEfGhIjKlMnOpQrStUvWx"),
            new Sample("clave de GitHub ghp_abcdefghijklmnopqrstuvwxyz0123456789 en uso", "ghp_abcdefghijklmnopqrstuvwxyz0123456789"),
            new Sample("jwt eyJhbGciOiJIUzI1.eyJzdWIiOiIxMjM0.SflKxwRJSMeKKF2QT4fw", "SflKxwRJSMeKKF2QT4fw"),
            new Sample("Meta EAABwzLixnjYBO1234567890abcdefghijklmnopqrstu", "EAABwzLixnjYBO1234567890abcdefghijklmnopqrstu"),
            new Sample("X-Api-Key: 9f8e7d6c5b4a39281706", "9f8e7d6c5b4a39281706"),
            new Sample("callback ?code=4/0AbCdEfGh&state=zz", "4/0AbCdEfGh"));

    /** Textos corrientes que tienen que quedar tal cual. */
    static final List<String> HARMLESS = List.of(
            "input_tokens=1200 output_tokens=340",
            "PostgreSQL incluido en marcha en 127.0.0.1:54329",
            "Error en la API: GET /api/publications/p-1 -> java.lang.IllegalStateException: La pieza no existe",
            "Operación postgres/migrate terminada en 120 ms");

    private Secrets() {}
}
