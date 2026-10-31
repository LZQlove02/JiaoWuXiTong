package com.lzpu.jwxt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class TombstoneParserTest {

    @Test
    public void parsesSignalCauseAndCrashingThread() throws IOException {
        String timestamp = "2026-11-01 00:19:12.932+0800";

        ByteArrayOutputStream signal = new ByteArrayOutputStream();
        varintField(signal, 1, 11);
        stringField(signal, 2, "SIGSEGV");
        stringField(signal, 4, "SI_USER");
        varintField(signal, 8, 1);
        varintField(signal, 9, 0);

        ByteArrayOutputStream cause = new ByteArrayOutputStream();
        stringField(cause, 1, "null pointer dereference");

        ByteArrayOutputStream thread = new ByteArrayOutputStream();
        varintField(thread, 1, 123);
        stringField(thread, 2, "main");
        for (int i = 0; i < 20; i++) {
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            varintField(frame, 2, 0x1000 + i);
            stringField(frame, 4, "func" + i);
            stringField(frame, 6, "libc.so");
            appendMessage(thread, 4, frame.toByteArray());
        }

        ByteArrayOutputStream threadEntry = new ByteArrayOutputStream();
        varintField(threadEntry, 1, 123);
        appendMessage(threadEntry, 2, thread.toByteArray());

        ByteArrayOutputStream root = new ByteArrayOutputStream();
        stringField(root, 2, "Xiaomi/fuxi/fuxi:16/build");
        stringField(root, 4, timestamp);
        varintField(root, 6, 123);
        stringField(root, 9, "com.lzpu.jwxt");
        appendMessage(root, 10, signal.toByteArray());
        appendMessage(root, 15, cause.toByteArray());
        appendMessage(root, 16, threadEntry.toByteArray());

        TombstoneParser.Info info = TombstoneParser.parse(root.toByteArray());
        assertEquals(timestamp, info.timestamp);
        assertEquals(123, info.tid);
        assertEquals("com.lzpu.jwxt", info.process);
        assertEquals("SIGSEGV", info.signalName);
        assertEquals(11, info.signalNumber);
        assertEquals("SI_USER", info.signalCode);
        assertTrue(info.hasFaultAddress);
        assertEquals("null pointer dereference", info.cause);
        assertEquals("main", info.thread().name);
        assertEquals(20, info.thread().frames.size());
        assertEquals("func0", info.thread().frames.get(0).function);

        String text = TombstoneParser.toText(info);
        assertTrue(text.contains("信号：SIGSEGV (11)"));
        assertTrue(text.contains("code SI_USER"));
        assertTrue(text.contains("原因：null pointer dereference"));
        assertTrue(text.contains("线程：main (tid 123)"));
        assertTrue(text.contains("#11 func11"));
        assertFalse(text.contains("#12 func12"));
        assertFalse(text.contains("Xiaomi/fuxi"));
    }

    private static void varintField(ByteArrayOutputStream out, int field, long value) {
        tag(out, field, 0);
        writeVarint(out, value);
    }

    private static void stringField(ByteArrayOutputStream out, int field, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        tag(out, field, 2);
        writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    private static void appendMessage(ByteArrayOutputStream out, int field, byte[] payload) {
        tag(out, field, 2);
        writeVarint(out, payload.length);
        out.write(payload, 0, payload.length);
    }

    private static void tag(ByteArrayOutputStream out, int field, int wire) {
        writeVarint(out, ((long) field << 3) | wire);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }
}
