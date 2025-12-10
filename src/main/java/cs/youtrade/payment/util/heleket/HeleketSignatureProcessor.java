package cs.youtrade.payment.util.heleket;

import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hc.client5.http.utils.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Log4j2
public class HeleketSignatureProcessor {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public static String generateSignature(String json, String heleketAPI) {
        // Создаем base64 строку
        String base64Data = Base64.encodeBase64String(json.getBytes(StandardCharsets.UTF_8));
        // Создаем хеш
        String message = base64Data + heleketAPI;
        return DigestUtils.md5Hex(message);
    }

    public static boolean validateSignature(String jsonInput, String heleketAPI) {
        try {
            // Парсим JSON
            JsonElement jsonElement = JsonParser.parseString(jsonInput);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // Получаем подпись
            String sign = jsonObject.get("sign").getAsString();

            // Удаляем подпись из JSON объекта
            jsonObject.remove("sign");

            // Конвертируем обратно в строку JSON
            String json = GSON.toJson(jsonObject);

            // Создаем base64 строку
            String base64Encoded = Base64.encodeBase64String(json.getBytes(StandardCharsets.UTF_8));

            // Создаем хеш
            String message = base64Encoded + heleketAPI;
            String hash = DigestUtils.md5Hex(message);

            // Безопасное сравнение
            return MessageDigest.isEqual(
                    hash.getBytes(StandardCharsets.UTF_8),
                    sign.getBytes(StandardCharsets.UTF_8)
            );

        } catch (Exception e) {
            log.error("Signature validation failed", e);
            return false;
        }
    }
}
