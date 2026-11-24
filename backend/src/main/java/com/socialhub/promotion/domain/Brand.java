package com.socialhub.promotion.domain;

import java.util.List;
import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record Brand(@Identity String id, String name, String handle, String color, boolean demo, List<String> kitPieces) {

    /** Piezas del kit de marca que lleva una marca nueva: lo común a todas (la cita es solo de El DeLito). */
    public static final List<String> DEFAULT_KIT_PIECES = List.of("cierre", "historia");

    public Brand {
        kitPieces = List.copyOf(kitPieces);
    }

    public Brand(String id, String name, String handle, String color, boolean demo) {
        this(id, name, handle, color, demo, DEFAULT_KIT_PIECES);
    }

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** El color es un #RRGGBB. Las marcas antiguas pueden no tenerlo (vacío): no es un error, solo se avisa. */
    public static boolean validColor(String color) {
        return color != null && HEX.matcher(color).matches();
    }

    public boolean hasColor() { return validColor(color); }
}
