package cs.youtrade.payment.service.trybit;

import com.google.gson.Gson;
import cs.youtrade.payment.controller.PaymentStatus;
import cs.youtrade.payment.controller.TopUpType;
import cs.youtrade.payment.controller.dto.UserPaymentCreateDto;
import cs.youtrade.payment.controller.dto.UserPaymentProcedureDto;
import cs.youtrade.payment.controller.trybit.dto.TrybitNotificationDto;
import cs.youtrade.payment.util.gson.GsonConfig;
import cs.youtrade.payment.util.trybit.TrybitCommunicationEndpoint;
import cs.youtrade.payment.util.trybit.dto.TrybitInvoice;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static cs.youtrade.payment.util.trybit.TrybitSignatureProcessor.validateSignature;

@Log4j2
@Service
public class TrybitPaymentService {
    public enum NotificationResult {
        PROCESSED,
        EMPTY_ORDER_ID
    }

    public enum InvoiceProcessingMode {
        ALL_STATUSES,
        CANCELLATIONS_ONLY
    }

    private static final Gson GSON = GsonConfig.createGson();
    private static final Map<String, PaymentStatus> INVOICE_STATUSES = Map.of(
            "paid", PaymentStatus.COMPLETED,
            "overpaid", PaymentStatus.COMPLETED,
            "created", PaymentStatus.PENDING,
            "partial", PaymentStatus.PENDING,
            "canceled", PaymentStatus.CANCELLED,
            "failed", PaymentStatus.CANCELLED
    );
    private final TrybitCommunicationEndpoint endpoint;
    private final RedisTemplate<String, String> redisTemplate;
    private final String redisStreamName;
    private final String secret;
    private final ConcurrentMap<String, Instant> publishedCancellations = new ConcurrentHashMap<>();

    public TrybitPaymentService(
            TrybitCommunicationEndpoint endpoint,
            RedisTemplate<String, String> redisTemplate,
            @Value("${youtrade.payment.redis.stream.name}") String redisStreamName,
            @Value("${youtrade.payment.trybit.secret}") String secret
    ) {
        this.endpoint = endpoint;
        this.redisTemplate = redisTemplate;
        this.redisStreamName = redisStreamName;
        this.secret = secret;
    }

    public UserPaymentCreateDto createPayment(String id, BigDecimal amount) {
        if (id == null || id.isBlank() || amount == null || amount.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty id and positive amount are required");
        }
        requireSecret();
        var invoice = endpoint.createPayment(id, amount);
        if (invoice.link() == null || invoice.link().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TryBit did not return a payment link");
        }
        return UserPaymentCreateDto.builder()
                .id(id).amount(amount).type(TopUpType.TRYBIT).url(invoice.link()).build();
    }

    public NotificationResult proceed(TrybitNotificationDto notification) {
        validateNotification(notification);
        if (isEmptyOrderId(notification.orderId())) {
            log.info("Ignoring TryBit notification: order_id is empty");
            return NotificationResult.EMPTY_ORDER_ID;
        }
        var invoice = loadVerifiedInvoice(notification);
        processInvoice(invoice, InvoiceProcessingMode.ALL_STATUSES);
        return NotificationResult.PROCESSED;
    }

    /** Shared processing path for invoices returned by authenticated TryBit API calls. */
    public void processInvoice(TrybitInvoice invoice, InvoiceProcessingMode mode) {
        if (!shouldProcessInvoice(invoice, mode)) {
            return;
        }
        validateInvoice(invoice);
        PaymentStatus status = decidePaymentStatus(invoice.status());
        publishInvoice(invoice, status);
    }

    private void validateNotification(TrybitNotificationDto notification) {
        requireSecret();
        if (!validateSignature(notification.token(), secret)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid TryBit signature");
        }
        if (!"success".equals(notification.status()) || notification.invoiceId() == null
                || notification.invoiceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TryBit notification");
        }
    }

    private TrybitInvoice loadVerifiedInvoice(TrybitNotificationDto notification) {
        // The JWT does not guarantee that the remaining POSTBACK fields are signed.
        // Retrieve authoritative order, amount and status with our own API credentials.
        String uuid = notification.invoiceId().startsWith("INV-")
                ? notification.invoiceId() : "INV-" + notification.invoiceId();
        var invoice = endpoint.getInvoice(uuid);
        if (!uuid.equals(invoice.uuid()) || !notification.orderId().equals(invoice.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TryBit invoice does not match the order");
        }
        return invoice;
    }

    private boolean shouldProcessInvoice(TrybitInvoice invoice, InvoiceProcessingMode mode) {
        if (invoice == null || isEmptyOrderId(invoice.orderId())) {
            return false;
        }
        return mode == InvoiceProcessingMode.ALL_STATUSES || (invoice.status() != null
                && INVOICE_STATUSES.get(invoice.status()) == PaymentStatus.CANCELLED);
    }

    private void validateInvoice(TrybitInvoice invoice) {
        if (invoice.uuid() == null || invoice.uuid().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TryBit invoice has no valid UUID");
        }
        if (invoice.amountUsd() == null || invoice.amountUsd().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TryBit invoice has no valid USD amount");
        }
    }

    private PaymentStatus decidePaymentStatus(String providerStatus) {
        PaymentStatus status = providerStatus == null ? null : INVOICE_STATUSES.get(providerStatus);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unknown TryBit invoice status");
        }
        return status;
    }

    private void publishInvoice(TrybitInvoice invoice, PaymentStatus status) {
        if (status == PaymentStatus.CANCELLED) {
            publishCancellation(invoice);
        } else {
            publishEvent(invoice, status);
        }
    }

    private void publishCancellation(TrybitInvoice invoice) {
        // The cache only deduplicates cancellation events in this process. The Redis
        // consumer must remain idempotent across restarts and service instances.
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(3));
        publishedCancellations.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        publishedCancellations.computeIfAbsent(invoice.uuid(), uuid -> {
            publishEvent(invoice, PaymentStatus.CANCELLED);
            log.info("Published TryBit cancellation for invoice {}", uuid);
            return now;
        });
    }

    private void publishEvent(TrybitInvoice invoice, PaymentStatus status) {
        var event = UserPaymentProcedureDto.builder()
                .id(invoice.orderId()).amount(invoice.amountUsd()).type(TopUpType.TRYBIT).status(status).build();
        MapRecord<String, String, String> record = MapRecord.create(redisStreamName,
                Map.of("payload", GSON.toJson(event)));
        if (redisTemplate.opsForStream().add(record) == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Payment event was not published");
        }
    }

    private static boolean isEmptyOrderId(String orderId) {
        return orderId == null || orderId.isBlank() || "None".equalsIgnoreCase(orderId.strip())
                || "null".equalsIgnoreCase(orderId.strip());
    }

    private void requireSecret() {
        if (secret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TryBit secret key is not configured");
        }
    }
}
