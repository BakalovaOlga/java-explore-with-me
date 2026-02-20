package ru.practicum.server.service;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.server.exceptions.ValidationException;
import ru.practicum.server.mapper.EndpointHitMapper;
import ru.practicum.server.model.EndpointHit;
import ru.practicum.server.repository.StatsRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StatsRepository statsRepository;
    private final EndpointHitMapper hitMapper;

    @Override
    @Transactional
    public void saveHit(EndpointHitDto endpointHitDto) {
        EndpointHit hit = hitMapper.toEntity(endpointHitDto);
        statsRepository.save(hit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewStatsDto> getStats(String start, String end, List<String> uris, Boolean unique) {
        LocalDateTime startTime = LocalDateTime.parse(start, FORMATTER);
        LocalDateTime endTime = LocalDateTime.parse(end, FORMATTER);
        if (startTime.isAfter(endTime)) {
            throw new ValidationException("старт дата должны быть до конечной даты");
        }
        if (startTime.isAfter(LocalDateTime.now())) {
            throw new ValidationException("Дата начала не может быть в будущем");
        }

        if (Boolean.TRUE.equals(unique)) {
            return statsRepository.getUniqueStats(startTime, endTime, uris);
        } else {
            return statsRepository.getStats(startTime, endTime, uris);
        }
    }
}
