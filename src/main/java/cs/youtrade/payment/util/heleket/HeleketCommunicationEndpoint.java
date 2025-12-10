package cs.youtrade.payment.util.heleket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import cs.youtrade.payment.controller.heleket.dto.HeleketPaymentCreateAnsDto;
import cs.youtrade.payment.util.communication.HttpMethod;
import cs.youtrade.payment.util.communication.RestAnswer;
import cs.youtrade.payment.util.communication.YtSyncRestClient;
import cs.youtrade.payment.util.gson.GsonConfig;
import cs.youtrade.payment.util.heleket.dto.HeleketPaymentCreateDto;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

import static cs.youtrade.payment.util.heleket.HeleketSignatureProcessor.generateSignature;

@Component
public class HeleketCommunicationEndpoint {
    private static final Gson GSON = GsonConfig.createGson();
    private static final String URL_CALLBACK = "https://youtradecs.xyz/webhook/heleket";
    private static final String HELEKET_ENDPOINT = "https://api.heleket.com/v1";

    @Value("${youtrade.payment.heleket.uuid}")
    private String heleketUUID;
    @Value("${youtrade.payment.heleket.api}")
    private String heleketAPI;
    private YtSyncRestClient client;

    @PostConstruct
    public void init() {
        this.client = new YtSyncRestClient(HELEKET_ENDPOINT);
    }

    public RestAnswer<HeleketPaymentCreateAnsDto> createPayment(
            String id,
            BigDecimal amount
    ) {
        // 1. Создаем body
        HeleketPaymentCreateDto dto = HeleketPaymentCreateDto
                .builder()
                .amount(amount.toPlainString())
                .order_id(id)
                .currency("USD")
                .url_callback(URL_CALLBACK)
                .build();

        // 2. Создаем json для подписи
        String json = GSON.toJson(dto);

        // 3. Отправляем запрос и возвращаем его ответ
        return client.fetchFromApi(
                HttpMethod.POST,
                "/payment",
                getHeaders(json),
                dto,
                new TypeToken<>() {
                }
        );
    }

    private Map<String, String> getHeaders(String jsonData) {
        return Map.of(
                "merchant", heleketUUID,
                "sign", generateSignature(jsonData, heleketAPI)
        );
    }
}
