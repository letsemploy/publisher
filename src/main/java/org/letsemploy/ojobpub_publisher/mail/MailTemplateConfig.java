package org.letsemploy.ojobpub_publisher.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

/**
 * Plain-text mail templates, {@code templates/mail/*.txt}. Boot adds every
 * template resolver bean to its engine, so mail is rendered by the same engine
 * as the screens, with the same message bundles (spec 8.1).
 */
@Configuration
public class MailTemplateConfig {

    @Bean
    ITemplateResolver mailTemplateResolver() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".txt");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setResolvablePatterns(java.util.Set.of("mail/*"));
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        return resolver;
    }
}
