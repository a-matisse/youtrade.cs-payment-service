package cs.youtrade.payment.service.heleket;

import com.google.gson.Gson;
import cs.youtrade.payment.controller.PaymentStatus;
import cs.youtrade.payment.controller.TopUpType;
import cs.youtrade.payment.controller.dto.UserPaymentProcedureDto;
import cs.youtrade.payment.controller.heleket.dto.HeleketPaymentDto;
import cs.youtrade.payment.util.gson.GsonConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class HeleketPaymentService {
    private static final Gson GSON = GsonConfig.createGson();

    private final RedisTemplate<String, String> redisTemplate;
    private final String redisStreamName;

    @Autowired
    public HeleketPaymentService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${youtrade.payment.redis.stream.name}") String redisStreamName
    ) {
        this.redisTemplate = redisTemplate;
        this.redisStreamName = redisStreamName;
    }

    public boolean proceed(String jsonBody) {
        // 1. Создание внутренней платежной информации на основе ответа
        var payment = GSON.fromJson(jsonBody, HeleketPaymentDto.class);
        UserPaymentProcedureDto ans = UserPaymentProcedureDto
                .builder()
                .id(payment.getOrderId())
                .amount(payment.getAmount())
                .type(TopUpType.HELEKET)
                .status(decidePaymentStatus(payment))
                .build();

        // 2. Отправка информации в redis-stream
        String json = GSON.toJson(ans);
        MapRecord<String, String, String> record = MapRecord
                .create(redisStreamName, Map.of("payload", json));
        redisTemplate.opsForStream().add(record);

        // 3. Подтверждение успешности операции
        return true;
    }

    private PaymentStatus decidePaymentStatus(HeleketPaymentDto dto) {
        return switch (dto.getStatus()) {
            case "confirm_check" -> PaymentStatus.PENDING;
            case "paid", "paid_over", "wrong_amount" -> PaymentStatus.COMPLETED;
            case "fail", "cancel", "system_fail" -> PaymentStatus.CANCELLED;
            default -> throw new IllegalStateException("Unexpected value: " + dto.getStatus());
        };
    }
}
