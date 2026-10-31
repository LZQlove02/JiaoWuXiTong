package com.lzpu.jwxt;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 原生（NDK）崩溃记录：Android 11+ 从系统 ApplicationExitInfo 读取上次原生崩溃的
 * 退出原因与 tombstone（protobuf），解析后写入应用私有目录。
 *
 * 不接入第三方 SDK、不联网、不安装信号处理器；Android 10 及以下没有公开接口，保持不记录。
 */
public final class NativeCrashLog {

    private static final String DIR = "diagnostics";
    private static final String FILE = "last-native-crash.txt";
    private static final String PREFS = "lzpu_diagnostics";
    private static final String KEY_CAPTURED = "native_captured_at";
    private static final int MAX_TRACE = 1024 * 1024;
    private static final int MAX_CHARS = 8 * 1024;
    private static final int MAX_SUMMARY = 2000;

    private NativeCrashLog() {
    }

    /** 启动时异步抓取上次进程的原生崩溃；首次运行或无记录时不会产生文件。 */
    public static void captureAsync(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    capture(app);
                } catch (Throwable ignored) {
                    // 读取退出信息失败不能影响启动
                }
            }
        }, "native-crash-capture").start();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void capture(Context context) {
        ActivityManager manager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        List<ApplicationExitInfo> exits =
                manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, 5);
        if (exits == null) return;

        long captured = prefs(context).getLong(KEY_CAPTURED, 0L);
        ApplicationExitInfo latest = null;
        for (ApplicationExitInfo info : exits) {
            if (info.getReason() != ApplicationExitInfo.REASON_CRASH_NATIVE) continue;
            if (info.getTimestamp() <= captured) continue;
            if (latest == null || info.getTimestamp() > latest.getTimestamp()) latest = info;
        }
        if (latest == null) return;

        byte[] trace = readTrace(latest);
        write(context, latest, trace);
        prefs(context).edit().putLong(KEY_CAPTURED, latest.getTimestamp()).apply();
    }

    public static String readSummary(Context context) {
        String text = readAll(context);
        if (text.isEmpty()) return "";
        if (text.length() > MAX_SUMMARY) {
            return text.substring(0, MAX_SUMMARY) + "\n…（已截断）";
        }
        return text;
    }

    public static void clear(Context context) {
        File target = file(context);
        if (target.exists()) target.delete();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File file(Context context) {
        return new File(new File(context.getFilesDir(), DIR), FILE);
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static byte[] readTrace(ApplicationExitInfo info) {
        InputStream input = null;
        try {
            input = info.getTraceInputStream();
            if (input == null) return null;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_TRACE) break;
            }
            return output.toByteArray();
        } catch (Throwable error) {
            return null;
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // 关闭失败忽略
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static void write(Context context, ApplicationExitInfo exit, byte[] trace) {
        TombstoneParser.Info info = null;
        if (trace != null && trace.length > 0) {
            try {
                info = TombstoneParser.parse(trace);
            } catch (Throwable ignored) {
                // 解析失败时仍记录退出原因
            }
        }

        String timestamp = info != null && !info.timestamp.isEmpty()
                ? info.timestamp
                : stamp(exit.getTimestamp());
        String description = exit.getDescription();

        StringBuilder text = new StringBuilder();
        text.append("类型：原生崩溃（NDK）")
                .append("\n时间：").append(timestamp)
                .append("\n应用：").append(versionLabel(context))
                .append("\n设备：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\n系统：Android ").append(Build.VERSION.RELEASE)
                .append("（API ").append(Build.VERSION.SDK_INT).append('）')
                .append("\n退出原因：REASON_CRASH_NATIVE")
                .append("\n描述：")
                .append(description == null || description.isEmpty() ? "未知" : description)
                .append('\n');
        if (info != null) {
            text.append(TombstoneParser.toText(info));
        } else {
            text.append("堆栈：\n系统未提供\n");
        }

        String content = CrashLog.sanitize(text.toString());
        if (content.length() > MAX_CHARS) content = content.substring(0, MAX_CHARS);

        File target = file(context);
        File dir = target.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) return;
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 写不进去就放弃，不影响启动
        }
    }

    private static String stamp(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
    }

    private static String versionLabel(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0).versionName;
            return name == null || name.isEmpty() ? "未知" : "v" + name;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知";
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
}
