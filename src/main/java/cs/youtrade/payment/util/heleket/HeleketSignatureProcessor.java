package cs.youtrade.payment.util.heleket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.*;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;

@Log4j2
public class HeleketSignatureProcessor {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    public static String generateSignature(String json, String heleketAPI) {
        // Создаем base64 строку
        String base64Data = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        // Создаем хеш
        String message = base64Data + heleketAPI;
        return DigestUtils.md5Hex(message);
    }

    public static boolean validateSignature(String jsonInput, String heleketAPI) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Разбираем в LinkedHashMap — порядок ключей сохраняется в том виде, как был в JSON
            TypeReference<LinkedHashMap<String, Object>> typeRef = new TypeReference<>() {};
            LinkedHashMap<String, Object> map = mapper.readValue(jsonInput, typeRef);

            // Получаем и удаляем sign
            Object signObj = map.remove("sign");
            if (signObj == null) {
                return false;
            }
            String sign = signObj.toString().trim().toLowerCase(Locale.ROOT);

            // Сериализуем обратно компактно (без пробелов/переводов строк)
            String json = mapper.writeValueAsString(map);
            // Важно: Jackson по умолчанию не экранирует unicode, поэтому кириллица останется как есть.

            // Base64 и md5 как в PHP
            String base64Encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            String message = base64Encoded + heleketAPI;
            String hash = DigestUtils.md5Hex(message).toLowerCase(Locale.ROOT);

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
