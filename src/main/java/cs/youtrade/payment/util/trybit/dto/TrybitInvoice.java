package cs.youtrade.payment.util.trybit.dto;

import com.google.gson.annotations.SerializedName;

import java.math.BigDecimal;

public record TrybitInvoice(
        String uuid,
        String link,
        String status,
        @SerializedName("order_id") String orderId,
        @SerializedName("amount_usd") BigDecimal amountUsd
) {
}
