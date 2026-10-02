package org.letsemploy.ojobpub_publisher.feed;

import java.net.URI;
import java.util.List;
import org.letsemploy.ojobpub_publisher.web.view.WebserverSnippet;

/**
 * Webserver configuration that puts a permalink under the employer's own domain
 * (spec 5.5, 7.23): for Apache, nginx and Caddy, a redirect and a reverse proxy.
 *
 * <p>The redirect is the simpler of the two, but a consumer that does not follow
 * redirects fails on it, which is why the publisher itself serves rather than
 * redirects. The proxy serves the document, headers included, under the
 * employer's domain.
 */
public final class WebserverConfig {

    /** Where the snippets place the document on the employer's website. */
    public static final String PATH = "/.well-known/ojobpub.json";

    private WebserverConfig() {
    }

    /** All six snippets for a permalink URL: per server, the redirect, then the proxy. */
    public static List<WebserverSnippet> snippets(String permalinkUrl) {
        URI uri = URI.create(permalinkUrl);
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        String hostPort = uri.getPort() == -1 ? uri.getHost() : uri.getHost() + ":" + uri.getPort();
        String origin = uri.getScheme() + "://" + hostPort;
        String path = uri.getRawPath();
        return List.of(
                new WebserverSnippet("apache", "redirect", """
                        # Apache, in the site's <VirtualHost>; needs mod_alias.
                        # Any other path on your site works as well.
                        Redirect 302 "%s" "%s"
                        """.formatted(PATH, permalinkUrl)),
                new WebserverSnippet("apache", "proxy", """
                        # Apache, in the site's <VirtualHost>; needs mod_proxy%s.
                        # Any other path on your site works as well.
                        %sProxyPass "%s" "%s"
                        ProxyPassReverse "%s" "%s"
                        """.formatted(https ? ", mod_proxy_http and mod_ssl" : " and mod_proxy_http", https ? "SSLProxyEngine on\n" : "",
                        PATH, permalinkUrl, PATH, permalinkUrl)),
                new WebserverSnippet("nginx", "redirect", """
                        # nginx, in the site's server block.
                        # Any other path on your site works as well.
                        location = %s {
                            return 302 %s;
                        }
                        """.formatted(PATH, permalinkUrl)),
                new WebserverSnippet("nginx", "proxy", """
                        # nginx, in the site's server block.
                        # Any other path on your site works as well.
                        location = %s {
                            proxy_pass %s;
                        %s}
                        """.formatted(PATH, permalinkUrl, https ? "    proxy_ssl_server_name on;\n" : "")),
                new WebserverSnippet("caddy", "redirect", """
                        # Caddy, in the site's block of the Caddyfile.
                        # Any other path on your site works as well.
                        redir %s %s 302
                        """.formatted(PATH, permalinkUrl)),
                new WebserverSnippet("caddy", "proxy", """
                        # Caddy, in the site's block of the Caddyfile.
                        # Any other path on your site works as well.
                        handle %s {
                            rewrite * %s
                            reverse_proxy %s {
                                header_up Host {upstream_hostport}
                            }
                        }
                        """.formatted(PATH, path, origin)));
    }
}
