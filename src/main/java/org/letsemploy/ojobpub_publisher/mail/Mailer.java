package org.letsemploy.ojobpub_publisher.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends the application's mail (spec 2.12), after the change that asked for it
 * has committed.
 *
 * <p>A service calls {@link #send}, which only publishes the message. It is
 * delivered once the transaction commits, so a refused or rolled-back change
 * mails nobody, and delivery never holds a transaction open. A failure is
 * logged and nothing else: what the person is shown must not depend on whether
 * mail went out, or the screen would say more than the no-oracle rule allows.
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final ApplicationEventPublisher events;
    private final ObjectProvider<JavaMailSender> sender;
    private final TemplateEngine templates;
    private final MessageSource messages;
    private final String from;

    public Mailer(ApplicationEventPublisher events,
                  ObjectProvider<JavaMailSender> sender,
                  TemplateEngine templates,
                  MessageSource messages,
                  @Value("${app.mail.from}") String from) {
        this.events = events;
        this.sender = sender;
        this.templates = templates;
        this.messages = messages;
        this.from = from;
    }

    /** Queues the message for delivery after commit, or at once outside a transaction. */
    public void send(Mail mail) {
        events.publishEvent(mail);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void deliver(Mail mail) {
        JavaMailSender smtp = sender.getIfAvailable();
        if (smtp == null) {
            // Never the body: it carries a single-use secret.
            log.warn("No mail server configured (spring.mail.host); a '{}' message was not sent", mail.template());
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject(messages.getMessage(mail.subjectKey(), null, mail.locale()));
        message.setText(templates.process("mail/" + mail.template(), new Context(mail.locale(), mail.variables())));
        try {
            smtp.send(message);
        } catch (MailException e) {
            log.error("Sending a '{}' message failed: {}", mail.template(), e.getMessage());
        }
    }
}
