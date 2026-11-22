package com.socialhub.launcher.adapter.in.tray;

import com.socialhub.launcher.Launcher;
import com.socialhub.launcher.domain.TrayMenu;
import com.socialhub.platform.desktop.Desktop;
import jakarta.annotation.PreDestroy;
import java.awt.CheckboxMenuItem;
import java.awt.GraphicsEnvironment;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * El icono de SocialHub en la bandeja del sistema (junto al reloj), con {@code java.awt.SystemTray} (sin librerías). Solo con
 * SocialHub instalado y si el sistema tiene bandeja: en cualquier otro caso la aplicación sigue igual, sin icono. Con el
 * arranque con Windows, el icono es la señal de que está corriendo. Doble clic = abrir.
 */
@Slf4j
@Component
class TrayIconInstaller {

    private final Launcher launcher;
    private final TrayActions actions;
    private @Nullable TrayIcon icon;

    TrayIconInstaller(Launcher launcher) {
        this.launcher = launcher;
        this.actions = new TrayActions(launcher);
    }

    @EventListener(ApplicationReadyEvent.class)
    void install() {
        if (!Desktop.enabled()) return;
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                log.info("Este sistema no tiene bandeja del sistema (o SocialHub arrancó sin modo gráfico): se sigue sin icono");
                return;
            }
            SystemTray tray = SystemTray.getSystemTray();
            TrayIcon created = new TrayIcon(AppIcon.draw(Math.max(16, tray.getTrayIconSize().width)), TrayMenu.TOOLTIP);
            created.setImageAutoSize(true);
            created.setToolTip(TrayMenu.TOOLTIP);
            created.setPopupMenu(menu());
            created.addActionListener(e -> actions.perform(TrayMenu.Action.OPEN)); // doble clic
            tray.add(created);
            icon = created;
            log.info("Icono de SocialHub en la bandeja del sistema");
        } catch (Throwable e) {
            // Sin pantalla, sin bandeja o con un fallo gráfico: SocialHub no depende del icono
            log.info("No se pudo poner el icono en la bandeja: {}", e.toString());
        }
    }

    private PopupMenu menu() {
        Launcher.Autostart autostart = launcher.autostart();
        PopupMenu menu = new PopupMenu();
        for (TrayMenu.Entry entry : TrayMenu.entries(launcher.version(), autostart.available(), autostart.enabled())) {
            switch (entry.kind()) {
                case SEPARATOR -> menu.addSeparator();
                case TEXT -> {
                    MenuItem text = new MenuItem(entry.label());
                    text.setEnabled(false);
                    menu.add(text);
                }
                case ACTION -> {
                    MenuItem item = new MenuItem(entry.label());
                    item.addActionListener(e -> run(entry.action()));
                    menu.add(item);
                }
                case CHECK -> {
                    CheckboxMenuItem item = new CheckboxMenuItem(entry.label(), entry.checked());
                    item.addItemListener(e -> {
                        Boolean now = run(entry.action());
                        if (now != null) item.setState(now);
                    });
                    menu.add(item);
                }
            }
        }
        return menu;
    }

    private @Nullable Boolean run(TrayMenu.@Nullable Action action) {
        if (action == null) return null;
        try {
            return actions.perform(action);
        } catch (RuntimeException e) {
            log.warn("La acción «{}» de la bandeja falló: {}", action, e.getMessage());
            return null;
        }
    }

    @PreDestroy
    void remove() {
        TrayIcon current = icon;
        if (current == null) return;
        try {
            SystemTray.getSystemTray().remove(current);
        } catch (RuntimeException e) {
            log.debug("No se pudo quitar el icono de la bandeja: {}", e.getMessage());
        }
        icon = null;
    }
}
