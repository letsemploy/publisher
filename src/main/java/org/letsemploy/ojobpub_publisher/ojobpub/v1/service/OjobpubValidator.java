package org.letsemploy.ojobpub_publisher.ojobpub.v1.service;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

/**
 * Validates a generated document against the checked-in copy of the oJobPub v1
 * schema. The copy is authoritative offline; upstream is
 * {@code https://raw.githubusercontent.com/letsemploy/schema/refs/heads/main/v1/ojobpub.json}.
 *
 * <p>The document is serialised by {@link OjobpubJson}, as the endpoint serves it,
 * and the resulting <em>text</em> is what gets validated. That matters: the point
 * of this class is to assert that what the endpoint publishes conforms, and only
 * the serialised bytes carry the mapper's configuration - the RFC 3339 dates and
 * the omitted nulls. Handing the validator an object tree would check a
 * representation nobody receives.
 */
@Component
public class OjobpubValidator {

    private static final Logger log = LoggerFactory.getLogger(OjobpubValidator.class);

    private final OjobpubJson json;

    public OjobpubValidator(OjobpubJson json) {
        this.json = json;
    }
    private Schema schema;

    @PostConstruct
    public void load() throws Exception {
        try (InputStream in = new ClassPathResource("ojobpub/v1/ojobpub.schema.json").getInputStream()) {
            schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                    .getSchema(in);
        }
    }

    public List<String> validate(Object document) {
        String text;
        try {
            text = new String(json.write(document), StandardCharsets.UTF_8);
        } catch (JacksonException e) {
            // A document that cannot be serialised cannot be published either, so
            // this is a conformance failure rather than an exception to propagate.
            return List.of("Document could not be serialised: " + e.getOriginalMessage());
        }
        return schema.validate(text, InputFormat.JSON).stream()
                .map(Error::getMessage).sorted().toList();
    }

    public boolean isValid(Object document) {
        List<String> problems = validate(document);
        if (!problems.isEmpty()) {
            log.warn("Generated document does not conform to the ojobpub schema: {}", problems);
        }
        return problems.isEmpty();
    }
}
