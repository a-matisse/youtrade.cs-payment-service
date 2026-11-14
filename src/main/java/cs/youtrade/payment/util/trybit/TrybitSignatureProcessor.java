package cs.youtrade.payment.util.trybit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

public final class TrybitSignatureProcessor {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TrybitSignatureProcessor() {
    }

    public static boolean validateSignature(String token, String secret) {
        if (token == null || token.isBlank() || token.length() > 16384
                || secret == null || secret.isBlank()) {
            return false;
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return false;
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            JsonNode header = MAPPER.readTree(decoder.decode(parts[0]));
            if (header == null || !"HS256".equals(header.path("alg").asText())) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(expected, decoder.decode(parts[2]))) {
                return false;
            }

            JsonNode claims = MAPPER.readTree(decoder.decode(parts[1]));
            if (claims == null || !claims.isObject()) {
                return false;
            }
            long now = Instant.now().getEpochSecond();
            JsonNode expiry = claims.path("exp");
            if (!expiry.isIntegralNumber() || !expiry.canConvertToLong() || expiry.longValue() <= now) {
                return false;
            }
            JsonNode notBefore = claims.get("nbf");
            return notBefore == null || (notBefore.isIntegralNumber() && notBefore.canConvertToLong()
                    && notBefore.longValue() <= now);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
