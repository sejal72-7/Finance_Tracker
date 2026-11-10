package com.financetracker.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @Test
    void serializeAndParseRoundTrip() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("description", "Coffee \"Latte\"\nMorning");
        input.put("amount", new BigDecimal("12.50"));
        input.put("date", LocalDate.of(2026, 11, 10));
        input.put("active", true);
        input.put("tags", List.of("Food", "Cafe"));
        input.put("notes", null);

        String json = JsonUtil.toJson(input);
        Map<String, Object> parsed = JsonUtil.parseObject(json);

        assertEquals("Coffee \"Latte\"\nMorning", parsed.get("description"));
        assertEquals(new BigDecimal("12.50"), parsed.get("amount"));
        assertEquals("2026-11-10", parsed.get("date"));
        assertEquals(Boolean.TRUE, parsed.get("active"));
        assertEquals(List.of("Food", "Cafe"), parsed.get("tags"));
        assertNull(parsed.get("notes"));
    }

    @Test
    void rejectMalformedJson() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("{bad"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("[1, 2]"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseObject("{\"a\":1} extra"));
    }
}

