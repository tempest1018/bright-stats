package org.brightstats.interop;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Standalone test harness; optional reference invocation runs only in Docker. */
public final class LegacyEnvelopeCompatibility {
    @FunctionalInterface
    private interface CheckedAction { void run() throws Exception; }

    private static void mustReject(CheckedAction action) throws Exception {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException | java.security.GeneralSecurityException
                 | java.nio.charset.CharacterCodingException expected) {
            rejected = true;
        }
        if (!rejected) throw new AssertionError("Malformed envelope was accepted");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException("Expected key file, fixture directory, output directory, [reference]");
        }
        byte[] key = Files.readAllBytes(Path.of(args[0]));
        Path fixtures = Path.of(args[1]);
        Path output = Path.of(args[2]);
        Files.createDirectories(output);
        Method reference = null;
        if (args.length == 4 && args[3].equals("reference")) {
            Class<?> type = Class.forName("com.smartpls.common.y8gkp1gHZMI9wdx.YrbhECPsTFtSJTu");
            reference = type.getMethod("tomexvWZymxA17C", String.class, boolean.class);
        }
        int decoded = 0;
        try (var paths = Files.list(fixtures)) {
            for (Path file : paths.filter(p -> p.getFileName().toString().endsWith(".enc")).sorted().toList()) {
                String envelope = Files.readString(file, StandardCharsets.UTF_8);
                String plaintext = LegacyEnvelopeCodec.decode(envelope, key, false);
                if (reference != null) {
                    String expected = (String) reference.invoke(null, envelope, false);
                    if (!expected.equals(plaintext)) {
                        throw new AssertionError("Reference mismatch: " + file.getFileName());
                    }
                }
                String rebuilt = LegacyEnvelopeCodec.encode(plaintext, key, false);
                if (!rebuilt.equals(envelope)) {
                    throw new AssertionError("Envelope regeneration mismatch: " + file.getFileName());
                }
                Files.writeString(output.resolve(file.getFileName().toString().replace(".enc", ".decoded")),
                        plaintext, StandardCharsets.UTF_8);
                decoded++;
            }
        }
        for (String text : List.of("", "ASCII", "Bright-Stats: \u03b1 \u03b2 \u4e2d\u6587 \ud83d\ude42", "x".repeat(4097))) {
            for (boolean urlSafe : List.of(false, true)) {
                String envelope = LegacyEnvelopeCodec.encode(text, key, urlSafe);
                if (!LegacyEnvelopeCodec.decode(envelope, key, urlSafe).equals(text)) {
                    throw new AssertionError("UTF-8 envelope mismatch");
                }
            }
        }
        mustReject(() -> LegacyEnvelopeCodec.decode("not base64!", key, false));
        mustReject(() -> LegacyEnvelopeCodec.decode("AA==", key, false));
        mustReject(() -> LegacyEnvelopeCodec.decode("", key, false));
        mustReject(() -> LegacyEnvelopeCodec.encode("x", new byte[3], false));
        String valid = LegacyEnvelopeCodec.encode("deterministic negative fixture", key, false);
        byte[] wrongKey = key.clone();
        wrongKey[0] ^= 1;
        mustReject(() -> LegacyEnvelopeCodec.decode(valid, wrongKey, false));
        if (decoded == 0) throw new AssertionError("No real reference fixtures were checked");
        System.out.printf("{\"decoded_fixtures\":%d,\"reference_comparison\":%s,"
                + "\"exact_envelope_regeneration\":true,\"utf8_cases\":8,\"negative_cases\":5}%n",
                decoded, reference != null);
        Arrays.fill(key, (byte) 0);
    }
}
