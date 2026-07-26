package com.blog;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A minimal multipart/form-data parser. The JDK's built-in HTTP server has
 * no multipart support, so this handles it directly: splits the raw body
 * on the boundary and pulls out each part's headers and content.
 */
public final class MultipartParser {

    public static final class Part {
        public String name;
        public String filename; // null for plain text fields
        public String contentType;
        public byte[] data;

        public String asText() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /** Extracts the boundary token from a Content-Type header like: multipart/form-data; boundary=---xyz */
    public static String extractBoundary(String contentType) {
        if (contentType == null) return null;
        for (String piece : contentType.split(";")) {
            piece = piece.trim();
            if (piece.startsWith("boundary=")) {
                String b = piece.substring("boundary=".length());
                if (b.startsWith("\"") && b.endsWith("\"")) b = b.substring(1, b.length() - 1);
                return b;
            }
        }
        return null;
    }

    public static List<Part> parse(byte[] body, String boundary) {
        List<Part> parts = new ArrayList<>();
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);

        List<int[]> boundaryRanges = new ArrayList<>();
        int idx = 0;
        while (true) {
            int pos = indexOf(body, delimiter, idx);
            if (pos < 0) break;
            boundaryRanges.add(new int[]{pos, pos + delimiter.length});
            idx = pos + delimiter.length;
        }

        for (int b = 0; b < boundaryRanges.size() - 1; b++) {
            int partStart = boundaryRanges.get(b)[1];
            int partEnd = boundaryRanges.get(b + 1)[0];
            if (partEnd <= partStart) continue;

            if (partStart + 1 < body.length && body[partStart] == '-' && body[partStart + 1] == '-') {
                continue; // closing boundary
            }
            if (partStart + 1 < partEnd && body[partStart] == '\r' && body[partStart + 1] == '\n') {
                partStart += 2;
            }
            if (partEnd - 2 >= partStart && body[partEnd - 2] == '\r' && body[partEnd - 1] == '\n') {
                partEnd -= 2;
            }

            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), partStart);
            if (headerEnd < 0 || headerEnd > partEnd) continue;

            String headerText = new String(body, partStart, headerEnd - partStart, StandardCharsets.UTF_8);
            int contentStart = headerEnd + 4;

            Part part = new Part();
            for (String line : headerText.split("\r\n")) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("content-disposition")) {
                    part.name = extractParam(line, "name");
                    part.filename = extractParam(line, "filename");
                } else if (lower.startsWith("content-type")) {
                    part.contentType = line.substring(line.indexOf(':') + 1).trim();
                }
            }
            part.data = Arrays.copyOfRange(body, contentStart, partEnd);
            if (part.name != null) parts.add(part);
        }
        return parts;
    }

    private static String extractParam(String headerLine, String param) {
        String marker = param + "=\"";
        int i = headerLine.indexOf(marker);
        if (i < 0) return null;
        int start = i + marker.length();
        int end = headerLine.indexOf('"', start);
        if (end < 0) return null;
        return headerLine.substring(start, end);
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(from, 0); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
