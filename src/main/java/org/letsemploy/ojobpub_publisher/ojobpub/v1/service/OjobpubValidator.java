package org.letsemploy.ojobpub_publisher.ojobpub.v1.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Validates a generated document against the checked-in copy of the oJobPub v1
 * schema. The copy is authoritative offline; upstream is
 * {@code https://raw.githubusercontent.com/letsemploy/schema/refs/heads/main/v1/ojobpub.json}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OjobpubValidator {

    private final ObjectMapper objectMapper;
    private JsonSchema schema;

    @PostConstruct
    public void load() throws Exception {
        try (InputStream in = new ClassPathResource("ojobpub/v1/ojobpub.schema.json").getInputStream()) {
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(objectMapper.readTree(in));
        }
    }

    public List<String> validate(Object document) {
        JsonNode node = objectMapper.valueToTree(document);
        Set<ValidationMessage> messages = schema.validate(node);
        return messages.stream().map(ValidationMessage::getMessage).sorted().toList();
    }

    public boolean isValid(Object document) {
        List<String> problems = validate(document);
        if (!problems.isEmpty()) {
            log.warn("Generated document does not conform to the ojobpub schema: {}", problems);
        }
        return problems.isEmpty();
    }
}
