package org.letsemploy.ojobpub_publisher.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.config.WebLangConfig;
import org.letsemploy.ojobpub_publisher.mail.Mailer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.web.UserPreferences;
import org.letsemploy.ojobpub_publisher.web.view.FieldError;
import org.letsemploy.ojobpub_publisher.web.view.Avatar;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.letsemploy.ojobpub_publisher.web.view.SettingsView;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * A person's own settings (spec 7.26), from the user menu. The rules are
 * {@link SettingsService}'s; this applies what was saved to the session at once,
 * so the page that comes back is already in the new language and theme.
 *
 * <p>Not while viewing as someone: the screen answers 404, and the POST never
 * gets here, because {@code ImpersonationGuard} refuses it first (spec 2.9).
 */
@Controller
public class SettingsController {

    private final SettingsService settings;
    private final CurrentUserService currentUserService;
    private final UserPreferences preferences;
    private final Mailer mailer;
    private final MessageSource messages;
    private final PictureService pictures;

    public SettingsController(SettingsService settings,
                              CurrentUserService currentUserService,
                              UserPreferences preferences,
                              Mailer mailer,
                              MessageSource messages,
                              PictureService pictures) {
        this.settings = settings;
        this.currentUserService = currentUserService;
        this.preferences = preferences;
        this.mailer = mailer;
        this.messages = messages;
        this.pictures = pictures;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        UserEntity user = settings.self(self());
        return page(model, user, user.getLanguage(), user.getTheme(), user.getTimeZone(),
                user.isMailInvitations(), user.getDisplayName(), Map.of());
    }

    @PostMapping("/settings")
    public String save(@RequestParam(required = false) String language,
                       @RequestParam(required = false) String theme,
                       @RequestParam(required = false) String timeZone,
                       @RequestParam(defaultValue = "false") boolean mailInvitations,
                       @RequestParam(required = false) String name,
                       HttpServletRequest request, HttpServletResponse response,
                       Model model, RedirectAttributes flash) {
        Actor actor = self();
        UserEntity saved;
        try {
            saved = settings.save(actor, language, theme, timeZone, mailInvitations, name);
        } catch (ValidationFailure e) {
            Map<String, String> errors = new LinkedHashMap<>();
            // A select offers only what may be chosen, so only a name is ever
            // refused for something the person typed; its message is localized.
            e.getFieldErrors().forEach((field, message) ->
                    errors.put(field, field.equals("name") ? message : message("settings.invalid")));
            return page(model, settings.self(actor), language, theme, timeZone, mailInvitations, name, errors);
        }
        preferences.apply(request, response, saved, true);
        flash.addFlashAttribute("successMsg", "settings.saved");
        return "redirect:/settings";
    }

    private String page(Model model, UserEntity user, String language, String theme, String timeZone,
                        boolean mailInvitations, String name, Map<String, String> fieldErrors) {
        model.addAttribute("page", PageMeta.of(message("settings.title")));
        model.addAttribute("settings", new SettingsView(language, theme == null ? "auto" : theme, timeZone,
                mailInvitations, name, user.getEmail(),
                Avatar.of(pictures.urlFor(user.getId()).orElse(null), user.getLabel()),
                pictures.hasUpload(user.getId()), PictureProcessor.MAX_BYTES, SettingsService.isLocal(user), mailer.configured(),
                WebLangConfig.LANGUAGES, SettingsService.THEMES, SettingsService.ZONES,
                ZoneId.systemDefault().getId()));
        model.addAttribute("fieldErrors", fieldErrors);
        model.addAttribute("errors", fieldErrors.entrySet().stream()
                .map(e -> new FieldError(e.getKey(), e.getValue())).toList());
        return "user/settings";
    }

    private Actor self() {
        if (currentUserService.isImpersonating()) {
            throw new NotFoundException("Not found.");
        }
        return currentUserService.current();
    }

    private String message(String key) {
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
