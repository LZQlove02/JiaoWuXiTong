package com.lzpu.jwxt.change;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 一条课表变更记录。类型只有四种：停课、调课（含周次调整）、新增、换教室。
 * 换教室只进历史，不弹提醒；对应上课时间已过的记为 quiet，也不弹提醒。
 */
public final class ChangeEntry {

    public static final String TYPE_CANCEL = "cancel";
    public static final String TYPE_MOVE = "move";
    public static final String TYPE_ADD = "add";
    public static final String TYPE_ROOM = "room";

    public String id = "";
    public String type = "";
    public String courseId = "";
    public String courseName = "";
    public Snapshot from;
    public Snapshot to;
    public String detectedAt = "";
    public String detectedBy = "foreground";
    public boolean quiet;
    public boolean read;
    public boolean notified;

    public static final class Snapshot {
        public int weekday;
        public int startSection;
        public int endSection;
        public String room = "";
        public String weeksLabel = "";

        public Snapshot() {
        }

        public Snapshot(int weekday, int startSection, int endSection, String room, String weeksLabel) {
            this.weekday = weekday;
            this.startSection = startSection;
            this.endSection = endSection;
            this.room = room == null ? "" : room;
            this.weeksLabel = weeksLabel == null ? "" : weeksLabel;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("weekday", weekday);
            json.put("startSection", startSection);
            json.put("endSection", endSection);
            json.put("room", room);
            json.put("weeksLabel", weeksLabel);
            return json;
        }

        public static Snapshot fromJson(JSONObject json) {
            if (json == null) return null;
            return new Snapshot(
                    json.optInt("weekday", 1),
                    json.optInt("startSection", 1),
                    json.optInt("endSection", 1),
                    json.optString("room"),
                    json.optString("weeksLabel"));
        }

        public String key() {
            return weekday + "|" + startSection + "|" + endSection + "|" + weeksLabel + "|" + room;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("type", type);
        json.put("courseId", courseId);
        json.put("courseName", courseName);
        json.put("from", from == null ? JSONObject.NULL : from.toJson());
        json.put("to", to == null ? JSONObject.NULL : to.toJson());
        json.put("detectedAt", detectedAt);
        json.put("detectedBy", detectedBy);
        json.put("quiet", quiet);
        json.put("read", read);
        json.put("notified", notified);
        return json;
    }

    public static ChangeEntry fromJson(JSONObject json) {
        if (json == null) return null;
        ChangeEntry entry = new ChangeEntry();
        entry.id = json.optString("id");
        entry.type = json.optString("type");
        entry.courseId = json.optString("courseId");
        entry.courseName = json.optString("courseName");
        entry.from = Snapshot.fromJson(json.optJSONObject("from"));
        entry.to = Snapshot.fromJson(json.optJSONObject("to"));
        entry.detectedAt = json.optString("detectedAt");
        entry.detectedBy = json.optString("detectedBy", "foreground");
        entry.quiet = json.optBoolean("quiet");
        entry.read = json.optBoolean("read");
        entry.notified = json.optBoolean("notified");
        return entry;
    }

    public boolean isRemindable() {
        return !quiet && !TYPE_ROOM.equals(type);
    }
}