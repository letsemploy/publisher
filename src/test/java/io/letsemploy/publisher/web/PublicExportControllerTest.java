package io.letsemploy.publisher.web;

import io.letsemploy.publisher.domain.JobExport;
import io.letsemploy.publisher.service.ExportPayload;
import io.letsemploy.publisher.service.ExportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicExportControllerTest {

    @Mock
    private ExportService exportService;

    @Test
    void servesActiveExportAsJson() throws Exception {
        JobExport export = new JobExport();
        when(exportService.activeExport("example-domain")).thenReturn(export);
        when(exportService.buildPayload(export)).thenReturn(new ExportPayload(
                "1.0",
                OffsetDateTime.parse("2026-06-12T10:15:30Z"),
                new ExportPayload.EmployerPayload("Example Co", new ExportPayload.LocationPayload("Zurich", "CH"), "Software", "https://example.com"),
                List.of(new ExportPayload.JobPayload("en", LocalDate.parse("2026-06-10"), null, null, null, "Engineering", "JOB-1", "Platform Engineer", "Build systems", "permanent", null, null, "remote", null, List.of("Java"), List.of(new ExportPayload.LocationPayload("Zurich", "CH")), "https://example.com/jobs/1"))
        ));

        PublicExportController controller = new PublicExportController(exportService);
        ExportPayload payload = controller.publicExport("example-domain");

        assertThat(payload.version()).isEqualTo("1.0");
        assertThat(payload.jobs()).singleElement().extracting(ExportPayload.JobPayload::title).isEqualTo("Platform Engineer");
    }

    @Test
    void endpointDeclaresJsonContentType() throws Exception {
        GetMapping mapping = PublicExportController.class
                .getDeclaredMethod("publicExport", String.class)
                .getAnnotation(GetMapping.class);

        assertThat(mapping.produces()).containsExactly("application/json");
    }
}
