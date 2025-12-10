package cs.youtrade.payment.controller.dto;

import cs.youtrade.payment.controller.PaymentStatus;
import cs.youtrade.payment.controller.TopUpType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class UserPaymentProcedureDto {
    String id;
    BigDecimal amount;
    TopUpType type;
    PaymentStatus status;
}
