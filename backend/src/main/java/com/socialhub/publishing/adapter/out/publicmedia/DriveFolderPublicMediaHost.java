package com.socialhub.publishing.adapter.out.publicmedia;

import com.socialhub.publishing.domain.PublicMediaHost;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Espacio público en una carpeta «publico» de Drive: Raúl la comparte a mano («cualquier persona con el enlace») y los
 * archivos de dentro heredan el permiso. SocialHub copia ahí cada JPEG con un nombre aleatorio; Claude (en la nube) busca
 * el archivo con su conector de Drive y devuelve su enlace de descarga; SocialHub lo borra al terminar.
 * <p>
 * La carpeta es {@code «publico»} dentro de la carpeta «Encargos» de la marca, salvo que se indique otra con
 * {@code socialhub.publishing.public-folder}.
 */
@Slf4j
@Component
class DriveFolderPublicMediaHost implements PublicMediaHost {

    static final String FOLDER = "publico";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String fixedFolder;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NORMAL).build();

    DriveFolderPublicMediaHost(@Value("${socialhub.publishing.public-folder:}") String fixedFolder) {
        this.fixedFolder = fixedFolder.strip();
    }

    @Override
    public boolean available(String ordersFolder) { return folder(ordersFolder).map(Files::isDirectory).orElse(false); }

    @Override
    public Optional<String> stage(String ordersFolder, Path image) {
        Path dir = folder(ordersFolder).filter(Files::isDirectory).orElse(null);
        if (dir == null) return Optional.empty();
        String name = UUID.randomUUID().toString().replace("-", "").substring(0, 20) + ".jpg";
        Path target = dir.resolve(name);
        String file = image.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (file.endsWith(".jpg") || file.endsWith(".jpeg")) {
                Files.copy(image, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                BufferedImage source = ImageIO.read(image.toFile());
                if (source == null) {
                    throw new IllegalArgumentException("No se puede convertir a JPEG " + image.getFileName() + " (usa JPEG o PNG)");
                }
                // El JPEG no tiene transparencia: se pinta sobre blanco
                BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                g.drawImage(source, 0, 0, null);
                g.dispose();
                if (!ImageIO.write(rgb, "jpg", target.toFile())) {
                    throw new IllegalArgumentException("No se puede convertir a JPEG " + image.getFileName());
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo dejar " + image.getFileName() + " en la carpeta pública: " + e.getMessage());
        }
        return Optional.of(name);
    }

    @Override
    public void release(String ordersFolder, Collection<String> names) {
        Path dir = folder(ordersFolder).orElse(null);
        if (dir == null) return;
        for (String name : names) {
            // Solo nombres que hemos generado: nunca una ruta
            if (!name.matches("[0-9a-f]{20}\\.jpg")) continue;
            try {
                Files.deleteIfExists(dir.resolve(name));
            } catch (IOException e) {
                log.warn("No se pudo borrar {} de la carpeta pública: {}", name, e.getMessage());
            }
        }
    }

    @Override
    public Optional<String> problem(URI url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(url).timeout(TIMEOUT).header("Accept", "image/*").GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream ignored = response.body()) {
                if (response.statusCode() / 100 != 2) return Optional.of("el enlace responde " + response.statusCode());
                String type = response.headers().firstValue("Content-Type").orElse("");
                if (!type.toLowerCase(Locale.ROOT).startsWith("image/")) {
                    return Optional.of("el enlace no devuelve una imagen (" + (type.isBlank() ? "sin tipo" : type) + ")");
                }
            }
            return Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            return Optional.of("no se puede abrir el enlace: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("comprobación del enlace interrumpida");
        }
    }

    private Optional<Path> folder(String ordersFolder) {
        try {
            if (!fixedFolder.isEmpty()) return Optional.of(Path.of(fixedFolder));
            return Optional.of(Path.of(ordersFolder).resolve(FOLDER));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}
