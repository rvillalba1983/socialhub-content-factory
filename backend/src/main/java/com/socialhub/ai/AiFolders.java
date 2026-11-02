package com.socialhub.ai;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Subcarpetas de intercambio dentro de la carpeta «Encargos» de cada marca. */
final class AiFolders {

    static final String TO_CLAUDE = "para-claude";
    static final String FROM_CLAUDE = "de-claude";

    private AiFolders() {}

    static boolean available(String ordersFolder) {
        try {
            return Files.isDirectory(Path.of(ordersFolder));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
