package cs.youtrade.payment.controller.heleket.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HeleketPaymentDto {
    private String type;
    private String uuid;
    private String orderId;
    private BigDecimal amount;
    private BigDecimal paymentAmount;
    private BigDecimal paymentAmountUsd;
    private BigDecimal merchantAmount;
    private BigDecimal commission;
    private Boolean isFinal;
    private String status;
    private String from;
    private String walletAddressUuid;
    private String network;
    private String currency;
    private String payerCurrency;
    private String additionalData;
    private ConvertInfo convert;
    private String txid;
    private String sign;

    @Data
    public static class ConvertInfo {
        private String toCurrency;
        private BigDecimal commission;
        private BigDecimal rate;
        private BigDecimal amount;
    }
}
