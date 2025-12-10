package cs.youtrade.payment.controller;

import cs.youtrade.payment.controller.dto.UserPaymentCreateDto;
import cs.youtrade.payment.util.heleket.HeleketCommunicationEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RequiredArgsConstructor
@RestController
@RequestMapping("/payment/create")
public class InnerPaymentController {
    private final HeleketCommunicationEndpoint endpoint;

    @PostMapping("/heleket")
    public ResponseEntity<?> createHeleketPayment(
            @RequestParam String id,
            @RequestParam BigDecimal amount
    ) {
        var ans = endpoint.createPayment(id, amount);
        if (ans.getStatus() >= 300)
            return ResponseEntity
                    .badRequest()
                    .body("Error occured on payment creation");

        var result = ans.getResponse().getResult();
        UserPaymentCreateDto dto = UserPaymentCreateDto
                .builder()
                .id(result.getOrderId())
                .amount(result.getAmount())
                .type(TopUpType.HELEKET)
                .url(result.getUrl())
                .build();
        return ResponseEntity.ok(dto);
    }
}
