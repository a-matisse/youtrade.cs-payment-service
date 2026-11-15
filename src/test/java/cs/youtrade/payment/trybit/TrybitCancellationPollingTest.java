package cs.youtrade.payment.trybit;

import cs.youtrade.payment.controller.trybit.dto.TrybitNotificationDto;
import cs.youtrade.payment.service.trybit.TrybitCancellationPoller;
import cs.youtrade.payment.service.trybit.TrybitPaymentService;
import cs.youtrade.payment.util.trybit.TrybitCommunicationEndpoint;
import cs.youtrade.payment.util.trybit.dto.TrybitInvoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static cs.youtrade.payment.service.trybit.TrybitPaymentService.InvoiceProcessingMode.CANCELLATIONS_ONLY;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TrybitCancellationPollingTest {
    private TrybitCommunicationEndpoint endpoint;
    private RedisTemplate<String, String> redis;
    private StreamOperations<String, String, String> stream;
    private TrybitPaymentService service;
    private TrybitCancellationPoller poller;

    @BeforeEach
    void setUp() {
        endpoint = mock(TrybitCommunicationEndpoint.class);
        redis = mock(RedisTemplate.class);
        stream = mock(StreamOperations.class);
        when(endpoint.isConfigured()).thenReturn(true);
        when(redis.<String, String>opsForStream()).thenReturn(stream);
        when(stream.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        service = new TrybitPaymentService(endpoint, redis, "youtrade-payment", TrybitTestToken.SECRET);
        poller = new TrybitCancellationPoller(endpoint, service, true, 2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"canceled", "failed"})
    void publishesCancellationWithoutWebhookAndDoesNotRepeatOnNextPoll(String status) {
        page(List.of(invoice("INV-UZRHPWD9", status, "order-1")));
        poller.poll();
        poller.poll();
        verify(stream).add(org.mockito.ArgumentMatchers.<MapRecord<String, String, String>>argThat(record -> {
            var event = com.google.gson.JsonParser.parseString(record.getValue().get("payload")).getAsJsonObject();
            assertEquals("youtrade-payment", record.getStream());
            assertEquals("order-1", event.get("id").getAsString());
            assertEquals("TRYBIT", event.get("type").getAsString());
            assertEquals("CANCELLED", event.get("status").getAsString());
            assertEquals(new BigDecimal("100.00"), event.get("amount").getAsBigDecimal());
            return true;
        }));
    }

    @Test
    void skipsInvoicesWithoutOrderAndDoesNotReplayPaidOrPendingInvoices() {
        page(List.of(invoice("INV-1", "canceled", null), invoice("INV-2", "canceled", "None"),
                invoice("INV-3", "canceled", " "), invoice("INV-4", "paid", "order-1"),
                invoice("INV-5", "overpaid", "order-2"), invoice("INV-6", "partial", "order-3"),
                invoice("INV-7", "created", "order-4")));
        poller.poll();
        verifyNoInteractions(redis);
    }

    @Test
    void retriesRedisFailureInsteadOfRememberingUnpublishedCancellation() {
        page(List.of(invoice("INV-UZRHPWD9", "canceled", "order-1")));
        when(stream.add(any(MapRecord.class))).thenReturn(null, RecordId.of("1-0"));
        poller.poll();
        poller.poll();
        poller.poll();
        verify(stream, times(2)).add(any(MapRecord.class));
    }

    @Test
    void continuesAfterMalformedInvoiceAndRetriesApiFailureNextTime() {
        when(endpoint.listInvoices(any(), any(), eq(0), eq(100)))
                .thenThrow(new IllegalStateException("upstream unavailable"))
                .thenReturn(List.of(new TrybitInvoice("INV-BAD", null, "canceled", "bad-order", null),
                        invoice("INV-UZRHPWD9", "canceled", "order-1")));
        poller.poll();
        poller.poll();
        verify(stream).add(any(MapRecord.class));
    }

    @Test
    void pagesThroughProviderInvoicesUsingUtcDates() {
        List<TrybitInvoice> first = IntStream.range(0, 100)
                .mapToObj(i -> invoice("INV-PENDING" + i, "created", "order-" + i)).toList();
        when(endpoint.listInvoices(any(), any(), eq(0), eq(100))).thenReturn(first);
        when(endpoint.listInvoices(any(), any(), eq(100), eq(100)))
                .thenReturn(List.of(invoice("INV-UZRHPWD9", "canceled", "order-final")));
        poller.poll();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        verify(endpoint).listInvoices(today.minusDays(1), today, 0, 100);
        verify(endpoint).listInvoices(today.minusDays(1), today, 100, 100);
        verify(stream).add(any(MapRecord.class));
    }

    @Test
    void sharesCancellationDeduplicationWithWebhook() throws Exception {
        var canceled = invoice("INV-UZRHPWD9", "canceled", "order-1");
        page(List.of(canceled));
        when(endpoint.getInvoice("INV-UZRHPWD9")).thenReturn(canceled);
        var notification = new TrybitNotificationDto("success", "UZRHPWD9", "order-1", TrybitTestToken.valid());
        poller.poll();
        assertEquals(TrybitPaymentService.NotificationResult.PROCESSED, service.proceed(notification));
        verify(stream).add(any(MapRecord.class));
    }

    @Test
    void doesNotRepeatWebhookCancellationDuringPolling() throws Exception {
        var canceled = invoice("INV-UZRHPWD9", "canceled", "order-1");
        page(List.of(canceled));
        when(endpoint.getInvoice("INV-UZRHPWD9")).thenReturn(canceled);
        service.proceed(new TrybitNotificationDto("success", "UZRHPWD9", "order-1", TrybitTestToken.valid()));
        poller.poll();
        verify(stream).add(any(MapRecord.class));
    }

    @Test
    void concurrentCancellationProcessingPublishesOnlyOnce() throws Exception {
        var canceled = invoice("INV-UZRHPWD9", "canceled", "order-1");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = IntStream.range(0, 12).<java.util.concurrent.Callable<Void>>mapToObj(i -> () -> {
                service.processInvoice(canceled, CANCELLATIONS_ONLY);
                return null;
            }).toList();
            for (var result : executor.invokeAll(jobs)) {
                result.get();
            }
        }
        verify(stream).add(any(MapRecord.class));
    }

    @Test
    void skipsPollingWhenDisabledOrCredentialsMissing() {
        new TrybitCancellationPoller(endpoint, service, false, 2).poll();
        verifyNoInteractions(endpoint, redis);
        when(endpoint.isConfigured()).thenReturn(false);
        poller.poll();
        verify(endpoint, never()).listInvoices(any(), any(), anyInt(), anyInt());
        verifyNoInteractions(redis);
    }

    private void page(List<TrybitInvoice> invoices) {
        when(endpoint.listInvoices(any(), any(), eq(0), eq(100))).thenReturn(invoices);
    }

    private TrybitInvoice invoice(String uuid, String status, String orderId) {
        return new TrybitInvoice(uuid, null, status, orderId, new BigDecimal("100.00"));
    }
}
