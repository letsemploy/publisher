package org.letsemploy.ojobpub_publisher.config;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * The htmx library's view resolver, for {@code redirect:htmx:} (web/FormViews).
 * The library registers it only {@code @ConditionalOnBean(View.class)}, and this
 * application has no {@code View} bean, so without this Thymeleaf's resolver took
 * {@code redirect:htmx:/tags} as an ordinary redirect - a 302 to
 * {@code htmx:/tags} - and a form saved from its modal went nowhere.
 *
 * <p>It must come before Thymeleaf's resolver (lowest precedence - 5), which
 * claims every {@code redirect:}; it answers only its own prefixes and null for
 * every other view name. The order is the one the library itself would give it.
 */
@Configuration(proxyBeanMethods = false)
public class HtmxViewConfig {

    @Bean
    HtmxViewResolver htmxViewResolver() {
        HtmxViewResolver resolver = new HtmxViewResolver();
        resolver.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return resolver;
    }
}
