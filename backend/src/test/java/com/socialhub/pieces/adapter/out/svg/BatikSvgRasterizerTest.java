package com.socialhub.pieces.adapter.out.svg;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class BatikSvgRasterizerTest {

    private final BatikSvgRasterizer rasterizer = new BatikSvgRasterizer();

    private static String svg(String body) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\">" + body + "</svg>";
    }

    @Test
    void aSafeSvgIsPaintedAtTheRequestedSizeEvenWhenItsRatioDiffers() {
        String svg = svg("<defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"#ff0000\"/><stop offset=\"1\" stop-color=\"#0000ff\"/>"
                + "</linearGradient></defs><rect width=\"100\" height=\"100\" fill=\"url(#g)\"/><circle cx=\"50\" cy=\"50\" r=\"10\" fill=\"#ffffff\"/>"
                + "<text x=\"10\" y=\"90\" font-size=\"8\">Hola</text>");
        assertThat(rasterizer.isSafe(svg)).isTrue();
        var image = rasterizer.rasterize(svg, 1080, 1350).orElseThrow();
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isEqualTo(1350);
        // Rojo a la izquierda, azul a la derecha, y el círculo blanco en el centro
        assertThat(new Color(image.getRGB(20, 100)).getRed()).isGreaterThan(new Color(image.getRGB(20, 100)).getBlue());
        assertThat(new Color(image.getRGB(1060, 100)).getBlue()).isGreaterThan(new Color(image.getRGB(1060, 100)).getRed());
        assertThat(new Color(image.getRGB(540, 675)).getGreen()).isGreaterThan(240);
    }

    @Test
    void withoutViewBoxItFillsTheTargetSize() {
        var image = rasterizer.rasterize("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1080\" height=\"1920\" fill=\"#00ff00\"/></svg>",
                1080, 1920).orElseThrow();
        assertThat(new Color(image.getRGB(540, 960)).getGreen()).isGreaterThan(200);
    }

    static Stream<String> malicious() {
        String ns = "xmlns=\"http://www.w3.org/2000/svg\"";
        return Stream.of(
                "<svg " + ns + "><script>alert(1)</script></svg>",
                "<svg " + ns + "><foreignObject width=\"10\" height=\"10\"><div>x</div></foreignObject></svg>",
                "<svg " + ns + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><image xlink:href=\"http://evil.test/a.png\" width=\"1\" height=\"1\"/></svg>",
                "<svg " + ns + " xmlns:xlink=\"http://www.w3.org/1999/xlink\"><use xlink:href=\"file:///etc/passwd\"/></svg>",
                "<svg " + ns + "><linearGradient id=\"g\" href=\"http://evil.test/x.svg#a\"/></svg>",
                "<svg " + ns + "><rect width=\"1\" height=\"1\" fill=\"url(http://evil.test/x.svg#a)\"/></svg>",
                "<svg " + ns + "><rect width=\"1\" height=\"1\" fill=\"url(file:///etc/passwd)\"/></svg>",
                "<svg " + ns + "><style>@import url(http://evil.test/a.css);</style></svg>",
                "<svg " + ns + "><rect style=\"fill:url(http://evil.test)\" width=\"1\" height=\"1\"/></svg>",
                "<svg " + ns + "><rect width=\"1\" height=\"1\" onload=\"alert(1)\"/></svg>",
                "<svg " + ns + " onload=\"alert(1)\"></svg>",
                "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg " + ns + "><text>&x;</text></svg>",
                "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://evil.test/svg.dtd\"><svg " + ns + "/>",
                "<svg " + ns + "><a href=\"javascript:alert(1)\"><rect width=\"1\" height=\"1\"/></a></svg>",
                "<svg " + ns + "><text>javascript:alert(1)</text></svg>",
                "<svg " + ns + "><animate attributeName=\"x\" from=\"0\" to=\"1\"/></svg>",
                "<svg " + ns + "><set attributeName=\"href\" to=\"http://evil.test\"/></svg>",
                "<svg " + ns + "><filter id=\"f\"><feImage href=\"http://evil.test/a.png\"/></filter></svg>",
                "<svg xmlns=\"http://www.w3.org/1999/xhtml\"><rect/></svg>",
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"/>",
                "<svg><rect width=\"1\" height=\"1\"/></svg>",
                "<svg " + ns + "><rect width=\"1\" height=\"1\"></svg>",
                "no es xml",
                "");
    }

    @ParameterizedTest
    @MethodSource("malicious")
    void anythingUnsafeOrBrokenIsRejectedWithoutPaintingOrFailing(String svg) {
        assertThat(rasterizer.isSafe(svg)).isFalse();
        assertThat(rasterizer.rasterize(svg, 100, 100)).isEmpty();
    }

    @Test
    void tooBigTooDeepOrTooManyElementsAreRejected() {
        assertThat(rasterizer.isSafe(svg("<text>" + "a".repeat(70_000) + "</text>"))).isFalse();
        assertThat(rasterizer.isSafe(svg("<g>".repeat(30) + "</g>".repeat(30)))).isFalse();
        assertThat(rasterizer.isSafe(svg("<rect width=\"1\" height=\"1\"/>".repeat(2100)))).isFalse();
        assertThat(rasterizer.isSafe(svg("<rect width=\"1\" height=\"1\"/>".repeat(100)))).isTrue();
        assertThat(rasterizer.rasterize(svg(""), 0, 100)).isEmpty();
        assertThat(rasterizer.rasterize(svg(""), 5000, 5000)).isEmpty();
    }

    @Test
    void internalReferencesAreAllowedAndExternalOnesAreNot() {
        assertThat(rasterizer.isSafe(svg("<defs><linearGradient id=\"a\"/><linearGradient id=\"b\" href=\"#a\"/></defs>"
                + "<rect width=\"1\" height=\"1\" fill=\"url(#b)\"/>"))).isTrue();
        assertThat(rasterizer.isSafe(svg("<linearGradient id=\"b\" href=\"a.svg#a\"/>"))).isFalse();
    }
}
