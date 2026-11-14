package cs.youtrade.payment.trybit;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static cs.youtrade.payment.trybit.TrybitTestToken.SECRET;
import static cs.youtrade.payment.trybit.TrybitTestToken.sign;
import static cs.youtrade.payment.util.trybit.TrybitSignatureProcessor.validateSignature;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrybitSignatureProcessorTest {
    @Test
    void acceptsValidHs256TokenAndRejectsWrongSecretOrTampering() throws Exception {
        String token = TrybitTestToken.valid();
        assertTrue(validateSignature(token, SECRET));
        assertFalse(validateSignature(token, "wrong-secret"));
        assertFalse(validateSignature(token.substring(0, token.lastIndexOf('.') + 1) + "AAAA", SECRET));
    }

    @Test
    void rejectsExpiredMissingOrInvalidExpiryAndFutureNotBefore() throws Exception {
        long now = Instant.now().getEpochSecond();
        for (String claims : new String[]{
                "{\"exp\":" + (now - 1) + "}", "{}", "{\"exp\":\"9999999999\"}",
                "{\"exp\":null}", "{\"exp\":9999999999,\"nbf\":" + (now + 600) + "}"
        }) {
            assertFalse(validateSignature(sign("{\"alg\":\"HS256\"}", claims), SECRET));
        }
    }

    @Test
    void rejectsAlgorithmConfusionAndMalformedTokens() throws Exception {
        assertFalse(validateSignature(sign("{\"alg\":\"none\"}", "{\"exp\":9999999999}"), SECRET));
        for (String token : new String[]{"", "a.b.c", "one-part", "a.b.c.d", ".."}) {
            assertFalse(validateSignature(token, SECRET));
        }
        assertFalse(validateSignature(null, SECRET));
        assertFalse(validateSignature(TrybitTestToken.valid(), ""));
    }
}
