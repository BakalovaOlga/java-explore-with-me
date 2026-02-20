package ru.practicum.ewm.event.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.ewm.category.mapper.CategoryMapper;
import ru.practicum.ewm.event.dto.*;
import ru.practicum.ewm.event.model.Event;
import ru.practicum.ewm.user.mapper.UserMapper;

import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring",
        uses = {CategoryMapper.class, UserMapper.class})
public interface EventMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdOn", ignore = true)
    @Mapping(target = "publishedOn", ignore = true)
    @Mapping(target = "state", ignore = true)
    @Mapping(target = "initiator", ignore = true)
    @Mapping(target = "category", ignore = true)
    Event toEntity(NewEventDto newEventDto);

    EventFullDto toFullDto(Event event, Long confirmedRequests, Long views);

    EventShortDto toShortDto(Event event);

    EventShortDto toShortDto(Event event, Long confirmedRequests, Long views);

    //Маппинг списка с использованием мапы confirmedRequests и views
    default List<EventShortDto> toShortDto(List<Event> events,
                                           Map<Long, Long> confirmedRequestsMap,
                                           Map<Long, Long> viewsMap) {
        return events.stream()
                .map(event -> toShortDto(
                        event,
                        confirmedRequestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    //для FullDto
    default List<EventFullDto> toFullDto(List<Event> events,
                                         Map<Long, Long> confirmedRequestsMap,
                                         Map<Long, Long> viewsMap) {
        return events.stream()
                .map(event -> toFullDto(
                        event,
                        confirmedRequestsMap.getOrDefault(event.getId(), 0L),
                        viewsMap.getOrDefault(event.getId(), 0L)
                ))
                .collect(java.util.stream.Collectors.toList());
    }
}
