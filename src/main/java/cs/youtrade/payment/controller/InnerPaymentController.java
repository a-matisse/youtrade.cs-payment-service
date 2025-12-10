package cs.youtrade.payment.controller;

import cs.youtrade.payment.controller.heleket.dto.HeleketPaymentCreateAnsDto;
import cs.youtrade.payment.util.heleket.HeleketCommunicationEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController("/payment/create")
@RequiredArgsConstructor
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

        return ResponseEntity.ok(ans);
    }
}
