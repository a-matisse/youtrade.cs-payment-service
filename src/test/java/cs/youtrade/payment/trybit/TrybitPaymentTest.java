package cs.youtrade.payment.trybit;

import com.fasterxml.jackson.databind.ObjectMapper;
import cs.youtrade.payment.controller.InnerPaymentController;
import cs.youtrade.payment.controller.trybit.TrybitController;
import cs.youtrade.payment.service.trybit.TrybitPaymentService;
import cs.youtrade.payment.util.heleket.HeleketCommunicationEndpoint;
import cs.youtrade.payment.util.trybit.TrybitCommunicationEndpoint;
import cs.youtrade.payment.util.trybit.dto.TrybitInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrybitPaymentTest {
    private static final String CALLBACK = "/webhook/trybit/notification";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private TrybitCommunicationEndpoint endpoint;
    private RedisTemplate<String, String> redis;
    private StreamOperations<String, String, String> stream;
    private MockMvc mvc;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        endpoint = mock(TrybitCommunicationEndpoint.class);
        redis = mock(RedisTemplate.class);
        stream = mock(StreamOperations.class);
        when(redis.<String, String>opsForStream()).thenReturn(stream);
        when(stream.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        var service = new TrybitPaymentService(endpoint, redis, "youtrade-payment", TrybitTestToken.SECRET);
        mvc = MockMvcBuilders.standaloneSetup(new TrybitController(service),
                new InnerPaymentController(mock(HeleketCommunicationEndpoint.class), service)).build();
    }

    @Test
    void createsUsdPaymentWithSamePublicResponseAsHeleket() throws Exception {
        when(endpoint.createPayment("order-1", new BigDecimal("100.25")))
                .thenReturn(new TrybitInvoice("INV-89UX09KA", "https://pay.trybit.com/89UX09KA",
                        "created", null, new BigDecimal("100.25")));
        mvc.perform(post("/payment/create/trybit").param("id", "order-1").param("amount", "100.25"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("order-1"))
                .andExpect(jsonPath("$.amount").value(100.25)).andExpect(jsonPath("$.type").value("TRYBIT"))
                .andExpect(jsonPath("$.url").value("https://pay.trybit.com/89UX09KA"));
        verifyNoInteractions(redis);
    }

    @Test
    void rejectsInvalidCreateRequestBeforeCallingProvider() throws Exception {
        mvc.perform(post("/payment/create/trybit").param("id", "order-1").param("amount", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/payment/create/trybit").param("id", " ").param("amount", "10"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(endpoint, redis);
    }

    @ParameterizedTest
    @CsvSource({"paid,COMPLETED", "overpaid,COMPLETED", "partial,PENDING", "created,PENDING", "canceled,CANCELLED"})
    void verifiesInvoiceAndPublishesAuthoritativeUsdAmount(String providerStatus, String expectedStatus) throws Exception {
        when(endpoint.getInvoice("INV-89UX09KA")).thenReturn(invoice(providerStatus, "order-1"));
        mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON).content(notification()))
                .andExpect(status().isOk()).andExpect(content().string("true"));
        verify(stream).add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>argThat(record -> {
            assertEquals("youtrade-payment", record.getStream());
            var event = com.google.gson.JsonParser.parseString(record.getValue().get("payload")).getAsJsonObject();
            assertEquals("order-1", event.get("id").getAsString());
            assertEquals(new BigDecimal("100.25"), event.get("amount").getAsBigDecimal());
            assertEquals("TRYBIT", event.get("type").getAsString());
            assertEquals(expectedStatus, event.get("status").getAsString());
            return true;
        }));
    }

    @Test
    void acceptsFormPostbackAndTrailingSlash() throws Exception {
        when(endpoint.getInvoice("INV-89UX09KA")).thenReturn(invoice("paid", "order-1"));
        mvc.perform(post(CALLBACK + "/").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("status", "success").param("invoice_id", "89UX09KA")
                        .param("order_id", "order-1").param("token", TrybitTestToken.valid()))
                .andExpect(status().isOk());
        verify(stream).add(any(MapRecord.class));
    }

    @Test
    void rejectsMissingOrForgedSignatureWithoutApiOrRedisAccess() throws Exception {
        for (String token : new String[]{"", "forged"}) {
            mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON)
                            .content(MAPPER.writeValueAsString(Map.of("status", "success", "invoice_id", "89UX09KA",
                                    "order_id", "order-1", "token", token))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(endpoint, redis);
    }

    @Test
    void rejectsOrderMismatchAndUnknownStatusWithoutPublishing() throws Exception {
        when(endpoint.getInvoice("INV-89UX09KA")).thenReturn(invoice("paid", "another-order"));
        mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON).content(notification()))
                .andExpect(status().isBadRequest());
        when(endpoint.getInvoice("INV-89UX09KA")).thenReturn(invoice("unknown", "order-1"));
        mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON).content(notification()))
                .andExpect(status().isBadGateway());
        verifyNoInteractions(redis);
    }

    @Test
    void doesNotAcknowledgePaymentIfRedisWriteFails() throws Exception {
        when(endpoint.getInvoice("INV-89UX09KA")).thenReturn(invoice("paid", "order-1"));
        when(stream.add(any(MapRecord.class))).thenReturn(null);
        mvc.perform(post(CALLBACK).contentType(MediaType.APPLICATION_JSON).content(notification()))
                .andExpect(status().isServiceUnavailable());
    }

    private TrybitInvoice invoice(String status, String orderId) {
        return new TrybitInvoice("INV-89UX09KA", null, status, orderId, new BigDecimal("100.25"));
    }

    private String notification() throws Exception {
        // invoice_info is untrusted, and amount_crypto is deliberately not a USD amount.
        return MAPPER.writeValueAsString(Map.of("status", "success", "invoice_id", "89UX09KA",
                "order_id", "order-1", "token", TrybitTestToken.valid(), "amount_crypto", 0.034187,
                "invoice_info", Map.of("status", "paid", "amount_usd", 999999)));
    }
}
