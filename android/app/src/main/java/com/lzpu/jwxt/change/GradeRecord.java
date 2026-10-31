package com.lzpu.jwxt.change;

/**
 * 一门课的成绩状态。只保存合格 / 不合格 / 待公布，不保存分数明细。
 */
public final class GradeRecord {

    public static final String STATUS_PASS = "pass";
    public static final String STATUS_FAIL = "fail";
    public static final String STATUS_PENDING = "pending";

    public final String key;
    public final String courseName;
    public final String status;
    public final String score;

    public GradeRecord(String key, String courseName, String status, String score) {
        this.key = key == null ? "" : key;
        this.courseName = courseName == null ? "" : courseName;
        this.status = status == null ? STATUS_PENDING : status;
        this.score = score == null ? "" : score;
    }

    public boolean isFailed() {
        return STATUS_FAIL.equals(status);
    }
}
