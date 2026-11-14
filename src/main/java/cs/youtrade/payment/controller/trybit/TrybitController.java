package cs.youtrade.payment.controller.trybit;

import cs.youtrade.payment.controller.trybit.dto.TrybitNotificationDto;
import cs.youtrade.payment.service.trybit.TrybitPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/webhook/trybit")
public class TrybitController {
    private final TrybitPaymentService paymentService;

    @PostMapping(value = {"/notification", "/notification/"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Boolean> notification(@RequestBody TrybitNotificationDto notification) {
        return ResponseEntity.ok(paymentService.proceed(notification));
    }

    @PostMapping(value = {"/notification", "/notification/"}, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Boolean> formNotification(
            @RequestParam String status,
            @RequestParam("invoice_id") String invoiceId,
            @RequestParam("order_id") String orderId,
            @RequestParam String token
    ) {
        return notification(new TrybitNotificationDto(status, invoiceId, orderId, token));
    }
}
