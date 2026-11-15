package cs.youtrade.payment.trybit;

import com.fasterxml.jackson.databind.ObjectMapper;
import cs.youtrade.payment.controller.InnerPaymentController;
import cs.youtrade.payment.controller.trybit.TrybitController;
import cs.youtrade.payment.service.trybit.TrybitPaymentService;
import cs.youtrade.payment.util.heleket.HeleketCommunicationEndpoint;
import cs.youtrade.payment.util.trybit.TrybitCommunicationEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfSystemProperty(named = "trybit.test.base-url", matches = ".+")
class TrybitApiContractTest {
    @Test
    @SuppressWarnings("unchecked")
    void realHttpClientAndWebhookUseContractAndHandleUpstreamFailures() throws Exception {
        String baseUrl = System.getProperty("trybit.test.base-url");
        var redis = (RedisTemplate<String, String>) mock(RedisTemplate.class);
        var stream = (StreamOperations<String, String, String>) mock(StreamOperations.class);
        when(redis.<String, String>opsForStream()).thenReturn(stream);
        when(stream.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        try (var endpoint = new AutoClosingEndpoint(baseUrl)) {
            var service = new TrybitPaymentService(endpoint.client, redis, "youtrade-payment", TrybitTestToken.SECRET);
            var mvc = MockMvcBuilders.standaloneSetup(new TrybitController(service),
                    new InnerPaymentController(mock(HeleketCommunicationEndpoint.class), service)).build();

            mvc.perform(post("/payment/create/trybit").param("id", "contract-order").param("amount", "100.25"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(100.25))
                    .andExpect(jsonPath("$.url").value("https://pay.trybit.com/89UX09KA"));
            var today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
            var listed = endpoint.client.listInvoices(today.minusDays(1), today, 0, 100);
            org.junit.jupiter.api.Assertions.assertEquals(1, listed.size());
            org.junit.jupiter.api.Assertions.assertEquals("contract-order", listed.getFirst().orderId());
            org.junit.jupiter.api.Assertions.assertEquals("created", listed.getFirst().status());
            org.junit.jupiter.api.Assertions.assertTrue(endpoint.client.listInvoices(today.minusDays(1), today, 100, 100).isEmpty());
            control(baseUrl, "{\"status\":\"paid\"}");
            String notification = new ObjectMapper().writeValueAsString(Map.of("status", "success",
                    "invoice_id", "89UX09KA", "order_id", "contract-order", "token", TrybitTestToken.valid()));
            mvc.perform(post("/webhook/trybit/notification").contentType(MediaType.APPLICATION_JSON).content(notification))
                    .andExpect(status().isOk()).andExpect(content().string("true"));
            verify(stream).add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>argThat(record -> {
                var event = com.google.gson.JsonParser.parseString(record.getValue().get("payload")).getAsJsonObject();
                return event.get("amount").getAsBigDecimal().compareTo(new java.math.BigDecimal("100.25")) == 0
                        && event.get("status").getAsString().equals("COMPLETED");
            }));

            clearInvocations(redis, stream);
            for (String failure : new String[]{"upstream", "invalid-json", "api-error"}) {
                control(baseUrl, "{\"failure\":\"" + failure + "\"}");
                mvc.perform(post("/webhook/trybit/notification").contentType(MediaType.APPLICATION_JSON).content(notification))
                        .andExpect(status().isBadGateway());
            }
            verifyNoInteractions(redis, stream);
        }
    }

    private void control(String baseUrl, String json) throws Exception {
        URI uri = URI.create(baseUrl.substring(0, baseUrl.length() - "/v2".length()) + "/__control");
        var request = HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        try (var client = HttpClient.newHttpClient()) {
            org.junit.jupiter.api.Assertions.assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }

    private static final class AutoClosingEndpoint implements AutoCloseable {
        final TrybitCommunicationEndpoint client;

        AutoClosingEndpoint(String baseUrl) {
            client = new TrybitCommunicationEndpoint(baseUrl, "test-api-key", "test-shop");
        }

        @Override
        public void close() throws Exception {
            client.close();
        }
    }
}
