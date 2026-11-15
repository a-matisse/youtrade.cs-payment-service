package cs.youtrade.payment.service.trybit;

import cs.youtrade.payment.util.trybit.TrybitCommunicationEndpoint;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static cs.youtrade.payment.service.trybit.TrybitPaymentService.InvoiceProcessingMode.CANCELLATIONS_ONLY;

@Log4j2
@Component
public class TrybitCancellationPoller {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 100;
    private final TrybitCommunicationEndpoint endpoint;
    private final TrybitPaymentService paymentService;
    private final boolean enabled;
    private final int lookbackDays;

    public TrybitCancellationPoller(
            TrybitCommunicationEndpoint endpoint,
            TrybitPaymentService paymentService,
            @Value("${youtrade.payment.trybit.poll-enabled:true}") boolean enabled,
            @Value("${youtrade.payment.trybit.poll-lookback-days:2}") int lookbackDays
    ) {
        if (lookbackDays < 1 || lookbackDays > 2) {
            throw new IllegalArgumentException("TryBit cancellation polling lookback must be 1 or 2 days");
        }
        this.endpoint = endpoint;
        this.paymentService = paymentService;
        this.enabled = enabled;
        this.lookbackDays = lookbackDays;
    }

    @Scheduled(fixedDelayString = "${youtrade.payment.trybit.poll-interval-ms:60000}",
            initialDelayString = "${youtrade.payment.trybit.poll-initial-delay-ms:10000}")
    public void poll() {
        if (!enabled || !endpoint.isConfigured()) {
            return;
        }
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusDays(lookbackDays - 1L);
        try {
            for (int page = 0; page < MAX_PAGES; page++) {
                var invoices = endpoint.listInvoices(start, end, page * PAGE_SIZE, PAGE_SIZE);
                for (var invoice : invoices) {
                    try {
                        paymentService.processInvoice(invoice, CANCELLATIONS_ONLY);
                    } catch (RuntimeException e) {
                        log.warn("TryBit cancellation processing failed; will retry on the next poll ({})",
                                e.getClass().getSimpleName());
                    }
                }
                if (invoices.size() < PAGE_SIZE) {
                    return;
                }
            }
            log.warn("TryBit cancellation poll reached its page limit; some invoices may not have been checked");
        } catch (RuntimeException e) {
            log.warn("TryBit cancellation polling failed; will retry on the next poll ({})",
                    e.getClass().getSimpleName());
        }
    }
}
