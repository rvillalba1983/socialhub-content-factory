package com.socialhub.promotion.domain;

import java.util.regex.Pattern;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record Brand(@Identity String id, String name, String handle, String color, boolean demo) {

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /** El color es un #RRGGBB. Las marcas antiguas pueden no tenerlo (vacío): no es un error, solo se avisa. */
    public static boolean validColor(String color) {
        return color != null && HEX.matcher(color).matches();
    }

    public boolean hasColor() { return validColor(color); }
}
