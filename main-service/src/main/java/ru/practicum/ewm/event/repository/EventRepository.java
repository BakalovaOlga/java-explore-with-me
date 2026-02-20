package ru.practicum.ewm.event.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.QEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, QuerydslPredicateExecutor<Event> {

    List<Event> findByInitiatorId(Long userId, Pageable pageable);

    Optional<Event> findByIdAndInitiatorId(Long eventId, Long userId);

    List<Event> findByIdIn(List<Long> events);

    boolean existsByCategoryId(Long categoryId);

    Optional<Event> findByIdAndState(Long id, EventState state);

    //QueryDSL для админа
    static Predicate createAdminPredicate(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        QEvent event = QEvent.event;
        BooleanBuilder builder = new BooleanBuilder();

        if (users != null && !users.isEmpty()) {
            builder.and(event.initiator.id.in(users));
        }

        if (states != null && !states.isEmpty()) {
            builder.and(event.state.in(states));
        }

        if (categories != null && !categories.isEmpty()) {
            builder.and(event.category.id.in(categories));
        }

        if (rangeStart != null) {
            builder.and(event.eventDate.goe(rangeStart));
        }

        if (rangeEnd != null) {
            builder.and(event.eventDate.loe(rangeEnd));
        }

        return builder;
    }

    //QueryDSL для Public
    static Predicate createPublicPredicate(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd
    ) {
        QEvent event = QEvent.event;
        BooleanBuilder builder = new BooleanBuilder();

        builder.and(event.state.eq(EventState.PUBLISHED));

        if (text != null && !text.isBlank()) {
            builder.and(
                    event.annotation.containsIgnoreCase(text)
                            .or(event.description.containsIgnoreCase(text))
            );
        }

        if (categories != null && !categories.isEmpty()) {
            builder.and(event.category.id.in(categories));
        }

        if (paid != null) {
            builder.and(event.paid.eq(paid));
        }

        if (rangeStart != null) {
            builder.and(event.eventDate.goe(rangeStart));
        }
        if (rangeEnd != null) {
            builder.and(event.eventDate.loe(rangeEnd));
        }

        if (rangeStart == null && rangeEnd == null) {
            builder.and(event.eventDate.goe(LocalDateTime.now()));
        }

        return builder;
    }

    @Query("SELECT e FROM Event e WHERE e.id IN :eventIds " +
            "AND (e.participantLimit = 0 OR " +
            "(SELECT COUNT(r) FROM Request r WHERE r.event.id = e.id AND r.status = 'CONFIRMED') < e.participantLimit)")
    List<Event> filterOnlyAvailable(@Param("eventIds") List<Long> eventIds);
}