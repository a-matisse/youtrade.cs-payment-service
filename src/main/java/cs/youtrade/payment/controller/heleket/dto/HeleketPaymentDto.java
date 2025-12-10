package cs.youtrade.payment.controller.heleket.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class HeleketPaymentDto {
    String type;
    String uuid;
    @SerializedName("order_id")
    String orderId;
    BigDecimal amount;
    @SerializedName("payment_amount")
    BigDecimal paymentAmount;
    @SerializedName("payment_amount_usd")
    BigDecimal paymentAmountUsd;
    BigDecimal merchantAmount;
    BigDecimal commission;
    Boolean isFinal;
    String status;
    String from;
    String walletAddressUuid;
    String network;
    String currency;
    String payerCurrency;
    String additionalData;
    ConvertInfo convert;
    String txid;
    String sign;

    @Value
    @Builder
    public static class ConvertInfo {
        String toCurrency;
        BigDecimal commission;
        BigDecimal rate;
        BigDecimal amount;
    }
}
