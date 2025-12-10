package cs.youtrade.payment.controller.heleket;

import cs.youtrade.payment.service.heleket.HeleketPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static cs.youtrade.payment.util.heleket.HeleketSignatureProcessor.validateSignature;

@Log4j2
@RequiredArgsConstructor
@RestController("/webhook/heleket")
public class HeleketController {
    private final HeleketPaymentService heleketPaymentService;

    @Value("${youtrade.payment.heleket.api}")
    private String heleketAPI;

    @PostMapping
    public ResponseEntity<?> proceedPayment(
            HttpServletRequest request
    ) throws IOException {
        // 1. Проверяем IP-адрес
        String clientIp = getClientIp(request);
        if (!isTrustedIp(clientIp)) {
            log.warn("Access denied from IP: {}", clientIp);
            return ResponseEntity.badRequest().body("Access denied from IP: " + clientIp);
        }

        // 2. Получаем jsonBody
        String jsonBody = request
                .getReader()
                .lines()
                .collect(Collectors.joining());

        // 2. Проверяем подпись
        if (!validateSignature(jsonBody, heleketAPI)) {
            log.error("Invalid signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        // 3. Ответ
        return ResponseEntity.ok(heleketPaymentService.proceed(jsonBody));
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_X_FORWARDED_FOR",
                "HTTP_X_FORWARDED",
                "HTTP_X_CLUSTER_CLIENT_IP",
                "HTTP_CLIENT_IP",
                "HTTP_FORWARDED_FOR",
                "HTTP_FORWARDED",
                "HTTP_VIA",
                "REMOTE_ADDR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For может содержать несколько IP
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

    private boolean isTrustedIp(String ip) {
        // Доверенные IP адреса
        Set<String> trustedIps = Set.of(
                "31.133.220.8"
        );

        return trustedIps.contains(ip);
    }
}
