package com.lzpu.jwxt.change;

import android.content.Context;

import com.lzpu.jwxt.widget.WidgetStore;
import com.lzpu.jwxt.widget.WidgetUpdater;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 变更业务编排：解析、对账、落盘、通知、闹钟、组件刷新都在这里串起来。
 * 前台（Bridge）与后台（Receiver）走同一条对账路径，保证不重复、不遗漏。
 */
public final class ScheduleChangeService {

    private static final Object LOCK = new Object();

    private ScheduleChangeService() {
    }

    /** 基线决策：与 Android 依赖分离，便于单测。 */
    enum BaselineAction {
        RESET,
        SKIP_EMPTY,
        DIFF
    }

    public static final class Result {
        public final boolean ok;
        public final List<ChangeEntry> newEntries;
        public final int unread;
        public final boolean enabled;

        private Result(boolean ok, List<ChangeEntry> newEntries, int unread, boolean enabled) {
            this.ok = ok;
            this.newEntries = newEntries == null ? new ArrayList<>() : newEntries;
            this.unread = unread;
            this.enabled = enabled;
        }

        static Result failed() {
            return new Result(false, new ArrayList<>(), 0, false);
        }

        static Result success(List<ChangeEntry> entries, int unread, boolean enabled) {
            return new Result(true, entries, unread, enabled);
        }
    }

    static BaselineAction decide(ScheduleChangeStore.Baseline baseline, TermInfo term,
                                 String studentId, int newSessionCount) {
        String owner = studentId == null ? "" : studentId;
        if (baseline == null || baseline.term == null || !baseline.term.sameAs(term)
                || !owner.equals(baseline.studentId)) {
            return BaselineAction.RESET;
        }
        // 接口异常（新课表为空）：保留旧基线，避免误报「全部停课」
        if (newSessionCount == 0 && !baseline.sessions.isEmpty()) {
            return BaselineAction.SKIP_EMPTY;
        }
        return BaselineAction.DIFF;
    }

    /** 前台对账：raw 是实时课表接口的原始 JSON，term 来自 JS 当前学期。 */
    public static Result apply(Context context, String raw, TermInfo term, String studentId,
                               Map<Integer, String> sectionTimes, String detectedBy) {
        return apply(context, raw, term, studentId, sectionTimes, detectedBy, null);
    }

    /**
     * @param expectedSavedAt 非空时要求当前基线的 savedAt 未变化；后台拉取期间前台已对账过就跳过，
     *                        避免拿旧响应覆盖新基线、报出反向变更。
     */
    static Result apply(Context context, String raw, TermInfo term, String studentId,
                        Map<Integer, String> sectionTimes, String detectedBy,
                        String expectedSavedAt) {
        if (context == null || raw == null || raw.trim().isEmpty() || term == null || !term.isValid()) {
            return Result.failed();
        }
        Context app = context.getApplicationContext();
        String owner = studentId == null ? "" : studentId;
        Map<Integer, String> times = sectionTimes == null || sectionTimes.isEmpty()
                ? ScheduleChangeStore.defaultSectionTimes()
                : sectionTimes;
        synchronized (LOCK) {
            try {
                List<ScheduleSession> sessions = ScheduleParser.parse(raw);
                ScheduleChangeStore.Baseline baseline = ScheduleChangeStore.readBaseline(app);

                // 后台拉取期间前台已经用更新的课表对过账：本轮响应已过时，直接丢弃
                if (expectedSavedAt != null && baseline != null
                        && !expectedSavedAt.equals(baseline.savedAt)) {
                    return Result.failed();
                }

                BaselineAction action = decide(baseline, term, owner, sessions.size());
                if (action == BaselineAction.SKIP_EMPTY) {
                    ChangeDebug.warn(app, "新课表为空，跳过本轮对账");
                    return Result.success(new ArrayList<>(),
                            ScheduleChangeStore.unreadCount(app), ScheduleChangeStore.isEnabled(app));
                }
                if (action == BaselineAction.RESET) {
                    // 换账号时不展示上一个人的变更历史
                    if (baseline != null && !owner.equals(baseline.studentId)) {
                        ScheduleChangeStore.writeEntries(app, new ArrayList<>(), Calendar.getInstance());
                        WidgetStore.markChangeBadge(app, false);
                        WidgetUpdater.refreshAll(app);
                    }
                    writeBaseline(app, term, owner, sessions, times);
                    return Result.success(new ArrayList<>(), 0, ScheduleChangeStore.isEnabled(app));
                }

                List<ChangeEntry> detected = ScheduleChangeEngine.diff(
                        baseline.sessions, sessions, term, times,
                        Calendar.getInstance(), detectedBy);

                List<ChangeEntry> log = ScheduleChangeStore.readEntries(app);
                Set<String> existing = new HashSet<>();
                for (ChangeEntry entry : log) existing.add(entry.id);
                List<ChangeEntry> fresh = new ArrayList<>();
                for (ChangeEntry entry : detected) {
                    if (existing.add(entry.id)) fresh.add(entry);
                }
                if (!fresh.isEmpty()) {
                    log.addAll(fresh);
                    ScheduleChangeStore.writeEntries(app, log, Calendar.getInstance());
                }
                writeBaseline(app, term, owner, sessions, times);

                int unread = ScheduleChangeStore.unreadCount(app);
                boolean badgeChanged = WidgetStore.markChangeBadge(app, unread > 0);
                if (!fresh.isEmpty() || badgeChanged) {
                    WidgetUpdater.refreshAll(app);
                }
                return Result.success(fresh, unread, ScheduleChangeStore.isEnabled(app));
            } catch (Exception error) {
                // 解析失败不能影响课表展示，静默跳过本轮；异常类名足够定位，不记录响应内容
                ChangeDebug.warn(app, "课表对账失败: " + error.getClass().getSimpleName());
                return Result.failed();
            }
        }
    }

    private static void writeBaseline(Context app, TermInfo term, String studentId,
                                      List<ScheduleSession> sessions,
                                      Map<Integer, String> times) {
        ScheduleChangeStore.Baseline baseline = new ScheduleChangeStore.Baseline();
        baseline.term = term;
        baseline.studentId = studentId;
        baseline.sessions = sessions;
        baseline.sectionTimes = times;
        // 带毫秒，保证同一秒内前后两次对账也能区分新旧
        baseline.savedAt = DateUtils.isoNow(Calendar.getInstance())
                + "." + (System.currentTimeMillis() % 1000);
        ScheduleChangeStore.writeBaseline(app, baseline);
    }

    /** 后台闹钟入口：拉课表 → 对账 → 需要时发通知。 */
    public static void checkNow(Context context) {
        Context app = context.getApplicationContext();
        if (!ScheduleChangeStore.isEnabled(app)) return;
        ScheduleChangeStore.Baseline baseline = ScheduleChangeStore.readBaseline(app);
        if (baseline == null || baseline.term == null) return;

        String expectedSavedAt = baseline.savedAt;
        ScheduleFetcher.Result fetched = ScheduleFetcher.fetch(app, baseline.term);
        if (fetched.sessionExpired) {
            ScheduleChangeNotifier.notifySessionExpired(app);
            return;
        }
        if (!fetched.ok) return;

        // 后台不掌握当前登录态，沿用基线归属；换账号会在下次前台对账时重置。
        boolean foreground = AppVisibility.isForeground();
        Result applied = apply(app, fetched.body, baseline.term, baseline.studentId,
                baseline.sectionTimes, "background", expectedSavedAt);
        if (!applied.ok) return;
        if (applied.newEntries.isEmpty()) return;

        if (!foreground) {
            ScheduleChangeNotifier.notifyChanges(app, applied.newEntries);
        } else {
            ScheduleChangeBridge.emitChanged();
        }
        WidgetUpdater.refreshAll(app);
    }

    public static void markAllRead(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            ScheduleChangeStore.markAllRead(app);
            WidgetStore.markChangeBadge(app, false);
            ScheduleChangeNotifier.cancelAll(app);
            WidgetUpdater.refreshAll(app);
        }
    }

    public static void setEnabled(Context context, boolean enabled) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            ScheduleChangeStore.setEnabled(app, enabled);
            if (enabled) {
                ScheduleCheckScheduler.scheduleAll(app);
            } else {
                ScheduleCheckScheduler.cancelAll(app);
                ScheduleChangeNotifier.cancelAll(app);
                WidgetStore.markChangeBadge(app, false);
            }
            WidgetUpdater.refreshAll(app);
        }
    }

    /** 退出登录：清空基线与历史，避免下一个账号看到上一个人的课表信息。 */
    public static void clearForLogout(Context context) {
        Context app = context.getApplicationContext();
        synchronized (LOCK) {
            // 先按现有条目取消通知，再清日志，否则通知栏会留下旧提醒
            ScheduleChangeNotifier.cancelAll(app);
            ScheduleChangeStore.clearBaseline(app);
            ScheduleChangeStore.writeEntries(app, new ArrayList<>(), Calendar.getInstance());
            ScheduleCheckScheduler.cancelAll(app);
            WidgetStore.markChangeBadge(app, false);
            WidgetUpdater.refreshAll(app);
        }
    }

    /** 给 JS 的状态快照：条目、未读数、开关、通知权限。 */
    public static JSONObject state(Context context) {
        Context app = context.getApplicationContext();
        JSONObject state = new JSONObject();
        try {
            state.put("enabled", ScheduleChangeStore.isEnabled(app));
            state.put("unread", ScheduleChangeStore.unreadCount(app));
            state.put("notificationsEnabled", ScheduleChangeNotifier.notificationsEnabled(app));
            JSONArray entries = new JSONArray();
            synchronized (LOCK) {
                for (ChangeEntry entry : ScheduleChangeStore.readEntries(app)) {
                    entries.put(entry.toJson());
                }
            }
            state.put("entries", entries);
        } catch (JSONException error) {
            ChangeDebug.warn(app, "状态序列化失败");
        }
        return state;
    }
}