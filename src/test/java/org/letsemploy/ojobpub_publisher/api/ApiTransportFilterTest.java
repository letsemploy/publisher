package org.letsemploy.ojobpub_publisher.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The body limit and the request budget of spec 11.6, below Spring. A chunked
 * body declares no length, so the limit must count what arrives rather than
 * trust a header that is not there.
 */
class ApiTransportFilterTest {

    private static final int LIMIT = 64;

    /** A request sent chunked: a body, but no Content-Length. */
    private static MockHttpServletRequest chunked(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static ApiTransportFilter filter() {
        return new ApiTransportFilter(new ApiRateLimiter(600, Duration.ofMinutes(1)), LIMIT);
    }

    @Test
    void anOversizedChunkedBodyIsRefused() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(chunked("x".repeat(LIMIT + 1)), response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        assertThat(chain.getRequest()).as("never reaches the API").isNull();
    }

    /** Within the limit it is passed on, with the body that was read still readable. */
    @Test
    void aChunkedBodyWithinTheLimitIsPassedOnWhole() throws Exception {
        String body = "{\"query\":\"{ employer { name } }\"}";
        MockFilterChain chain = new MockFilterChain();

        filter().doFilter(chunked(body), new MockHttpServletResponse(), chain);

        HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
        assertThat(passed).isNotNull();
        assertThat(passed.getContentLengthLong()).isEqualTo(body.length());
        assertThat(new String(passed.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo(body);
    }

    /** 0 is unlimited, as for every other limit, not "refuse every call". */
    @Test
    void aRateLimitOfZeroIsUnlimited() {
        ApiRateLimiter unlimited = new ApiRateLimiter(0, Duration.ofMinutes(1));
        UUID token = UUID.randomUUID();
        for (int i = 0; i < 1000; i++) {
            assertThat(unlimited.check(token).allowed()).isTrue();
        }
    }
}
