package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.Impersonation;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

/**
 * A person's settings (spec 7.26): saved with the account, applied to every new
 * session, chosen from the page or the user menu - and never written for someone
 * an admin is viewing as.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class SettingsTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;

    @MockitoBean
    private CurrentUserService currentUserService;

    private UserEntity person;

    @BeforeEach
    void aPerson() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("settings-" + UUID.randomUUID());
        user.setEmail("settings@example.com");
        user.setDisplayName("Sam Settings");
        person = userRepo.save(user);
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(
                Actor.user(person.getId(), "Sam Settings", person.getEmail(), false, Map.of()));
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private UserEntity reloaded() {
        return userRepo.findById(person.getId()).orElseThrow();
    }

    private String render(String path, MockHttpSession session) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theChoicesAreSavedAndShownAtOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/settings/display").session(session).param("language", "de").param("theme", "dark")
                        .param("timeZone", "America/New_York"))
                .andExpect(redirectedUrl("/settings/display"));

        UserEntity saved = reloaded();
        assertThat(saved.getLanguage()).isEqualTo("de");
        assertThat(saved.getTheme()).isEqualTo("dark");
        assertThat(saved.getTimeZone()).isEqualTo("America/New_York");

        assertThat(render("/settings/display", session))
                .contains("lang=\"de\"").contains("data-bs-theme=\"dark\"").contains("Einstellungen");
    }

    /**
     * Each page saves only its own fields (spec 7.26): the display page sends no
     * mail switches, and must not read their absence as "off" - nor the other way.
     */
    @Test
    void eachPageSavesOnlyItsOwnFields() throws Exception {
        person.setMailInvitations(true);
        person.setMailSummary(true);
        person.setLanguage("fr");
        userRepo.save(person);

        mvc.perform(post("/settings/display").param("language", "de").param("theme", "auto").param("timeZone", ""))
                .andExpect(redirectedUrl("/settings/display"));
        assertThat(reloaded().isMailInvitations()).isTrue();
        assertThat(reloaded().isMailSummary()).isTrue();

        mvc.perform(post("/settings/notifications"))
                .andExpect(redirectedUrl("/settings/notifications"));
        UserEntity saved = reloaded();
        assertThat(saved.isMailInvitations()).isFalse();
        assertThat(saved.isMailSummary()).isFalse();
        assertThat(saved.getLanguage()).isEqualTo("de");
    }

    /** One navigation for every section, the current one marked (spec 7.26, 7.9). */
    @Test
    void everySectionHasTheNavigation() throws Exception {
        for (String path : java.util.List.of("/settings", "/settings/display", "/settings/notifications",
                "/account/delete")) {
            assertThat(render(path, new MockHttpSession()))
                    .as(path)
                    .contains("href=\"/settings/display\"").contains("href=\"/settings/notifications\"")
                    .contains("href=\"/account/delete\"").contains("aria-current=\"page\"")
                    // A provider's account has no password here (spec 2.12).
                    .doesNotContain("href=\"/account/password\"");
        }
    }

    /** What makes them settings rather than session state: the next sign-in has them. */
    @Test
    void aNewSessionStartsWithThem() throws Exception {
        person.setLanguage("de");
        person.setTheme("light");
        person.setTimeZone("Asia/Tokyo");
        userRepo.save(person);

        MockHttpSession fresh = new MockHttpSession();
        assertThat(render("/", fresh)).contains("lang=\"de\"").contains("data-bs-theme=\"light\"");
        assertThat(fresh.getAttribute(SessionLocaleResolver.TIME_ZONE_SESSION_ATTRIBUTE_NAME))
                .isEqualTo(TimeZone.getTimeZone("Asia/Tokyo"));
    }

    @Test
    void unchosenMeansTheDefaults() throws Exception {
        person.setLanguage("de");
        person.setTheme("dark");
        person.setTimeZone("Asia/Tokyo");
        userRepo.save(person);
        mvc.perform(post("/settings/display").param("language", "").param("theme", "auto").param("timeZone", ""))
                .andExpect(redirectedUrl("/settings/display"));
        mvc.perform(post("/settings/notifications").param("mailInvitations", "true").param("mailSummary", "true"))
                .andExpect(redirectedUrl("/settings/notifications"));

        UserEntity saved = reloaded();
        // The weekly summary is asked for (spec 7.28).
        assertThat(saved.isMailSummary()).isTrue();
        assertThat(saved.getLanguage()).isNull();
        assertThat(saved.getTheme()).isNull();
        assertThat(saved.getTimeZone()).isNull();
        assertThat(saved.isMailInvitations()).isTrue();
    }

    @Test
    void onlyWhatIsOfferedIsSaved() throws Exception {
        assertThat(mvc.perform(post("/settings/display").param("language", "de").param("timeZone", "Mars/Olympus"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("Choose one of the options offered.");
        assertThat(reloaded().getLanguage()).isNull();
    }

    /** A provider names its accounts (spec 2.2): only a local account renames itself. */
    @Test
    void aProvidersAccountCannotBeRenamed() throws Exception {
        assertThat(render("/settings", new MockHttpSession()))
                .contains("id=\"name-readonly\"").doesNotContain("name=\"name\"");
        mvc.perform(post("/settings").param("name", "Someone Else")).andExpect(status().isNotFound());
        assertThat(reloaded().getDisplayName()).isEqualTo("Sam Settings");
    }

    /**
     * Viewing as someone is read-only (spec 2.9): not their settings to see or
     * change, and theirs are not applied to the admin's session either.
     */
    @Test
    void notWhileViewingAsSomeone() throws Exception {
        person.setLanguage("de");
        userRepo.save(person);
        given(currentUserService.isImpersonating()).willReturn(true);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(Impersonation.class.getName(), new Impersonation.State(UUID.randomUUID(), person.getId()));

        for (String path : java.util.List.of("/settings", "/settings/display", "/settings/notifications")) {
            mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
        }
        assertThat(render("/", session)).contains("lang=\"en\"");
    }

    @Test
    void timestampsAreShownInTheViewersZone() {
        Instant noonUtc = Instant.parse("2026-06-01T12:00:00Z");
        LocaleContextHolder.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertThat(Views.timestamp(noonUtc)).isEqualTo("2026-06-01 12:00");
        LocaleContextHolder.setLocaleContext(new org.springframework.context.i18n.SimpleTimeZoneAwareLocaleContext(
                Locale.ENGLISH, TimeZone.getTimeZone("Asia/Tokyo")));
        assertThat(Views.timestamp(noonUtc)).isEqualTo("2026-06-01 21:00");
    }
}
