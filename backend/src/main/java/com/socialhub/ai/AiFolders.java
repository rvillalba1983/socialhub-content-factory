package com.socialhub.ai;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Subcarpetas de intercambio dentro de la carpeta «Encargos» de cada marca. */
final class AiFolders {

    static final String TO_CLAUDE = "para-claude";
    static final String FROM_CLAUDE = "de-claude";
    /** Encargos que solo puede hacer Claude con Chrome en el PC de Raúl. */
    static final String TO_CLAUDE_PC = "para-claude-pc";

    private AiFolders() {}

    static boolean available(String ordersFolder) {
        try {
            return Files.isDirectory(Path.of(ordersFolder));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
