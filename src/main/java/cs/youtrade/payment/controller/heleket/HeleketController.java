package cs.youtrade.payment.controller.heleket;

import com.google.gson.Gson;
import cs.youtrade.payment.controller.heleket.dto.HeleketPaymentDto;
import cs.youtrade.payment.util.gson.GsonConfig;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static cs.youtrade.payment.util.heleket.HeleketSignatureProcessor.validateSignature;

@Log4j2
@RestController("/webhook/heleket")
public class HeleketController {
    private static final Gson gson = GsonConfig.createGson();

    private final String heleketAPI;
    private final RedisTemplate<String, String> redisTemplate;
    private final String redisStreamName;

    @Autowired
    public HeleketController(
            RedisTemplate<String, String> redisTemplate,
            @Value("${youtrade.payment.heleket.api}") String heleketAPI,
            @Value("${youtrade.payment.redis.stream.name}") String redisStreamName
    ) {
        this.redisTemplate = redisTemplate;
        this.heleketAPI = heleketAPI;
        this.redisStreamName = redisStreamName;
    }

    @PostMapping
    public void proceedPayment(
            HttpServletRequest request
    ) throws IOException {
        String jsonBody = request
                .getReader()
                .lines()
                .collect(Collectors.joining());

        if (!validateSignature(jsonBody, heleketAPI)) {
            log.error("Invalid signature");
            return;
        }

        MapRecord<String, String, String> record = MapRecord
                .create(redisStreamName, Map.of("payload", jsonBody));
        redisTemplate.opsForStream().add(record);
    }
}
