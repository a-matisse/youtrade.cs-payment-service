package cs.youtrade.payment.controller.trybit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TrybitNotificationDto(
        String status,
        @JsonProperty("invoice_id") String invoiceId,
        @JsonProperty("order_id") String orderId,
        String token
) {
}
