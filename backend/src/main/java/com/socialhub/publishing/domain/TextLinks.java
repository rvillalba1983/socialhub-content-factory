package com.socialhub.publishing.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Enlaces que lleva el texto de una publicación. */
public final class TextLinks {

    private static final Pattern URL = Pattern.compile("(?i)\\bhttps?://[^\\s<>\"'`]+");
    /** Máximo de enlaces que se miran por texto. */
    public static final int MAX_LINKS = 5;

    private TextLinks() {}

    /** Los enlaces http(s) del texto, sin repetir y en orden, sin la puntuación que los rodea. */
    public static List<String> find(String text) {
        List<String> links = new ArrayList<>();
        if (text == null) return links;
        Matcher m = URL.matcher(text);
        while (m.find()) {
            String url = trim(m.group());
            if (url.length() > 8 && !links.contains(url)) links.add(url);
        }
        return links;
    }

    private static String trim(String url) {
        String u = url;
        while (!u.isEmpty()) {
            char last = u.charAt(u.length() - 1);
            boolean closing = last == ')' && u.chars().filter(c -> c == '(').count() >= u.chars().filter(c -> c == ')').count();
            if (".,;:!?»”’)]}".indexOf(last) >= 0 && !closing) u = u.substring(0, u.length() - 1);
            else break;
        }
        return u;
    }

    public static boolean https(String url) { return url.regionMatches(true, 0, "https://", 0, 8); }
}
