package com.lzqlove.jiaowu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析 debuggerd 的 Tombstone protobuf（system/core/debuggerd/proto/tombstone.proto），
 * 只提取反馈需要的字段：时间、进程、信号、原因、线程与堆栈前 12 帧。
 */
final class TombstoneParser {

    private static final int MAX_FRAMES = 12;

    private TombstoneParser() {
    }

    static final class Info {
        String timestamp = "";
        String process = "";
        int pid;
        int tid;
        int signalNumber;
        String signalName = "";
        String signalCode = "";
        boolean hasFaultAddress;
        long faultAddress;
        String abortMessage = "";
        String cause = "";
        final Map<Integer, ThreadInfo> threads = new LinkedHashMap<>();

        ThreadInfo thread() {
            ThreadInfo direct = threads.get(tid);
            if (direct != null) return direct;
            return threads.isEmpty() ? null : threads.values().iterator().next();
        }
    }

    static final class ThreadInfo {
        int id;
        String name = "";
        final List<Frame> frames = new ArrayList<>();
    }

    static final class Frame {
        long pc;
        String function = "";
        long offset;
        String file = "";
    }

    static Info parse(byte[] data) throws IOException {
        Info info = new Info();
        Reader reader = new Reader(data);
        while (!reader.done()) {
            int tag = reader.readTag();
            if (tag == 0) break;
            int field = tag >>> 3;
            int wire = tag & 7;
            switch (field) {
                case 4:
                    info.timestamp = text(reader, wire);
                    break;
                case 5:
                    info.pid = (int) number(reader, wire);
                    break;
                case 6:
                    info.tid = (int) number(reader, wire);
                    break;
                case 9:
                    // command_line 是 repeated string，只取第一个进程名
                    if (info.process.isEmpty()) {
                        info.process = text(reader, wire);
                    } else {
                        reader.skip(wire);
                    }
                    break;
                case 10:
                    parseSignal(info, message(reader, wire));
                    break;
                case 14:
                    info.abortMessage = text(reader, wire);
                    break;
                case 15:
                    parseCause(info, message(reader, wire));
                    break;
                case 16:
                    parseThreadEntry(info, message(reader, wire));
                    break;
                default:
                    reader.skip(wire);
            }
        }
        return info;
    }

    static String toText(Info info) {
        StringBuilder out = new StringBuilder();
        if (!info.process.isEmpty()) out.append("进程：").append(info.process).append('\n');

        out.append("信号：");
        out.append(info.signalName.isEmpty() ? "未知" : info.signalName);
        if (info.signalNumber != 0) out.append(" (").append(info.signalNumber).append(')');
        if (!info.signalCode.isEmpty()) out.append("，code ").append(info.signalCode);
        if (info.hasFaultAddress) {
            out.append("，fault addr 0x").append(Long.toHexString(info.faultAddress));
        }
        out.append('\n');

        if (!info.abortMessage.isEmpty()) out.append("Abort：").append(info.abortMessage).append('\n');
        if (!info.cause.isEmpty()) out.append("原因：").append(info.cause).append('\n');

        ThreadInfo thread = info.thread();
        if (thread != null && !thread.name.isEmpty()) {
            out.append("线程：").append(thread.name)
                    .append(" (tid ").append(info.tid).append(")\n");
        }

        out.append("堆栈：\n");
        if (thread == null || thread.frames.isEmpty()) {
            out.append("系统未提供\n");
            return out.toString();
        }

        int count = Math.min(thread.frames.size(), MAX_FRAMES);
        for (int i = 0; i < count; i++) {
            Frame frame = thread.frames.get(i);
            out.append("  #").append(i < 10 ? "0" : "").append(i).append(' ');
            out.append(frame.function.isEmpty() ? "未知函数" : frame.function);
            if (frame.offset != 0) out.append("+0x").append(Long.toHexString(frame.offset));
            if (!frame.file.isEmpty()) out.append(" (").append(frame.file).append(')');
            out.append('\n');
        }
        if (thread.frames.size() > count) out.append("  …\n");
        return out.toString();
    }

    private static void parseSignal(Info info, byte[] data) throws IOException {
        if (data == null) return;
        Reader reader = new Reader(data);
        while (!reader.done()) {
            int tag = reader.readTag();
            if (tag == 0) break;
            int field = tag >>> 3;
            int wire = tag & 7;
            switch (field) {
                case 1:
                    info.signalNumber = (int) number(reader, wire);
                    break;
                case 2:
                    info.signalName = text(reader, wire);
                    break;
                case 4:
                    info.signalCode = text(reader, wire);
                    break;
                case 8:
                    info.hasFaultAddress = number(reader, wire) != 0;
                    break;
                case 9:
                    info.faultAddress = number(reader, wire);
                    break;
                default:
                    reader.skip(wire);
            }
        }
    }

    private static void parseCause(Info info, byte[] data) throws IOException {
        if (data == null) return;
        Reader reader = new Reader(data);
        while (!reader.done()) {
            int tag = reader.readTag();
            if (tag == 0) break;
            int field = tag >>> 3;
            int wire = tag & 7;
            if (field == 1) {
                info.cause = text(reader, wire);
            } else {
                reader.skip(wire);
            }
        }
    }

    private static void parseThreadEntry(Info info, byte[] data) throws IOException {
        if (data == null) return;
        Reader reader = new Reader(data);
        int key = 0;
        ThreadInfo thread = null;
        while (!reader.done()) {
            int tag = reader.readTag();
            if (tag == 0) break;
            int field = tag >>> 3;
            int wire = tag & 7;
            if (field == 1) {
                key = (int) number(reader, wire);
            } else if (field == 2 && wire == 2) {
                thread = new ThreadInfo();
                parseThread(thread, reader.readBytes());
            } else {
                reader.skip(wire);
            }
        }
        if (thread != null) info.threads.put(key, thread);
    }

    private static void parseThread(ThreadInfo thread, byte[] data) throws IOException {
        Reader reader = new Reader(data);
        while (!reader.done()) {
            int tag = reader.readTag();
            if (tag == 0) break;
            int field = tag >>> 3;
            int wire = tag & 7;
            switch (field) {
                case 1:
                    thread.id = (int) number(reader, wire);
                    break;
                case 2:
                    thread.name = text(reader, wire);
                    break;
                case 4:
                    byte[] frameData = message(reader, wire);
                    if (frameData != null) thread.frames.add(parseFrame(frameData));
                    break;
                default:
                    reader.skip(wire);
            }
        }
    }

    private static Frame parseFrame(byte[] data) throws IOException {
        Frame frame = new Frame();
        Reader reader = new Reader(data);
        while (!reader.done()) {
            int tag = reader.readTag();
            if (tag == 0) break;
            int field = tag >>> 3;
            int wire = tag & 7;
            switch (field) {
                case 2:
                    frame.pc = number(reader, wire);
                    break;
                case 4:
                    frame.function = text(reader, wire);
                    break;
                case 5:
                    frame.offset = number(reader, wire);
                    break;
                case 6:
                    frame.file = text(reader, wire);
                    break;
                default:
                    reader.skip(wire);
            }
        }
        return frame;
    }

    private static long number(Reader reader, int wire) throws IOException {
        if (wire == 0) return reader.readVarint();
        reader.skip(wire);
        return 0;
    }

    private static String text(Reader reader, int wire) throws IOException {
        if (wire == 2) return reader.readString();
        reader.skip(wire);
        return "";
    }

    private static byte[] message(Reader reader, int wire) throws IOException {
        if (wire == 2) return reader.readBytes();
        reader.skip(wire);
        return null;
    }

    /** 最小 protobuf wire format 读取器，够解析 tombstone 即可。 */
    private static final class Reader {
        private final byte[] data;
        private int position;

        Reader(byte[] data) {
            this.data = data;
        }

        boolean done() {
            return position >= data.length;
        }

        int readTag() throws IOException {
            long value = readVarint();
            if (value == 0 || value > Integer.MAX_VALUE) throw new IOException("bad tag");
            return (int) value;
        }

        long readVarint() throws IOException {
            long result = 0;
            int shift = 0;
            while (shift < 64) {
                if (position >= data.length) throw new IOException("truncated");
                int value = data[position++] & 0xFF;
                result |= (long) (value & 0x7F) << shift;
                if ((value & 0x80) == 0) return result;
                shift += 7;
            }
            throw new IOException("varint too long");
        }

        String readString() throws IOException {
            return new String(readBytes(), StandardCharsets.UTF_8);
        }

        byte[] readBytes() throws IOException {
            long length = readVarint();
            if (length < 0 || length > data.length - position) {
                throw new IOException("bad length");
            }
            byte[] value = new byte[(int) length];
            System.arraycopy(data, position, value, 0, (int) length);
            position += (int) length;
            return value;
        }

        void skip(int wire) throws IOException {
            switch (wire) {
                case 0:
                    readVarint();
                    break;
                case 1:
                    require(8);
                    position += 8;
                    break;
                case 2:
                    long length = readVarint();
                    if (length < 0 || length > data.length - position) {
                        throw new IOException("bad length");
                    }
                    position += (int) length;
                    break;
                case 5:
                    require(4);
                    position += 4;
                    break;
                default:
                    throw new IOException("unsupported wire type");
            }
        }

        private void require(int count) throws IOException {
            if (data.length - position < count) throw new IOException("truncated");
        }
    }
}
