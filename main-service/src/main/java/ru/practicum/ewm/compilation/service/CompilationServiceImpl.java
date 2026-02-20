package ru.practicum.ewm.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.dto.UpdateCompilationRequest;
import ru.practicum.ewm.compilation.mapper.CompilationMapper;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.compilation.repository.CompilationRepository;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.event.service.EventService;
import ru.practicum.ewm.exception.ConflictException;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.request.service.RequestService;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final CompilationMapper compilationMapper;
    private final EventService eventService;
    private final EventMapper eventMapper;
    private final RequestService requestService;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newCompilationDto) {
        log.info("Создание новой подборки: title={}, pinned={}, eventsCount={}",
                newCompilationDto.getTitle(), newCompilationDto.getPinned(),
                newCompilationDto.getEvents() != null ? newCompilationDto.getEvents().size() : 0);

        if (compilationRepository.existsByTitle(newCompilationDto.getTitle())) {
            throw new ConflictException("Подборка с названием '" + newCompilationDto.getTitle() + "' уже существует");
        }

        Compilation compilation = compilationMapper.toEntity(newCompilationDto);

        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            compilation.setEvents(
                    new HashSet<>(
                            eventService.getEventsByIds(
                                    new ArrayList<>(newCompilationDto.getEvents())
                            )
                    )
            );
        } else {
            compilation.setEvents(new HashSet<>());
        }

        Compilation savedCompilation = compilationRepository.save(compilation);
        log.info("Подборка создана: id={}", savedCompilation.getId());

        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(savedCompilation.getEvents());
        Map<Long, Long> viewsMap = getEventsViewsMap(savedCompilation.getEvents());

        return compilationMapper.toDtoWithStats(savedCompilation, confirmedRequestsMap, viewsMap, eventMapper);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Удаление подборки: id={}", compId);

        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Подборка с id=" + compId + " не найдена");
        }

        compilationRepository.deleteById(compId);
        log.info("Подборка удалена: id={}", compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        log.info("Обновление подборки: id={}, updateRequest={}", compId, updateRequest);

        Compilation compilation = compilationRepository.findByIdWithEvents(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        if (updateRequest.getTitle() != null) {
            compilation.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }

        if (updateRequest.getEvents() != null) {
            if (updateRequest.getEvents().isEmpty()) {
                compilation.setEvents(new HashSet<>());
            } else {
                Set<Event> events = new HashSet<>(eventService.getEventsByIds(updateRequest.getEvents()));
                compilation.setEvents(events);
            }
        }

        Compilation updatedCompilation = compilationRepository.save(compilation);
        log.info("Подборка обновлена: id={}", updatedCompilation.getId());

        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(updatedCompilation.getEvents());
        Map<Long, Long> viewsMap = getEventsViewsMap(updatedCompilation.getEvents());

        return compilationMapper.toDtoWithStats(updatedCompilation, confirmedRequestsMap, viewsMap, eventMapper);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Integer from, Integer size) {
        log.info("Получение подборок: pinned={}, from={}, size={}", pinned, from, size);

        Pageable pageable = PageRequest.of(from / size, size);
        Page<Compilation> compilationsPage;

        if (pinned != null) {
            compilationsPage = compilationRepository.findByPinned(pinned, pageable);
        } else {
            compilationsPage = compilationRepository.findAll(pageable);
        }

        List<Compilation> compilations = compilationsPage.getContent();

        if (compilations.isEmpty()) {
            log.debug("Подборки не найдены");
            return Collections.emptyList();
        }

        Set<Event> allEvents = compilations.stream()
                .flatMap(c -> c.getEvents().stream())
                .collect(Collectors.toSet());

        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(allEvents);
        Map<Long, Long> viewsMap = getEventsViewsMap(allEvents);

        return compilations.stream()
                .map(compilation -> compilationMapper.toDtoWithStats(
                        compilation, confirmedRequestsMap, viewsMap, eventMapper))
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        log.info("Получение подборки по id: {}", compId);

        Compilation compilation = compilationRepository.findByIdWithEvents(compId)
                .orElseThrow(() -> new NotFoundException("Подборка с id=" + compId + " не найдена"));

        Map<Long, Long> confirmedRequestsMap = getConfirmedRequestsMap(compilation.getEvents());
        Map<Long, Long> viewsMap = getEventsViewsMap(compilation.getEvents());

        return compilationMapper.toDtoWithStats(compilation, confirmedRequestsMap, viewsMap, eventMapper);
    }

    private Map<Long, Long> getConfirmedRequestsMap(Set<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        return requestService.countConfirmedRequestsForEvents(eventIds);
    }

    private Map<Long, Long> getEventsViewsMap(Set<Event> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> eventIds = events.stream()
                .map(Event::getId)
                .collect(Collectors.toList());

        return eventService.getViewsForEvents(eventIds);
    }
}