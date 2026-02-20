package ru.practicum.client.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class RestStatsClient implements StatsClient {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final RestClient restClient;

    public RestStatsClient(@Value("${CLIENT_URL:http://localhost:9090}") String statsServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(statsServiceUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public void hit(EndpointHitDto endpointHitDto) {
        try {
            restClient.post().uri("/hit").body(endpointHitDto).retrieve().toBodilessEntity();

            log.debug("Статистика успешно отправлена: app={}, uri={}", endpointHitDto.getApp(), endpointHitDto.getUri());

        } catch (Exception e) {
            log.error("Не удалось отправить статистику: app={}, uri={}, ошибка: {}",
                    endpointHitDto.getApp(), endpointHitDto.getUri(), e.getMessage());
        }
    }

    @Override
    public List<ViewStatsDto> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique) {
        try {
            ResponseEntity<List<ViewStatsDto>> response = restClient.get().uri(uriBuilder -> {
                var builder = uriBuilder.path("/stats")
                        .queryParam("start", start.format(FORMATTER))
                        .queryParam("end", end.format(FORMATTER));

                if (uris != null && !uris.isEmpty()) {
                    builder.queryParam("uris", String.join(",", uris));
                }
                if (unique != null) {
                    builder.queryParam("unique", unique);
                }
                return builder.build();
            }).retrieve().toEntity(new ParameterizedTypeReference<>() {
            });

            List<ViewStatsDto> stats = response.getBody() != null ? response.getBody() : Collections.emptyList();
            log.debug("Статистика успешно получена: период с {} по {}, количество записей: {}", start, end, stats.size());

            return stats;

        } catch (Exception e) {
            log.error("Не удалось получить статистику: период с {} по {}, ошибка: {}", start, end, e.getMessage());
            return Collections.emptyList();
        }
    }
}
