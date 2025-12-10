package cs.youtrade.payment.controller.heleket.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class HeleketPaymentCreateAnsDto {
    Integer state;
    PaymentResult result;

    @Value
    @Builder
    public static class PaymentResult {
        String uuid;

        @SerializedName("order_id")
        String orderId;

        BigDecimal amount;

        @SerializedName("payment_amount")
        BigDecimal paymentAmount;

        @SerializedName("payment_amount_usd")
        BigDecimal paymentAmountUsd;

        @SerializedName("payer_amount")
        BigDecimal payerAmount;

        @SerializedName("payer_amount_exchange_rate")
        BigDecimal payerAmountExchangeRate;

        @SerializedName("discount_percent")
        Integer discountPercent;

        BigDecimal discount;

        @SerializedName("payer_currency")
        String payerCurrency;

        String currency;

        String comments;

        @SerializedName("merchant_amount")
        BigDecimal merchantAmount;

        String network;

        String address;

        String from;

        String txid;

        @SerializedName("payment_status")
        String paymentStatus;

        String url;

        @SerializedName("expired_at")
        Long expiredAt;

        String status;

        @SerializedName("is_final")
        Boolean isFinal;

        @SerializedName("additional_data")
        Object additionalData;

        @SerializedName("created_at")
        String createdAt;

        @SerializedName("updated_at")
        String updatedAt;

        BigDecimal commission;

        @SerializedName("address_qr_code")
        String addressQrCode;
    }
}
