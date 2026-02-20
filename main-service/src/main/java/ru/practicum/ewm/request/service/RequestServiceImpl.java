package ru.practicum.ewm.request.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.model.EventState;
import ru.practicum.ewm.event.repository.EventRepository;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.ewm.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.ewm.request.dto.ParticipationRequestDto;
import ru.practicum.ewm.request.mapper.RequestMapper;
import ru.practicum.ewm.request.model.Request;
import ru.practicum.ewm.request.model.RequestStatus;
import ru.practicum.ewm.request.repository.RequestRepository;
import ru.practicum.ewm.user.model.User;
import ru.practicum.ewm.user.service.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RequestServiceImpl implements RequestService {
    private final RequestRepository requestRepository;
    private final RequestMapper requestMapper;
    private final UserService userService;
    private final EventRepository eventRepository;

    @Override
    @Transactional
    public ParticipationRequestDto createRequest(Long userId, Long eventId) {
        log.info("Создание запроса на участие: userId={}, eventId={}", userId, eventId);

        User requester = userService.getUserById(userId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        validateRequestCreation(userId, eventId, event);

        RequestStatus status;
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            status = RequestStatus.CONFIRMED;
        } else {
            status = RequestStatus.PENDING;
        }

        Request request = Request.builder()
                .requester(requester)
                .event(event)
                .status(status)
                .build();

        Request savedRequest = requestRepository.save(request);
        log.info("Запрос успешно создан: id={}, status={}", savedRequest.getId(), savedRequest.getStatus());

        return requestMapper.toParticipationRequestDto(savedRequest);
    }

    @Override
    public List<ParticipationRequestDto> getUserRequests(Long userId) {
        log.info("Получение запросов пользователя: userId={}", userId);

        userService.getUserById(userId);

        List<Request> requests = requestRepository.findByRequesterId(userId);
        log.debug("Найдено {} заявок для пользователя {}", requests.size(), userId);

        return requestMapper.toParticipationRequestDto(requests);
    }

    @Override
    public List<ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        log.info("Получение запросов на событие: userId={}, eventId={}", userId, eventId);

        Event event = getEventAndCheckInitiator(eventId, userId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не принадлежит пользователю с id=" + userId);
        }

        List<Request> requests = requestRepository.findByEventId(eventId);
        return requestMapper.toParticipationRequestDto(requests);
    }

    @Override
    @Transactional
    public ParticipationRequestDto cancelRequest(Long userId, Long requestId) {
        log.info("Отмена запроса: userId={}, requestId={}", userId, requestId);

        userService.getUserById(userId);

        Request request = requestRepository.findByIdAndRequesterId(requestId, userId)
                .orElseThrow(() -> new NotFoundException("Заявка с ID " + requestId + " не найдена"));

        if (!request.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Запрос с id=" + requestId + " не принадлежит пользователю с id=" + userId);
        }

        if (request.getStatus() == RequestStatus.CANCELED) {
            throw new ConflictException("Заявка уже отменена");
        }
        if (request.getStatus() == RequestStatus.CONFIRMED) {
            throw new ConflictException("Нельзя отменить уже подтвержденную заявку");
        }

        request.setStatus(RequestStatus.CANCELED);
        Request updated = requestRepository.save(request);

        log.info("Заявка {} отменена пользователем {}", requestId, userId);

        return requestMapper.toParticipationRequestDto(updated);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestStatus(
            Long userId,
            Long eventId,
            EventRequestStatusUpdateRequest updateRequest
    ) {
        log.info("Изменение статуса заявок на событие {} пользователем {}", eventId, userId);

        userService.getUserById(userId);

        Event event = getEventAndCheckInitiator(eventId, userId);

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не принадлежит пользователю с id=" + userId);
        }

        List<Request> requests = requestRepository.findByIdIn(updateRequest.getRequestIds());
        if (requests.isEmpty()) {
            throw new NotFoundException("Заявки не найдены");
        }

        for (Request request : requests) {
            if (!request.getEvent().getId().equals(eventId)) {
                throw new ConflictException("Заявка с ID " + request.getId() +
                        " не относится к событию с ID " + eventId);
            }
        }

        if (shouldAutoConfirmRequests(event)) {
            return autoConfirmRequests(requests);
        }

        return processRequestsWithLimit(event, requests, updateRequest.getStatus());
    }

    private boolean isParticipantLimitReached(Event event, long confirmedCount) {
        if (event.getParticipantLimit() == null || event.getParticipantLimit() == 0) {
            return false;
        }
        return confirmedCount >= event.getParticipantLimit();
    }

    private void validateRequestCreation(Long userId, Long eventId, Event event) {
        if (requestRepository.existsByRequesterIdAndEventId(userId, eventId)) {
            throw new ConflictException("Нельзя добавить повторный запрос");
        }

        if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор события не может добавить запрос на участие в своём событии");
        }

        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Нельзя участвовать в неопубликованном событии");
        }

        long confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (isParticipantLimitReached(event, confirmedRequests)) {
            throw new ConflictException("Достигнут лимит участников");
        }
    }

    private boolean shouldAutoConfirmRequests(Event event) {
        return event.getParticipantLimit() == 0 ||
                (event.getRequestModeration() != null && !event.getRequestModeration());
    }

    private EventRequestStatusUpdateResult autoConfirmRequests(List<Request> requests) {
        List<ParticipationRequestDto> confirmed = new ArrayList<>();

        for (Request request : requests) {
            if (request.getStatus().equals(RequestStatus.PENDING)) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(requestMapper.toParticipationRequestDto(request));
            }
        }

        requestRepository.saveAll(requests);
        log.info("Все заявки автоматически подтверждены (отключена модерация или лимит 0)");

        return new EventRequestStatusUpdateResult(confirmed, List.of());
    }

    private EventRequestStatusUpdateResult processRequestsWithLimit(
            Event event,
            List<Request> requests,
            RequestStatus targetStatus
    ) {
        long confirmedRequests = requestRepository.countByEventIdAndStatus(event.getId(), RequestStatus.CONFIRMED);
        long availableSlots = event.getParticipantLimit() - confirmedRequests;

        if (targetStatus == RequestStatus.CONFIRMED && availableSlots <= 0) {
            throw new ConflictException("Достигнут лимит участников");
        }

        List<ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ParticipationRequestDto> rejected = new ArrayList<>();
        long slotsLeft = availableSlots;

        for (Request request : requests) {
            if (!request.getStatus().equals(RequestStatus.PENDING)) {
                throw new ConflictException("Можно изменять только заявки в статусе PENDING. " +
                        "Заявка ID: " + request.getId() + " имеет статус: " + request.getStatus());
            }

            if (targetStatus == RequestStatus.CONFIRMED && slotsLeft > 0) {
                request.setStatus(RequestStatus.CONFIRMED);
                confirmed.add(requestMapper.toParticipationRequestDto(request));
                slotsLeft--;
                log.debug("Заявка {} подтверждена", request.getId());
            } else {
                request.setStatus(RequestStatus.REJECTED);
                rejected.add(requestMapper.toParticipationRequestDto(request));
                log.debug("Заявка {} отклонена", request.getId());
            }
        }

        requestRepository.saveAll(requests);
        log.info("Статус заявок обновлён: подтверждено - {}, отклонено - {}", confirmed.size(), rejected.size());

        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }

    @Override
    public Long countConfirmedRequests(Long eventId) {
        log.debug("Подсчет подтвержденных запросов для события: eventId={}", eventId);
        return requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
    }

    @Override
    public Map<Long, Long> countConfirmedRequestsForEvents(List<Long> eventIds) {
        log.debug("Массовый подсчет подтвержденных запросов для событий: eventIds={}", eventIds);

        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object[]> results = requestRepository.countConfirmedRequestsByEventIds(eventIds, RequestStatus.CONFIRMED);

        return results.stream()
                .collect(Collectors.toMap(
                        arr -> (Long) arr[0],
                        arr -> (Long) arr[1]
                ));
    }

    private Event getEventAndCheckInitiator(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + eventId + " не найдено"));

        if (!event.getInitiator().getId().equals(userId)) {
            throw new NotFoundException("Событие с id=" + eventId + " не принадлежит пользователю с id=" + userId);
        }

        return event;
    }
}
