package ru.practicum.ewm.compilation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.ewm.compilation.dto.CompilationDto;
import ru.practicum.ewm.compilation.dto.NewCompilationDto;
import ru.practicum.ewm.compilation.model.Compilation;
import ru.practicum.ewm.event.dto.EventShortDto;
import ru.practicum.ewm.event.mapper.EventMapper;
import ru.practicum.ewm.event.model.Event;

import java.util.*;

@Mapper(componentModel = "spring", uses = EventMapper.class)
public interface CompilationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "events", ignore = true)
    Compilation toEntity(NewCompilationDto newCompilationDto);

    @Mapping(target = "events", source = "events")
    CompilationDto toDto(Compilation compilation);

    default CompilationDto toDtoWithStats(Compilation compilation,
                                          Map<Long, Long> confirmedRequestsMap,
                                          Map<Long, Long> viewsMap,
                                          EventMapper eventMapper) {
        CompilationDto dto = toDto(compilation);

        if (compilation.getEvents() != null && !compilation.getEvents().isEmpty()) {
            List<Event> eventsList = new ArrayList<>(compilation.getEvents());

            List<EventShortDto> eventDtos = eventMapper.toShortDto(
                    eventsList,
                    confirmedRequestsMap,
                    viewsMap
            );

            dto.setEvents(new HashSet<>(eventDtos));
        }

        return dto;
    }
}
