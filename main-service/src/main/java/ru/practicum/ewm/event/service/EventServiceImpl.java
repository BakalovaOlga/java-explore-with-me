package ru.practicum.ewm.event.service;

import com.querydsl.core.types.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.client.client.StatsClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.ewm.category.model.Category;
import ru.practicum.ewm.category.service.CategoryService;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.model.StateAction;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.exception.ValidationException;
import ru.practicum.ewm.request.service.RequestService;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.service.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {
    private static final int MIN_HOURS_BEFORE_EVENT_USER = 2;
    private static final int MIN_HOURS_BEFORE_EVENT_ADMIN = 1;
    private static final int DEFAULT_RANGE_YEARS = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EventMapper eventMapper;
    private final EventRepository eventRepository;
    private final UserService userService;
    private final CategoryService categoryService;
    private final RequestService requestService;
    private final StatsClient statsClient;

    @Override
    public List<EventShortDto> getUserEvents(Long userId, Integer from, Integer size) {
        log.info("Получение событий пользователя: userId={}, from={}, size={}", userId, from, size);

        userService.getUserById(userId);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        List<Event> events = eventRepository.findByInitiatorId(userId, pageable);

        if (events.isEmpty()) {
            log.debug("У пользователя userId={} нет событий", userId);
            return Collections.emptyList();
        }

        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(events);
        Map<Long, Long> viewsMap = getEventsViewsBatch(events);

        return eventMapper.toShortDto(events, confirmedRequestsMap, viewsMap);
    }

    @Override
    @Transactional
    public EventFullDto createEvent(Long userId, NewEventDto newEventDto) {
        log.info("Создание события пользователем: userId={}, event={}", userId, newEventDto);

        User user = userService.getUserById(userId);
        Category category = categoryService.getEntityById(newEventDto.getCategory());

        validateEventDate(newEventDto.getEventDate(), MIN_HOURS_BEFORE_EVENT_USER);

        Event event = eventMapper.toEntity(newEventDto);
        event.setInitiator(user);
        event.setCategory(category);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());

        Event savedEvent = eventRepository.save(event);
        log.info("Событие создано: id={}", savedEvent.getId());

        return eventMapper.toFullDto(savedEvent, 0L, 0L);
    }

    @Override
    public EventFullDto getUserEventById(Long userId, Long eventId) {
        log.info("Получение события пользователя: userId={}, eventId={}", userId, eventId);

        userService.getUserById(userId);

        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId +
                        " не найдено у пользователя id=" + userId));

        Long confirmedRequests = requestService.countConfirmedRequests(eventId);
        Long views = getEventViews(event);

        return eventMapper.toFullDto(event, confirmedRequests, views);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByUser(Long userId, Long eventId, UpdateEventUserRequest updateRequest) {
        log.info("Обновление события пользователем: userId={}, eventId={}, updateRequest={}",
                userId, eventId, updateRequest);

        userService.getUserById(userId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не принадлежит пользователю id=" + userId);
        }

        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Нельзя редактировать опубликованное событие");
        }

        if (updateRequest.getEventDate() != null) {
            validateEventDate(updateRequest.getEventDate(), MIN_HOURS_BEFORE_EVENT_USER);
        }

        updateEventFieldsByUser(event, updateRequest);

        if (updateRequest.getCategory() != null) {
            Category category = categoryService.getEntityById(updateRequest.getCategory());
            event.setCategory(category);
        }

        if (updateRequest.getStateAction() != null) {
            handleUserStateAction(event, updateRequest.getStateAction());
        }

        Event updatedEvent = eventRepository.save(event);
        log.info("Событие обновлено пользователем: id={}, новый статус={}",
                updatedEvent.getId(), updatedEvent.getState());

        Long confirmedRequests = requestService.countConfirmedRequests(eventId);
        Long views = getEventViews(updatedEvent);

        return eventMapper.toFullDto(updatedEvent, confirmedRequests, views);
    }

    //Админ

    @Override
    public List<EventFullDto> getEventsByAdmin(
            List<Long> users,
            List<EventState> states,
            List<Long> categories,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Integer from,
            Integer size
    ) {
        log.info("Поиск событий администратором: users={}, states={}, categories={}, from={}, size={}",
                users, states, categories, from, size);

        rangeStart = validateAndAdjustRangeStart(rangeStart);
        rangeEnd = validateAndAdjustRangeEnd(rangeEnd);
        validateDateRange(rangeStart, rangeEnd);

        Pageable pageable = PageRequest.of(from / size, size, Sort.by("id").ascending());

        Predicate predicate = EventRepository.createAdminPredicate(
                users, states, categories, rangeStart, rangeEnd
        );

        Page<Event> eventPage = eventRepository.findAll(predicate, pageable);
        List<Event> events = eventPage.getContent();

        if (events.isEmpty()) {
            log.debug("События не найдены по заданным критериям");
            return Collections.emptyList();
        }

        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(events);
        Map<Long, Long> viewsMap = getEventsViewsBatch(events);

        return eventMapper.toFullDto(events, confirmedRequestsMap, viewsMap);
    }

    @Override
    @Transactional
    public EventFullDto updateEventByAdmin(Long eventId, UpdateEventAdminRequest updateRequest) {
        log.info("Обновление события администратором: eventId={}, updateRequest={}", eventId, updateRequest);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (updateRequest.getEventDate() != null) {
            validateEventDate(updateRequest.getEventDate(), MIN_HOURS_BEFORE_EVENT_ADMIN);
        }

        updateEventFieldsByAdmin(event, updateRequest);

        if (updateRequest.getCategory() != null) {
            Category category = categoryService.getEntityById(updateRequest.getCategory());
            event.setCategory(category);
        }

        if (updateRequest.getStateAction() != null) {
            handleAdminStateAction(event, updateRequest.getStateAction());
        }

        Event updatedEvent = eventRepository.save(event);
        log.info("Событие обновлено администратором: id={}, новый статус={}",
                updatedEvent.getId(), updatedEvent.getState());

        Long confirmedRequests = requestService.countConfirmedRequests(eventId);
        Long views = getEventViews(updatedEvent);

        return eventMapper.toFullDto(updatedEvent, confirmedRequests, views);
    }

    //Public

    @Override
    public List<EventShortDto> getEventsPublic(
            String text,
            List<Long> categories,
            Boolean paid,
            LocalDateTime rangeStart,
            LocalDateTime rangeEnd,
            Boolean onlyAvailable,
            String sort,
            Integer from,
            Integer size,
            HttpServletRequest request
    ) {
        log.info("Публичный поиск событий: text={}, categories={}, paid={}, onlyAvailable={}, sort={}, from={}, size={}",
                text, categories, paid, onlyAvailable, sort, from, size);

        rangeStart = validateAndAdjustRangeStart(rangeStart);
        rangeEnd = validateAndAdjustRangeEnd(rangeEnd);
        validateDateRange(rangeStart, rangeEnd);

        List<EventShortDto> result;
        if ("VIEWS".equalsIgnoreCase(sort)) {
            result = findEventsSortedByViews(text, categories, paid, rangeStart, rangeEnd,
                    onlyAvailable, from, size);
        } else {
            result = findEventsSortedByDate(text, categories, paid, rangeStart, rangeEnd,
                    onlyAvailable, from, size);
        }

        sendStatisticHit(request);

        log.debug("Найдено {} событий", result.size());
        return result;
    }

    @Override
    public EventFullDto getEventPublic(Long eventId, HttpServletRequest request) {
        log.info("Получение публичного события: eventId={}, ip={}, uri={}",
                eventId, request.getRemoteAddr(), request.getRequestURI());

        Event event = eventRepository.findByIdAndState(eventId, EventState.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено или не опубликовано"));

        sendStatisticHit(request);

        Long confirmedRequests = requestService.countConfirmedRequests(eventId);
        Long views = getEventViews(event);

        log.info("Событие {} успешно получено, просмотров: {}, подтверждено: {}",
                eventId, views, confirmedRequests);

        return eventMapper.toFullDto(event, confirmedRequests, views);
    }


    @Override
    public List<Event> getEventsByIds(List<Long> eventIds) {
        log.debug("Получение событий по списку ID: {}", eventIds);

        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Event> events = eventRepository.findByIdIn(eventIds);

        if (events.size() != eventIds.size()) {
            List<Long> foundIds = events.stream()
                    .map(Event::getId)
                    .collect(Collectors.toList());
            List<Long> notFoundIds = eventIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());
            throw new NotFoundException("События с ID " + notFoundIds + " не найдены");
        }

        return events;
    }

    @Override
    public Map<Long, Long> getViewsForEvents(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Event> events = eventRepository.findByIdIn(eventIds);
        return getEventsViewsBatch(events);
    }

    private Map<Long, Long> getConfirmedRequestsMap(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        return requestService.countConfirmedRequestsForEvents(eventIds);
    }

    private Map<Long, Long> getEventsViewsBatch(List<Event> events) {
        if (events.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<String> uris = events.stream()
                    .map(event -> "/events/" + event.getId())
                    .collect(Collectors.toList());

            LocalDateTime earliestCreated = events.stream()
                    .map(Event::getCreatedOn)
                    .min(LocalDateTime::compareTo)
                    .orElse(LocalDateTime.now().minusYears(1));

            List<ViewStatsDto> stats = statsClient.getStats(
                    earliestCreated,
                    LocalDateTime.now(),
                    uris,
                    false
            );

            return stats.stream()
                    .collect(Collectors.toMap(
                            stat -> extractEventIdFromUri(stat.getUri()),
                            ViewStatsDto::getHits,
                            (existing, replacement) -> existing
                    ));
        } catch (Exception e) {
            log.error("Ошибка при batch-получении статистики: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Long extractEventIdFromUri(String uri) {
        String[] parts = uri.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }

    private Long getEventViews(Event event) {
        try {
            String uri = "/events/" + event.getId();
            List<ViewStatsDto> stats = statsClient.getStats(
                    event.getCreatedOn(),
                    LocalDateTime.now(),
                    List.of(uri),
                    true
            );

            return stats.stream()
                    .filter(stat -> stat.getUri().equals(uri))
                    .map(ViewStatsDto::getHits)
                    .findFirst()
                    .orElse(0L);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики для события {}: {}", event.getId(), e.getMessage());
            return 0L;
        }
    }

    private void updateEventFieldsByUser(Event event, UpdateEventUserRequest updateRequest) {
        log.trace("Обновление полей события {} из пользовательского запроса", event.getId());

        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
            log.trace("Обновлена аннотация: {}", updateRequest.getAnnotation());
        }

        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
            log.trace("Обновлено описание: {}", updateRequest.getDescription());
        }

        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
            log.trace("Обновлен заголовок: {}", updateRequest.getTitle());
        }

        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
            log.trace("Обновлена дата события: {}", updateRequest.getEventDate());
        }

        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
            log.trace("Обновлен флаг paid: {}", updateRequest.getPaid());
        }

        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
            log.trace("Обновлен лимит участников: {}", updateRequest.getParticipantLimit());
        }

        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
            log.trace("Обновлен флаг requestModeration: {}", updateRequest.getRequestModeration());
        }

        if (updateRequest.getLocation() != null) {
            event.setLocation(updateRequest.getLocation());
            log.trace("Обновлена локация: lat={}, lon={}",
                    updateRequest.getLocation().getLat(),
                    updateRequest.getLocation().getLon());
        }
    }

    private void handleUserStateAction(Event event, StateAction stateAction) {
        if (!stateAction.isUserStateAction()) {
            throw new ValidationException("Некорректное действие для пользователя: " + stateAction);
        }

        switch (stateAction) {
            case SEND_TO_REVIEW:
                event.setState(EventState.PENDING);
                log.debug("Событие {} отправлено на модерацию", event.getId());
                break;
            case CANCEL_REVIEW:
                event.setState(EventState.CANCELED);
                log.debug("Событие {} отменено пользователем", event.getId());
                break;
            default:
                throw new IllegalArgumentException("Неизвестное действие: " + stateAction);
        }
    }

    private void validateEventDate(LocalDateTime eventDate, int minHours) {
        if (eventDate.isBefore(LocalDateTime.now().plusHours(minHours))) {
            throw new ValidationException(String.format("Дата события должна быть не ранее " +
                    "чем через %d часа от текущего момента", minHours)
            );
        }
    }

    private LocalDateTime validateAndAdjustRangeStart(LocalDateTime rangeStart) {
        return rangeStart != null ? rangeStart : LocalDateTime.now();
    }

    private LocalDateTime validateAndAdjustRangeEnd(LocalDateTime rangeEnd) {
        return rangeEnd != null ? rangeEnd : LocalDateTime.now().plusYears(DEFAULT_RANGE_YEARS);
    }

    private void validateDateRange(LocalDateTime rangeStart, LocalDateTime rangeEnd) {
        if (rangeStart.isAfter(rangeEnd)) {
            throw new ValidationException("Начальная дата не может быть позже конечной");
        }
    }

    private void updateEventFieldsByAdmin(Event event, UpdateEventAdminRequest updateRequest) {
        log.trace("Обновление полей события {} из административного запроса", event.getId());

        if (updateRequest.getAnnotation() != null) {
            event.setAnnotation(updateRequest.getAnnotation());
            log.trace("Обновлена аннотация администратором: {}", updateRequest.getAnnotation());
        }

        if (updateRequest.getDescription() != null) {
            event.setDescription(updateRequest.getDescription());
            log.trace("Обновлено описание администратором: {}", updateRequest.getDescription());
        }

        if (updateRequest.getTitle() != null) {
            event.setTitle(updateRequest.getTitle());
            log.trace("Обновлен заголовок администратором: {}", updateRequest.getTitle());
        }

        if (updateRequest.getEventDate() != null) {
            event.setEventDate(updateRequest.getEventDate());
            log.trace("Обновлена дата события администратором: {}", updateRequest.getEventDate());
        }

        if (updateRequest.getPaid() != null) {
            event.setPaid(updateRequest.getPaid());
            log.trace("Обновлен флаг paid администратором: {}", updateRequest.getPaid());
        }

        if (updateRequest.getParticipantLimit() != null) {
            event.setParticipantLimit(updateRequest.getParticipantLimit());
            log.trace("Обновлен лимит участников администратором: {}", updateRequest.getParticipantLimit());
        }

        if (updateRequest.getRequestModeration() != null) {
            event.setRequestModeration(updateRequest.getRequestModeration());
            log.trace("Обновлен флаг requestModeration администратором: {}", updateRequest.getRequestModeration());
        }

        if (updateRequest.getLocation() != null) {
            event.setLocation(updateRequest.getLocation());
            log.trace("Обновлена локация администратором: lat={}, lon={}",
                    updateRequest.getLocation().getLat(),
                    updateRequest.getLocation().getLon());
        }
    }

    private void handleAdminStateAction(Event event, StateAction stateAction) {
        if (!stateAction.isAdminStateAction()) {
            throw new ValidationException("Некорректное действие для администратора: " + stateAction);
        }

        switch (stateAction) {
            case PUBLISH_EVENT:
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException(
                            "Нельзя опубликовать событие в статусе " + event.getState()
                    );
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
                log.debug("Событие {} опубликовано администратором", event.getId());
                break;

            case REJECT_EVENT:
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Нельзя отклонить опубликованное событие");
                }
                event.setState(EventState.CANCELED);
                log.debug("Событие {} отклонено администратором", event.getId());
                break;

            default:
                throw new IllegalArgumentException("Неизвестное действие: " + stateAction);
        }
    }

    private void sendStatisticHit(HttpServletRequest request) {
        try {
            EndpointHitDto hitDto = EndpointHitDto.builder()
                    .app("ewm-main-service")
                    .uri(request.getRequestURI())
                    .ip(request.getRemoteAddr())
                    .timestamp(LocalDateTime.now().format(DATE_TIME_FORMATTER))
                    .build();

            statsClient.hit(hitDto);
            log.debug("Статистика отправлена: uri={}", request.getRequestURI());
        } catch (Exception e) {
            log.error("Ошибка при отправке статистики: {}", e.getMessage());
        }
    }

    private List<EventShortDto> findEventsSortedByDate(
            String text, List<Long> categories, Boolean paid,
            LocalDateTime rangeStart, LocalDateTime rangeEnd,
            Boolean onlyAvailable, Integer from, Integer size
    ) {
        Sort sort = Sort.by("eventDate").ascending();
        Pageable pageable = PageRequest.of(from / size, size, sort);

        Predicate predicate = EventRepository.createPublicPredicate(
                text, categories, paid, rangeStart, rangeEnd
        );

        Page<Event> eventPage = eventRepository.findAll(predicate, pageable);
        List<Event> events = eventPage.getContent();

        if (Boolean.TRUE.equals(onlyAvailable) && !events.isEmpty()) {
            List<Long> eventIds = events.stream()
                    .map(Event::getId)
                    .collect(Collectors.toList());
            events = eventRepository.filterOnlyAvailable(eventIds);
        }

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        return enrichEventsWithStats(events);
    }

    private List<EventShortDto> findEventsSortedByViews(
            String text, List<Long> categories, Boolean paid,
            LocalDateTime rangeStart, LocalDateTime rangeEnd,
            Boolean onlyAvailable, Integer from, Integer size
    ) {

        Predicate predicate = EventRepository.createPublicPredicate(
                text, categories, paid, rangeStart, rangeEnd
        );

        Page<Event> eventPage = eventRepository.findAll(predicate, Pageable.unpaged());
        List<Event> events = eventPage.getContent();

        if (Boolean.TRUE.equals(onlyAvailable) && !events.isEmpty()) {
            List<Long> eventIds = events.stream()
                    .map(Event::getId)
                    .collect(Collectors.toList());
            events = eventRepository.filterOnlyAvailable(eventIds);
        }

        if (events.isEmpty()) {
            return Collections.emptyList();
        }

        List<EventShortDto> dtos = enrichEventsWithStats(events);
        dtos.sort(Comparator.comparing(EventShortDto::getViews).reversed());

        return applyPagination(dtos, from, size);
    }

    private List<EventShortDto> enrichEventsWithStats(List<Event> events) {
        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(events);
        Map<Long, Long> viewsMap = getEventsViewsBatch(events);

        return eventMapper.toShortDto(events, confirmedRequestsMap, viewsMap);
    }

    private List<EventShortDto> applyPagination(List<EventShortDto> dtos, Integer from, Integer size) {
        if (from >= dtos.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(from + size, dtos.size());
        return dtos.subList(from, toIndex);
    }
}
