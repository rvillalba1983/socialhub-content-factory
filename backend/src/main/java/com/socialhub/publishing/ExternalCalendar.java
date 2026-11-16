package com.socialhub.publishing;

import com.socialhub.publishing.domain.ExternalCalendarRepository.Item;
import com.socialhub.publishing.domain.ExternalCalendarRepository.State;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** El calendario externo (Publer y las redes) leído para una promoción. */
@PrimaryPort
public interface ExternalCalendar {

    record View(List<Item> items, List<State> states) {}

    /** Lo de fuera de SocialHub de la marca de la promoción entre esos días (hora de España), y cómo va cada fuente. */
    View view(String promotionId, LocalDate from, LocalDate to);

    /** Lo de fuera que ya ocupa esos días: para las propuestas de la semana. */
    List<Map<String, Object>> forAi(String promotionId, LocalDate from, LocalDate to);
}
