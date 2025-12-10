package cs.youtrade.payment.util.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import cs.youtrade.payment.util.gson.adapter.temporal.LocalDateAdapter;
import cs.youtrade.payment.util.gson.adapter.temporal.LocalDateTimeAdapter;
import cs.youtrade.payment.util.gson.adapter.temporal.LocalTimeAdapter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class GsonConfig {
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
                .create();
    }
}
