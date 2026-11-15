package cs.youtrade.payment.util.trybit;

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import cs.youtrade.payment.util.communication.HttpMethod;
import cs.youtrade.payment.util.communication.RestAnswer;
import cs.youtrade.payment.util.communication.YtSyncRestClient;
import cs.youtrade.payment.util.trybit.dto.TrybitApiResponse;
import cs.youtrade.payment.util.trybit.dto.TrybitInvoice;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Component
public class TrybitCommunicationEndpoint {
    private final String apiKey;
    private final String shopId;
    private final CloseableHttpClient httpClient;
    private final YtSyncRestClient client;

    public TrybitCommunicationEndpoint(
            @Value("${youtrade.payment.trybit.base-url}") String baseUrl,
            @Value("${youtrade.payment.trybit.api}") String apiKey,
            @Value("${youtrade.payment.trybit.shop-id}") String shopId
    ) {
        this.apiKey = apiKey;
        this.shopId = shopId;
        this.httpClient = HttpClients.custom()
                .disableAutomaticRetries()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                        .setResponseTimeout(Timeout.ofSeconds(20))
                        .build())
                .build();
        this.client = new YtSyncRestClient(baseUrl, httpClient);
    }

    public TrybitInvoice createPayment(String id, BigDecimal amount) {
        return request("/invoice/create", Map.of(
                "shop_id", shopId, "amount", amount, "currency", "USD", "order_id", id,
                "add_fields", Map.of("time_to_pay", Map.of("hours", 1, "minutes", 0))
        ), new TypeToken<TrybitApiResponse<TrybitInvoice>>() {});
    }

    public TrybitInvoice getInvoice(String invoiceId) {
        List<TrybitInvoice> invoices = request("/invoice/merchant/info", Map.of("uuids", List.of(invoiceId)),
                new TypeToken<TrybitApiResponse<List<TrybitInvoice>>>() {});
        if (invoices.size() != 1 || invoices.getFirst() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TryBit invoice not found");
        }
        return invoices.getFirst();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank() && !shopId.isBlank();
    }

    public List<TrybitInvoice> listInvoices(LocalDate start, LocalDate end, int offset, int limit) {
        var format = DateTimeFormatter.ofPattern("dd.MM.uuuu");
        return request("/invoice/merchant/list", Map.of(
                "start", start.format(format), "end", end.format(format),
                "offset", offset, "limit", limit
        ), new TypeToken<TrybitApiResponse<List<TrybitInvoice>>>() {});
    }

    private <T> T request(String path, Object body, TypeToken<TrybitApiResponse<T>> type) {
        if (apiKey.isBlank() || shopId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TryBit credentials are not configured");
        }
        RestAnswer<TrybitApiResponse<T>> answer;
        try {
            answer = client.fetchFromApi(HttpMethod.POST, path,
                    Map.of("Authorization", "Token " + apiKey), body, type);
        } catch (JsonParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid TryBit response");
        }
        var response = answer.getResponse();
        if (answer.getStatus() < 200 || answer.getStatus() >= 300 || response == null
                || !"success".equals(response.status()) || response.result() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TryBit API request failed");
        }
        return response.result();
    }

    @PreDestroy
    public void close() throws IOException {
        httpClient.close();
    }
}
