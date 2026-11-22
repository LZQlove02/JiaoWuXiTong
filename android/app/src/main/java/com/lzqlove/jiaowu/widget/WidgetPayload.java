package com.lzqlove.jiaowu.widget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 组件数据模型：解析 JS 侧写入的 payload JSON。
 * 解析失败一律退回「未登录」占位，保证组件不崩、不显示脏数据。
 *
 * 只解析三个组件真正消费的字段：4x4 用 week[]、4x2 用 today[]、2x2 用 next + remainingToday。
 * messages / notices / grades 在 4x4 摘要移除后已无消费者，不再解析（见《小组件执行方案》§7.3）。
 */
public final class WidgetPayload {

    public boolean loggedIn;
    public boolean scheduleLoaded;
    /** 课表变更未读：由 JS 与原生变更引擎写入，组件只读。 */
    public boolean changeBadge;
    public String updatedLabel = "";
    /** updatedAtLabel 对应的本地日期（yyyy-MM-dd），用来把「更新于」算成相对时间；老 payload 为空。 */
    public String updatedDate = "";
    public int termWeek;
    /** week[] 代表的那一周的周一（yyyy-MM-dd），用于判断快照是否已经跨周。 */
    public String weekAnchor = "";
    /** true = 快照所属周次早于当前周，today/next 已不可信，只提示回 App 同步。 */
    public boolean stale;
    public int remainingToday;
    public ClassItem next;
    public final List<ClassItem> today = new ArrayList<>();
    public final List<WeekItem> week = new ArrayList<>();
    /** 下一周的周次；0 = 未知（学期末或老 payload），此时 nextWeek 为空。 */
    public int nextWeekNo;
    public final List<WeekItem> nextWeek = new ArrayList<>();

    public static final class ClassItem {
        public String dayLabel = "";
        public int start;
        public int end;
        /** 1=周一 … 7=周日；0 表示老 payload 没带这个字段。 */
        public int weekday;
        public String startTime = "";
        public String endTime = "";
        public String name = "";
        public String room = "";

        public String timeRange() {
            if (startTime.isEmpty()) return endTime;
            if (endTime.isEmpty()) return startTime;
            return startTime + "-" + endTime;
        }

        public boolean isToday() {
            return dayLabel.isEmpty() || "今天".equals(dayLabel);
        }
    }

    public static final class WeekItem {
        public int weekday;
        public int start;
        public int end;
        public String startTime = "";
        public String endTime = "";
        public String name = "";
        public String room = "";
    }

    public static WidgetPayload loggedOut() {
        return new WidgetPayload();
    }

    public static WidgetPayload parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return loggedOut();
        }
        try {
            JSONObject root = new JSONObject(json);
            WidgetPayload payload = new WidgetPayload();
            payload.loggedIn = root.optBoolean("loggedIn", false);
            payload.scheduleLoaded = root.optBoolean("scheduleLoaded", false);
            payload.changeBadge = root.optBoolean("changeBadge", false);
            payload.updatedLabel = root.optString("updatedAtLabel", "");
            payload.updatedDate = root.optString("updatedAtDate", "");
            payload.weekAnchor = root.optString("weekAnchor", "");
            payload.stale = root.optBoolean("stale", false);

            JSONObject term = root.optJSONObject("term");
            if (term != null) {
                payload.termWeek = term.optInt("week", 0);
            }

            JSONArray today = root.optJSONArray("today");
            if (today != null) {
                for (int i = 0; i < today.length(); i += 1) {
                    JSONObject item = today.optJSONObject(i);
                    if (item != null) payload.today.add(readClass(item));
                }
            }

            JSONObject next = root.optJSONObject("next");
            if (next != null) {
                payload.next = readClass(next);
            }

            payload.remainingToday = root.optInt("remainingToday", 0);

            readWeeks(payload.week, root.optJSONArray("week"));

            // 下一周：4x4 的「下周」页用；老 payload 没有这个对象，按钮退化为只读
            JSONObject nextWeek = root.optJSONObject("nextWeek");
            if (nextWeek != null) {
                payload.nextWeekNo = nextWeek.optInt("week", 0);
                readWeeks(payload.nextWeek, nextWeek.optJSONArray("items"));
            }
            return payload;
        } catch (Exception error) {
            return loggedOut();
        }
    }

    private static void readWeeks(List<WeekItem> target, JSONArray array) {
        if (target == null || array == null) return;
        for (int i = 0; i < array.length(); i += 1) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            WeekItem entry = new WeekItem();
            entry.weekday = item.optInt("weekday", 0);
            entry.start = item.optInt("start", 0);
            entry.end = item.optInt("end", 0);
            entry.startTime = item.optString("startTime", "");
            entry.endTime = item.optString("endTime", "");
            entry.name = item.optString("name", "");
            entry.room = item.optString("room", "");
            target.add(entry);
        }
    }

    private static ClassItem readClass(JSONObject item) {
        ClassItem entry = new ClassItem();
        entry.dayLabel = item.optString("dayLabel", "");
        entry.start = item.optInt("start", 0);
        entry.end = item.optInt("end", 0);
        entry.weekday = item.optInt("weekday", 0);
        entry.startTime = item.optString("startTime", "");
        entry.endTime = item.optString("endTime", "");
        entry.name = item.optString("name", "");
        entry.room = item.optString("room", "");
        return entry;
    }

    /** 跨周降级：清空今日、下一节与下一周，只留 stale 标记，三个组件统一提示回 App 同步。 */
    public void markStale() {
        stale = true;
        next = null;
        remainingToday = 0;
        today.clear();
        nextWeekNo = 0;
        nextWeek.clear();
    }
}