package com.example.shoppingagent.websearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Uses a trimmed copy of a REAL SerpApi Google Shopping response ("olive oil"), including its quirks. */
class SerpApiClientTest {
    static final String BASE = "https://serpapi.test";
    static final String KEY = "secret-key-123";

    static final String SAMPLE = """
        {"shopping_results":[
          {"position":1,"title":"Pompeian Extra Virgin Olive Oil Smooth","product_id":"1",
           "product_link":"https://www.google.com/search?ibp=oshop&q=olive oil",
           "immersive_product_page_token":"HUGE-TOKEN-THAT-MUST-NOT-BE-FORWARDED",
           "source":"Walmart","price":"$12.97","extracted_price":12.97,
           "old_price":"$0.41/fl oz","extracted_old_price":0.41,
           "rating":4.6,"reviews":2300,"extensions":["Nearby, 3 mi"],
           "thumbnail":"https://example/t.png","delivery":"Delivery within 1h $20"},
          {"position":2,"title":"California Olive Ranch Extra Virgin Olive Oil","product_id":"2",
           "product_link":"https://www.google.com/search?ibp=oshop&q=2",
           "source":"H-E-B","price":"$10.94","extracted_price":10.94,
           "rating":4.7,"reviews":995,"extensions":["Nearby, 2 mi"]},
          {"position":3,"title":"Third item","source":"Target"}
        ]}
        """;

    MockRestServiceServer server;
    RestClient.Builder builder;

    SerpApiClient client(int maxResults, String key) {
        return new SerpApiClient(builder.build(), new SerpApiProperties(key, BASE, maxResults));
    }

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void parsesRealResponseAndSendsExpectedRequest() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/search.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("engine", "google_shopping"))
                .andExpect(queryParam("q", "olive%20oil")) // the space is URL-encoded
                .andExpect(queryParam("api_key", KEY))
                .andRespond(withSuccess(SAMPLE, MediaType.APPLICATION_JSON));

        List<WebProduct> result = client(5, KEY).searchShopping("olive oil");

        assertThat(result).hasSize(3);
        WebProduct first = result.get(0);
        assertThat(first.title()).isEqualTo("Pompeian Extra Virgin Olive Oil Smooth");
        assertThat(first.price()).isEqualByComparingTo(new BigDecimal("12.97"));
        assertThat(first.store()).isEqualTo("Walmart");
        assertThat(first.rating()).isEqualTo(4.6);
        assertThat(first.reviews()).isEqualTo(2300);
        assertThat(first.notes()).containsExactly("Nearby, 3 mi", "Delivery within 1h $20");
        // Fields missing from an item become null / empty instead of crashing.
        assertThat(result.get(2).price()).isNull();
        assertThat(result.get(2).rating()).isNull();
        // The bogus "old_price" ($0.41/fl oz) and the huge token never reach our model.
        assertThat(result.toString()).doesNotContain("0.41").doesNotContain("HUGE-TOKEN");
    }

    @Test
    void limitsNumberOfResults() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withSuccess(SAMPLE, MediaType.APPLICATION_JSON));
        assertThat(client(2, KEY).searchShopping("olive oil")).hasSize(2);
    }

    @Test
    void identicalSearchesAreServedFromCache() {
        server.expect(ExpectedCount.once(), requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withSuccess(SAMPLE, MediaType.APPLICATION_JSON));
        SerpApiClient c = client(5, KEY);

        c.searchShopping("Olive Oil");
        c.searchShopping("  olive oil ");   // same search, different case/spacing: no second HTTP call

        server.verify();
    }

    @Test
    void noResultsErrorBecomesEmptyList() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withSuccess("{\"error\":\"Google hasn't returned any results for this query.\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(client(5, KEY).searchShopping("zzzzqqq")).isEmpty();
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        assertThatThrownBy(() -> client(5, "").searchShopping("tv"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SERPAPI_API_KEY");
    }

    @Test
    void httpErrorNeverLeaksTheKey() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client(5, KEY).searchShopping("tv"))
                .hasMessageContaining("401").hasMessageNotContaining(KEY);
    }

    @Test
    void networkErrorNeverLeaksTheKey() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(request -> { throw new IOException("connect failed: " + request.getURI()); });
        assertThatThrownBy(() -> client(5, KEY).searchShopping("tv"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(KEY);
    }
}
