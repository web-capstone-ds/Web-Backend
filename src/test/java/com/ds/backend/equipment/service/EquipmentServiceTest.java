package com.ds.backend.equipment.service;

import com.ds.backend.analysis.dto.AiDtos.KpiSummaryResponse;
import com.ds.backend.analysis.service.AiServerClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentServiceTest {
    private final RecipeSpecService recipeSpecService = mock(RecipeSpecService.class);
    private final AiServerClient aiServerClient = mock(AiServerClient.class);
    private final EquipmentService service = new EquipmentService(recipeSpecService, aiServerClient);

    @Test
    void downtimeTrendUsesAiSummaryDowntimeMinutesForOneDay() {
        when(aiServerClient.kpiSummary(anyMap())).thenReturn(Optional.of(summary(904.5)));

        Map<String, Object> result = service.downtimeTrend(
                LocalDate.of(2026, 6, 4),
                LocalDate.of(2026, 6, 4),
                "all"
        );

        assertThat(result.get("unit")).isEqualTo("min");
        assertThat(firstDataPoint(result)).containsEntry("label", "06/04").containsEntry("value", 904.5);
    }

    @Test
    void downtimeTrendConvertsMinutesToHoursForRange() {
        when(aiServerClient.kpiSummary(anyMap())).thenReturn(Optional.of(summary(180.0)));

        Map<String, Object> result = service.downtimeTrend(
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 4),
                "all"
        );

        assertThat(result.get("unit")).isEqualTo("hr");
        assertThat(firstDataPoint(result)).containsEntry("label", "06/01-06/04").containsEntry("value", 3.0);
    }

    @Test
    void downtimeTrendQueriesAiWithKstDayBoundaryAndSelectedEquipment() {
        when(aiServerClient.kpiSummary(anyMap())).thenReturn(Optional.of(summary(10.0)));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> queryCaptor = ArgumentCaptor.forClass(Map.class);

        service.downtimeTrend(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 4), "EQ-A,EQ-B");

        org.mockito.Mockito.verify(aiServerClient).kpiSummary(queryCaptor.capture());
        assertThat(queryCaptor.getValue())
                .containsEntry("from", "2026-06-03T15:00:00Z")
                .containsEntry("to", "2026-06-04T15:00:00Z")
                .containsEntry("equipmentId", "EQ-A");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstDataPoint(Map<String, Object> result) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        return data.get(0);
    }

    private KpiSummaryResponse summary(double downtimeMin) {
        return new KpiSummaryResponse(
                Map.of(),
                0,
                0,
                0,
                0.0,
                0.0,
                0,
                0,
                0,
                0.0,
                0.0,
                downtimeMin,
                0,
                0,
                0.0,
                List.of(),
                List.of()
        );
    }
}
