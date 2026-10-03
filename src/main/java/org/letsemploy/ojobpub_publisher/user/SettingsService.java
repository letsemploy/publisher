package org.letsemploy.ojobpub_publisher.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.account.LocalAccount;
import org.letsemploy.ojobpub_publisher.account.LocalAccountRepo;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.config.WebLangConfig;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's own settings (spec 7.26): language, theme, time zone, whether
 * invitations are mailed to them, and - for a local account - their name.
 *
 * <p>Only ever one's own: the actor is the record, so there is no id to name
 * someone else's. Never a token, which has no settings, and never while viewing as
 * someone - the screen refuses that, and the menu switches only write here for a
 * person acting as themselves.
 */
@Service
public class SettingsService {

    public static final List<String> THEMES = List.of("auto", "light", "dark");

    /** Region zones by name, and UTC - not the aliases and offsets Java also knows. */
    public static final List<String> ZONES = ZoneId.getAvailableZoneIds().stream()
            .filter(id -> id.equals("UTC")
                    || id.matches("(Africa|America|Antarctica|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.+"))
            .sorted()
            .toList();

    private final UserRepo userRepo;
    private final LocalAccountRepo localAccounts;
    private final InputValidator inputs;

    public SettingsService(UserRepo userRepo, LocalAccountRepo localAccounts, InputValidator inputs) {
        this.userRepo = userRepo;
        this.localAccounts = localAccounts;
        this.inputs = inputs;
    }

    /** A local account's new name: the same rule as at sign-up (spec 2.12). */
    record NameChange(
            @NotBlank(message = "{validation.name.required}")
            @Size(max = 255, message = "{validation.name.tooLong}")
            String name) {
    }

    @Transactional(readOnly = true)
    public UserEntity self(Actor actor) {
        if (actor.isToken() || actor.isAnonymous()) {
            throw new NotFoundException("Not found.");
        }
        return userRepo.findById(actor.getId()).orElseThrow(() -> new NotFoundException("Not found."));
    }

    /** Whether this is a local account, whose name is its own to change (spec 2.12). */
    public static boolean isLocal(UserEntity user) {
        return LocalAccount.ISSUER.equals(user.getIssuer());
    }

    /**
     * Save the settings form, all of it or none. A blank language or zone means
     * "not chosen", and so does the System theme: each is stored as null, and the
     * browser's or the server's default applies. {@code name} is null when the
     * form has no name field - every account but a local one.
     */
    @Transactional
    public UserEntity save(Actor actor, String language, String theme, String timeZone, boolean mailInvitations,
                           String name) {
        UserEntity user = self(actor);
        if (name != null) {
            rename(user, name);
        }
        String lang = blankToNull(language);
        String zone = blankToNull(timeZone);
        String look = blankToNull(theme);
        if (lang != null && !WebLangConfig.LANGUAGES.contains(lang)) {
            throw new ValidationFailure("language", "Choose one of the languages offered.");
        }
        if (look != null && !THEMES.contains(look)) {
            throw new ValidationFailure("theme", "Choose one of the themes offered.");
        }
        if (zone != null && !ZONES.contains(zone)) {
            throw new ValidationFailure("timeZone", "Choose one of the time zones offered.");
        }
        user.setLanguage(lang);
        user.setTheme("auto".equals(look) ? null : look);
        user.setTimeZone(zone);
        user.setMailInvitations(mailInvitations);
        return userRepo.save(user);
    }

    /**
     * A local account renames itself (spec 2.12). Both records change: the
     * account's, which every sign-in reads the name from, and the user's, which
     * every screen shows. A provider's account is named by its provider and
     * refreshed from it at each sign-in (spec 2.2), so it cannot.
     */
    private void rename(UserEntity user, String name) {
        if (!isLocal(user)) {
            throw new NotFoundException("Not a local account.");
        }
        inputs.check(new NameChange(name));
        String trimmed = name.strip();
        LocalAccount account = localAccounts.findById(UUID.fromString(user.getSubject()))
                .orElseThrow(() -> new NotFoundException("Not found."));
        account.setDisplayName(trimmed);
        localAccounts.save(account);
        user.setDisplayName(trimmed);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
