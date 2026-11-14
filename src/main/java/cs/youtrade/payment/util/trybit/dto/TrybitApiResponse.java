package cs.youtrade.payment.util.trybit.dto;

public record TrybitApiResponse<T>(String status, T result) {
}
