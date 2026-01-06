package org.letsemploy.ojobpub_publisher.web;

import java.io.Serializable;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * The active employer and theme (spec 2.5, 7.3).
 *
 * <p>Held server-side in the session, never in a client-controlled cookie: it is a
 * convenience, and authorization is always derived from membership instead.
 */
@Component
@SessionScope
@Getter
@Setter
public class EmployerContext implements Serializable {

    private UUID activeEmployerId;
    private String theme = "light";
}
