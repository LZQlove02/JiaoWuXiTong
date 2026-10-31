package com.lzpu.jwxt;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地崩溃日志：未捕获异常只写入应用私有目录，不上传、不联网。
 * 反馈页通过 SupportBridge.getLastCrash 读取脱敏摘要，由用户主动选择是否附带。
 */
public final class CrashLog {

    private static final String DIR = "diagnostics";
    private static final String FILE = "last-crash.txt";
    private static final int MAX_CHARS = 16 * 1024;
    private static final int MAX_MESSAGE = 300;
    private static final int MAX_SUMMARY = 1400;
    private static final int MAX_FRAMES = 12;

    private static volatile boolean installed;

    private CrashLog() {
    }

    /** 安装全局未捕获异常处理；保留系统原有处理，写入失败不影响崩溃流程。 */
    public static synchronized void install(Context context) {
        if (installed) return;
        installed = true;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable error) {
                try {
                    save(app, thread, error);
                } catch (Throwable ignored) {
                    // 崩溃处理路径不能再抛出异常
                }
                if (previous != null) previous.uncaughtException(thread, error);
            }
        });
    }

    /** 反馈邮件只带头部、异常摘要与前 12 帧；没有记录时返回空串。 */
    public static String readSummary(Context context) {
        String text = readAll(context);
        return text.isEmpty() ? "" : summarize(text);
    }

    public static void clear(Context context) {
        File target = file(context);
        if (target.exists()) target.delete();
    }

    private static File file(Context context) {
        return new File(new File(context.getFilesDir(), DIR), FILE);
    }

    private static void save(Context context, Thread thread, Throwable error) {
        String text = sanitize(format(context, thread, error));
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS);
        File target = file(context);
        File dir = target.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) return;
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 写不进去就放弃，不能影响原有崩溃流程
        }
    }

    static String format(Context context, Thread thread, Throwable error) {
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        error.printStackTrace(writer);
        writer.flush();

        String exception = error.toString();
        if (exception.length() > MAX_MESSAGE) exception = exception.substring(0, MAX_MESSAGE) + "…";

        String version = versionName(context);
        return "类型：应用崩溃"
                + "\n时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date())
                + "\n应用：" + (version.isEmpty() ? "未知" : "v" + version)
                + "\n设备：" + Build.MANUFACTURER + " " + Build.MODEL
                + "\n系统：Android " + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）"
                + "\n线程：" + (thread == null ? "未知" : thread.getName())
                + "\n异常：" + exception
                + "\n堆栈：\n" + buffer;
    }

    private static String versionName(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
            return name == null ? "" : name;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }

    private static String readAll(Context context) {
        File target = file(context);
        if (!target.isFile()) return "";
        try (FileInputStream input = new FileInputStream(target)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_CHARS) break;
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "";
        }
    }

    static String summarize(String text) {
        String[] lines = text.split("\n");
        StringBuilder summary = new StringBuilder();
        int frames = 0;
        for (String line : lines) {
            boolean frame = line.startsWith("\tat ") || line.startsWith("Caused by:");
            if (frame) {
                if (frames >= MAX_FRAMES) continue;
                frames++;
            }
            if (summary.length() + line.length() + 1 > MAX_SUMMARY) {
                summary.append("…（已截断）");
                break;
            }
            summary.append(line).append('\n');
        }
        return summary.toString().trim();
    }

    /** 去掉链接、Cookie/令牌等可能带隐私的值；只处理文本，不改变结构。 */
    static String sanitize(String text) {
        return text
                .replaceAll("https?://\\S+", "[已隐藏链接]")
                .replaceAll("(?i)(cookie|authorization|token|session|password|passwd|pwd)\\s*[:=]\\s*[^\\n]*", "$1=[已隐藏]")
                .replaceAll("[0-9A-Fa-f]{16,}", "[已隐藏]");
    }
}
