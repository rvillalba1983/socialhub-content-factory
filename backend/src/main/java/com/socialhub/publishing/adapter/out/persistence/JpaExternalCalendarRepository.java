package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.ExternalCalendarRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaExternalCalendarRepository implements ExternalCalendarRepository {

    private final ExternalPostJpaRepository posts;
    private final ExternalStateJpaRepository states;

    @Override
    public void replace(String brandId, Source source, Instant from, Instant to, List<Item> items, Instant seenAt) {
        posts.deleteWindow(brandId, source, from, to);
        posts.flush();
        items.stream().filter(i -> i.source() == source && !i.at().isBefore(from) && i.at().isBefore(to))
                .forEach(i -> posts.save(new ExternalCalendarEntities.PostEntity(brandId, i, seenAt)));
    }

    @Override
    public List<Item> between(String brandId, Instant from, Instant to) {
        return posts.between(brandId, from, to).stream()
                .map(p -> new Item(p.getSource(), p.getPlatform(), p.getAt(), p.getStatus(), p.getText(), p.getUrl(),
                        p.getExternalId(), p.getPublicationId()))
                .toList();
    }

    @Override
    public Optional<State> state(String brandId, Source source) {
        return states.findById(new ExternalCalendarEntities.StateKey(brandId, source)).map(JpaExternalCalendarRepository::toState);
    }

    @Override
    public List<State> states(String brandId) {
        return states.findByBrandIdOrderBySource(brandId).stream().map(JpaExternalCalendarRepository::toState).toList();
    }

    @Override
    public void saveState(String brandId, State s) {
        states.save(new ExternalCalendarEntities.StateEntity(brandId, s.source(), s.requestedOn(), s.taskId(), s.syncedAt(), s.error()));
    }

    private static State toState(ExternalCalendarEntities.StateEntity e) {
        return new State(e.getSource(), e.getRequestedOn(), e.getTaskId(), e.getSyncedAt(), e.getError());
    }
}
