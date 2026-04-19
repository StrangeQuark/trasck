package com.strangequark.trasck.api;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PageCursorCodec {

    private PageCursorCodec() {
    }

    public static String encodeRank(String rank, String id) {
        return encode(List.of(required(rank, "rank"), required(id, "id")));
    }

    public static RankCursor decodeRank(String cursor) {
        List<String> parts = decode(cursor);
        if (parts.size() != 2 || parts.get(0).isBlank() || parts.get(1).isBlank()) {
            throw badCursor();
        }
        return new RankCursor(parts.get(0), parts.get(1));
    }

    public static String encodeTimestamp(OffsetDateTime createdAt, String id) {
        return encode(List.of(required(createdAt, "createdAt").toString(), required(id, "id")));
    }

    public static TimestampCursor decodeTimestamp(String cursor) {
        List<String> parts = decode(cursor);
        if (parts.size() != 2 || parts.get(0).isBlank() || parts.get(1).isBlank()) {
            throw badCursor();
        }
        try {
            return new TimestampCursor(OffsetDateTime.parse(parts.get(0)), parts.get(1));
        } catch (Exception ex) {
            throw badCursor();
        }
    }

    public static String encodeLong(long value, String id) {
        return encode(List.of(Long.toString(value), required(id, "id")));
    }

    public static LongCursor decodeLong(String cursor) {
        List<String> parts = decode(cursor);
        if (parts.size() != 2 || parts.get(0).isBlank() || parts.get(1).isBlank()) {
            throw badCursor();
        }
        try {
            return new LongCursor(Long.parseLong(parts.get(0)), parts.get(1));
        } catch (NumberFormatException ex) {
            throw badCursor();
        }
    }

    private static String encode(List<String> parts) {
        String value = String.join("\n", parts);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return List.of();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return decoded.lines().toList();
        } catch (IllegalArgumentException ex) {
            throw badCursor();
        }
    }

    private static <T> T required(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private static ResponseStatusException badCursor() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page cursor");
    }

    public record RankCursor(String rank, String id) {
    }

    public record TimestampCursor(OffsetDateTime createdAt, String id) {
    }

    public record LongCursor(long value, String id) {
    }
}
