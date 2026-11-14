package cs.youtrade.payment.trybit;

import cs.youtrade.payment.util.trybit.TrybitCommunicationEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "trybit.live-check", matches = "true")
class TrybitLiveApiTest {
    @Test
    void createsOneUnpaidUsdInvoiceAndReadsItBack() throws Exception {
        // Explicitly opt-in: creates an invoice at the real provider; never pays it.
        Properties settings = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) {
            settings.load(reader);
        }
        String apiKey = setting(settings, "TRYBIT_API_KEY");
        String shopId = setting(settings, "TRYBIT_SHOP_ID");
        String orderId = "trybit-check-" + UUID.randomUUID();
        var endpoint = new TrybitCommunicationEndpoint("https://api.trybit.com/v2", apiKey, shopId);
        try {
            var created = endpoint.createPayment(orderId, new BigDecimal("1.00"));
            assertNotNull(created.uuid());
            assertNotNull(created.link());
            var retrieved = endpoint.getInvoice(created.uuid());
            assertEquals(orderId, retrieved.orderId());
            assertEquals("created", retrieved.status());
            assertNotNull(retrieved.amountUsd());
            assertEquals(0, new BigDecimal("1.00").compareTo(retrieved.amountUsd()));
            // Invoice IDs are safe to report; credentials and full provider responses are not.
            System.out.println("TryBit live check: created and read unpaid USD 1.00 invoice " + created.uuid());
        } finally {
            endpoint.close();
        }
    }

    private String setting(Properties settings, String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = settings.getProperty(name);
        }
        assertTrue(value != null && !value.isBlank(), name + " must be configured");
        return value;
    }
}
