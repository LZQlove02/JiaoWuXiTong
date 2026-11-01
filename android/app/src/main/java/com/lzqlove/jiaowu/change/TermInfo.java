package com.lzqlove.jiaowu.change;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 学期信息：接口参数 + 开学日期。变更引擎只用 startDate 推算当前周。
 */
public final class TermInfo {

    public final String xnm;
    public final String xqm;
    public final String startDate;

    public TermInfo(String xnm, String xqm, String startDate) {
        this.xnm = xnm == null ? "" : xnm;
        this.xqm = xqm == null ? "" : xqm;
        this.startDate = startDate == null ? "" : startDate;
    }

    public boolean sameAs(TermInfo other) {
        return other != null && xnm.equals(other.xnm) && xqm.equals(other.xqm);
    }

    public boolean isValid() {
        return !xnm.isEmpty() && !xqm.isEmpty() && DateUtils.parseDate(startDate) != null;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("xnm", xnm);
        json.put("xqm", xqm);
        json.put("startDate", startDate);
        return json;
    }

    public static TermInfo fromJson(JSONObject json) {
        if (json == null) return null;
        return new TermInfo(json.optString("xnm"), json.optString("xqm"), json.optString("startDate"));
    }
}