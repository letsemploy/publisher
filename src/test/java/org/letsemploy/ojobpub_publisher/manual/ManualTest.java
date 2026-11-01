package org.letsemploy.ojobpub_publisher.manual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The user manual (spec 7.29): built by Hugo into {@code static/docs}, public,
 * sessionless, and held to the same CSP and no-CDN rules as every screen (spec 7.2).
 * The checks run over the build's actual output, so a page, a template or a Hugo
 * upgrade that breaks one of them fails here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
class ManualTest {

    private static final Pattern URL_ATTRIBUTE =
            Pattern.compile("\\b(?:href|src)=(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>");
    private static final Pattern STYLE = Pattern.compile("<style\\b|\\sstyle=", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\son[a-z]+=", Pattern.CASE_INSENSITIVE);

    @Autowired
    private MockMvc mvc;

    private static Path root() throws IOException {
        return new ClassPathResource("static/docs").getFile().toPath();
    }

    /** Every generated page, as its path under /docs ("en/jobs.html"). */
    private static List<String> pages() throws IOException {
        Path root = root();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(p -> p.toString().endsWith(".html"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted().toList();
        }
    }

    private static String read(String page) throws IOException {
        return Files.readString(root().resolve(page), StandardCharsets.UTF_8);
    }

    private static List<String> urls(String html) {
        Matcher m = URL_ATTRIBUTE.matcher(html);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3));
        }
        return found;
    }

    private static void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).as("session").isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).as("cookie").isNull();
    }

    @Test
    void theBuildProducesTheManualInBothLanguages() throws IOException {
        List<String> pages = pages();
        assertThat(pages).contains("en/index.html", "de/index.html");

        Set<String> english = new TreeSet<>();
        Set<String> german = new TreeSet<>();
        for (String page : pages) {
            if (page.startsWith("en/")) {
                english.add(page.substring(3));
            } else if (page.startsWith("de/")) {
                german.add(page.substring(3));
            } else {
                throw new AssertionError("a page outside the language folders: " + page);
            }
        }
        assertThat(german).as("every page in both languages").isEqualTo(english);
        assertThat(english).hasSizeGreaterThan(1);
    }

    /**
     * The policy the manual is served under forbids inline script, style attributes and
     * other origins (spec 9.4): Hugo's highlighter or a theme would bring all three.
     */
    @Test
    void noPageNeedsMoreThanTheStrictPolicy() throws IOException {
        for (String page : pages()) {
            String html = read(page);
            assertThat(INLINE_SCRIPT.matcher(html).find()).as("inline script in %s", page).isFalse();
            assertThat(STYLE.matcher(html).find()).as("inline style in %s", page).isFalse();
            assertThat(EVENT_HANDLER.matcher(html).find()).as("event handler in %s", page).isFalse();
            for (String url : urls(html)) {
                assertThat(url).as("external URL in %s", page)
                        .doesNotStartWith("//").doesNotContain("://").doesNotStartWith("data:");
            }
        }
    }

    /**
     * Every link and every file a page loads resolves. Pages must stay flat at
     * /docs/{lang}/{page}.html for their ../../ paths to hold, and this is what
     * notices when they do not.
     */
    @Test
    void everyLinkAndAssetResolves() throws Exception {
        Set<String> checked = new HashSet<>();
        for (String page : pages()) {
            URI base = URI.create("/docs/" + page);
            for (String url : urls(read(page))) {
                if (url.startsWith("#")) {
                    continue;
                }
                String path = base.resolve(url).getPath();
                if (!checked.add(path)) {
                    continue;
                }
                MvcResult result = mvc.perform(get(path)).andReturn();
                assertThat(result.getResponse().getStatus()).as("%s on %s", url, page).isLessThan(400);
            }
        }
        assertThat(checked).contains("/vendor/tabler.min.css", "/js/theme.js", "/docs/de/index.html");
    }

    /** The policy itself is set by the sign-in chain, so OidcLoginTest checks it is sent. */
    @Test
    void pagesArePublicAndSessionless() throws Exception {
        for (String page : pages()) {
            MvcResult result = mvc.perform(get("/docs/" + page)).andExpect(status().isOk()).andReturn();
            assertNoSession(result);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "/docs, , , /docs/en/index.html",
            "/docs/, , , /docs/en/index.html",
            "/docs, , 'de-CH,de;q=0.9,en;q=0.8', /docs/de/index.html",
            "/docs, , 'fr-FR,fr;q=0.9', /docs/en/index.html",
            "/docs, de, 'en', /docs/de/index.html",
            // A language the manual does not have falls back to English, not to the header.
            "/docs, fr, 'de', /docs/en/index.html",
            "/docs, '../x', , /docs/en/index.html",
    })
    void theEntryPicksTheLanguage(String path, String lang, String acceptLanguage, String target) throws Exception {
        var request = get(path);
        if (lang != null) {
            request.param("lang", lang);
        }
        if (acceptLanguage != null) {
            request.header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage);
        }
        MvcResult result = mvc.perform(request)
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(target))
                .andReturn();
        assertNoSession(result);
    }

    @Test
    void theEntryAnswersHead() throws Exception {
        mvc.perform(head("/docs")).andExpect(status().isFound());
    }

    /**
     * Every help icon's target exists, as a page and as a heading id, in every language:
     * the ids are written into the Markdown ({#create}), so a renamed heading keeps them,
     * and a removed one fails here rather than as a link that opens the page's top.
     */
    @Test
    void everyHelpTopicExistsInEveryLanguage() throws IOException {
        for (ManualTopic topic : ManualTopic.values()) {
            for (String language : ManualHandler.LANGUAGES) {
                String page = language + "/" + topic.page() + ".html";
                assertThat(root().resolve(page)).as("%s: %s", topic, page).exists();
                if (topic.anchor() != null) {
                    assertThat(read(page)).as("%s: #%s in %s", topic, topic.anchor(), page)
                            .containsPattern("\\bid=\"?" + Pattern.quote(topic.anchor()) + "\"?[\\s>]");
                }
            }
        }
    }

    /** A screen's help icon opens its section, in the language shown, English for the rest. */
    @ParameterizedTest
    @CsvSource({
            "/jobs, en, /docs/en/jobs.html#shown-as",
            "/jobs, de, /docs/de/jobs.html#shown-as",
            "/jobs, fr, /docs/en/jobs.html#shown-as",
            "/jobs/create, de, /docs/de/jobs.html#create",
            "/feeds, en, /docs/en/feeds.html#feeds",
            "/settings, en, /docs/en/settings.html",
            "/, en, /docs/en/dashboard-activity.html#dashboard",
    })
    void aScreenLinksToItsSection(String screen, String lang, String target) throws Exception {
        String body = mvc.perform(get(screen).param("lang", lang)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("href=\"" + target + "\"");
    }

    /** Users is for admins, and the manual is for the people working on employers. */
    @Test
    void aScreenTheManualDoesNotCoverHasNoHelpIcon() throws Exception {
        String body = mvc.perform(get("/users")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("href=\"/docs/en/");
    }

    /** The user menu links to the manual in the language it is shown in. */
    @Test
    void theUserMenuOffersTheManual() throws Exception {
        String english = mvc.perform(get("/settings")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(english).contains("href=\"/docs?lang=en\"");

        String german = mvc.perform(get("/settings").param("lang", "de")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(german).contains("href=\"/docs?lang=de\"").contains("Benutzerhandbuch");
    }
}
