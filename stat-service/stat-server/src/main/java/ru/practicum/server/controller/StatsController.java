package ru.practicum.server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.server.service.StatsService;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.CREATED)
    public void hit(@Valid @RequestBody EndpointHitDto hitDto) {
        log.info("Запрос на сохранение статистики: app={}, uri={}", hitDto.getApp(), hitDto.getUri());
        statsService.saveHit(hitDto);
    }

    @GetMapping("/stats")
    public List<ViewStatsDto> getStats(@RequestParam String start,
                                       @RequestParam String end,
                                       @RequestParam(required = false) List<String> uris,
                                       @RequestParam(defaultValue = "false") Boolean unique) {
        log.info("Запрос на получение статистики: start={}, end={}, uris={}, unique={}", start, end, uris, unique);
        return statsService.getStats(start, end, uris, unique);
    }

}
