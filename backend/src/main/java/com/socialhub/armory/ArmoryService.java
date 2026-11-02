package com.socialhub.armory;

import com.socialhub.armory.domain.ArmoryItem;
import com.socialhub.armory.domain.ArmoryRepository;
import com.socialhub.platform.live.FactoryChanged;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Altas y cambios en la armería. */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class ArmoryService {

    private final ArmoryRepository repository;
    private final ApplicationEventPublisher events;

    /**
     * Registra una herramienta local con el resultado de su comprobación.
     *
     * @param ok si funciona; si no, queda con error en la armería
     */
    @Transactional
    public void registerLocalTool(String id, String name, String description, boolean ok, String note,
                                  List<String> capabilities) {
        repository.save(new ArmoryItem(id, "TOOL", name, description, ok ? "OK" : "FAILED", note, capabilities, false));
        events.publishEvent(new FactoryChanged("armory", id));
    }

    /**
     * Registra o actualiza un servicio externo (por ejemplo, Google Drive) con su estado.
     *
     * @param status {@code OK}, {@code BLOCKED} (no disponible) o {@code IDLE} (sin configurar)
     */
    @Transactional
    public void registerService(String id, String name, String description, String status, String note,
                                List<String> capabilities) {
        repository.save(new ArmoryItem(id, "SERVICE", name, description, status, note, capabilities, false));
        events.publishEvent(new FactoryChanged("armory", id));
    }
}
