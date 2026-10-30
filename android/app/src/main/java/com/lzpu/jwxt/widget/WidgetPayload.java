package com.lzpu.jwxt.widget;

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
    public String updatedLabel = "";
    public int termWeek;
    public int remainingToday;
    public ClassItem next;
    public final List<ClassItem> today = new ArrayList<>();
    public final List<WeekItem> week = new ArrayList<>();

    public static final class ClassItem {
        public String dayLabel = "";
        public int start;
        public int end;
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
            payload.updatedLabel = root.optString("updatedAtLabel", "");

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

            JSONArray week = root.optJSONArray("week");
            if (week != null) {
                for (int i = 0; i < week.length(); i += 1) {
                    JSONObject item = week.optJSONObject(i);
                    if (item == null) continue;
                    WeekItem entry = new WeekItem();
                    entry.weekday = item.optInt("weekday", 0);
                    entry.start = item.optInt("start", 0);
                    entry.end = item.optInt("end", 0);
                    entry.name = item.optString("name", "");
                    entry.room = item.optString("room", "");
                    payload.week.add(entry);
                }
            }
            return payload;
        } catch (Exception error) {
            return loggedOut();
        }
    }

    private static ClassItem readClass(JSONObject item) {
        ClassItem entry = new ClassItem();
        entry.dayLabel = item.optString("dayLabel", "");
        entry.start = item.optInt("start", 0);
        entry.end = item.optInt("end", 0);
        entry.startTime = item.optString("startTime", "");
        entry.endTime = item.optString("endTime", "");
        entry.name = item.optString("name", "");
        entry.room = item.optString("room", "");
        return entry;
    }
}