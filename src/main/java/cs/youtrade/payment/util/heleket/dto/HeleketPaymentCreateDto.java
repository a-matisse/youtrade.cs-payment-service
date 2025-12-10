package cs.youtrade.payment.util.heleket.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HeleketPaymentCreateDto {
    String amount;
    String currency;
    String order_id;
}
