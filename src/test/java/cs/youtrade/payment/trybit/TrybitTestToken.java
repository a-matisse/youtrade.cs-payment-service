package cs.youtrade.payment.trybit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class TrybitTestToken {
    static final String SECRET = "test-project-secret";

    private TrybitTestToken() {
    }

    static String sign(String header, String claims) throws Exception {
        var encoder = Base64.getUrlEncoder().withoutPadding();
        String data = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return data + "." + encoder.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.US_ASCII)));
    }

    static String valid() throws Exception {
        return sign("{\"alg\":\"HS256\",\"typ\":\"JWT\"}",
                "{\"id\":13,\"exp\":" + (java.time.Instant.now().getEpochSecond() + 300) + "}");
    }
}
