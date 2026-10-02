package org.letsemploy.ojobpub_publisher.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.web.view.WebserverSnippet;

/** The webserver configuration offered for a permalink (spec 7.23), with no Spring. */
class WebserverConfigTest {

    private static final String PATH = "/ojobpub/v1/permalink/5a5a5a5a-5a5a-4a5a-8a5a-5a5a5a5a5a5a/ojobpub.json";
    private static final String URL = "https://publisher.example.com" + PATH;

    private static String config(List<WebserverSnippet> snippets, String server, String kind) {
        return snippets.stream()
                .filter(s -> s.server().equals(server) && s.kind().equals(kind))
                .findFirst().orElseThrow().config();
    }

    @Test
    void everyServerHasARedirectAndAProxyAtTheWellKnownPath() {
        List<WebserverSnippet> snippets = WebserverConfig.snippets(URL);
        assertThat(snippets).extracting(s -> s.server() + "/" + s.kind()).containsExactly(
                "apache/redirect", "apache/proxy", "nginx/redirect", "nginx/proxy", "caddy/redirect", "caddy/proxy");
        assertThat(snippets).allSatisfy(s -> assertThat(s.config())
                .contains("/.well-known/ojobpub.json")
                .contains(PATH));
    }

    @Test
    void redirectsAreTemporaryAndPointAtThePermalink() {
        List<WebserverSnippet> snippets = WebserverConfig.snippets(URL);
        assertThat(config(snippets, "apache", "redirect")).contains("Redirect 302 \"/.well-known/ojobpub.json\" \"" + URL + "\"");
        assertThat(config(snippets, "nginx", "redirect")).contains("return 302 " + URL + ";");
        assertThat(config(snippets, "caddy", "redirect")).contains("redir /.well-known/ojobpub.json " + URL + " 302");
    }

    @Test
    void proxiesOverHttpsSendTheNameOfThePublisher() {
        List<WebserverSnippet> snippets = WebserverConfig.snippets(URL);
        assertThat(config(snippets, "apache", "proxy")).contains("SSLProxyEngine on", "ProxyPass \"/.well-known/ojobpub.json\" \"" + URL + "\"");
        assertThat(config(snippets, "nginx", "proxy")).contains("proxy_pass " + URL + ";", "proxy_ssl_server_name on;");
        // Caddy's upstream takes no path, so the path is rewritten first.
        assertThat(config(snippets, "caddy", "proxy")).contains(
                "rewrite * " + PATH, "reverse_proxy https://publisher.example.com {", "header_up Host {upstream_hostport}");
    }

    @Test
    void plainHttpNeedsNoTlsSettingsAndKeepsThePort() {
        List<WebserverSnippet> snippets = WebserverConfig.snippets("http://localhost:8080" + PATH);
        assertThat(config(snippets, "apache", "proxy")).doesNotContain("SSLProxyEngine", "mod_ssl");
        assertThat(config(snippets, "nginx", "proxy")).doesNotContain("proxy_ssl_server_name")
                .contains("proxy_pass http://localhost:8080" + PATH + ";");
        assertThat(config(snippets, "caddy", "proxy")).contains("reverse_proxy http://localhost:8080 {");
    }

    @Test
    void linesCountsTheWholeSnippet() {
        assertThat(new WebserverSnippet("caddy", "redirect", "a\nb\nc\n").lines()).isEqualTo(3);
    }
}
