package com.lzqlove.jiaowu.change;

import android.content.Context;

import org.json.JSONException;

import java.util.List;
import java.util.Set;

/**
 * 新通知/新消息编排：首次检查静默建基线，之后只对基线里没有的 ID 发摘要通知。
 */
public final class PortalAlertService {

    private static final String NOTICE_URL =
            "https://zfjw.lzzy.net/xtgl/xwck_cxMoreXwList.html?doType=query";
    private static final String MESSAGE_URL =
            "https://zfjw.lzzy.net/xtgl/index_cxDbsy.html?doType=query";
    private static final String NOTICE_BODY =
            "queryModel.showCount=20&queryModel.currentPage=1";
    private static final String MESSAGE_BODY =
            "flag=1&sfyy=1&queryModel.showCount=20&queryModel.currentPage=1";

    private PortalAlertService() {
    }

    public static void checkNow(Context context) {
        Context app = context.getApplicationContext();
        boolean noticesEnabled = PortalAlertStore.isNoticesEnabled(app);
        boolean messagesEnabled = PortalAlertStore.isMessagesEnabled(app);
        if (!noticesEnabled && !messagesEnabled) return;

        // 换账号时静默重建基线，避免把上一账号的内容当作新消息
        String storedStudent = PortalAlertStore.readStudentId(app);
        ScheduleChangeStore.Baseline schedule = ScheduleChangeStore.readBaseline(app);
        String studentId = schedule == null ? "" : schedule.studentId;
        if (!studentId.equals(storedStudent)) {
            PortalAlertStore.clearBaselines(app);
            PortalAlertStore.writeStudentId(app, studentId);
        }

        int newNotices = noticesEnabled
                ? checkType(app, true) : 0;
        int newMessages = messagesEnabled
                ? checkType(app, false) : 0;
        if (newNotices == 0 && newMessages == 0) return;
        if (AppVisibility.isForeground()) return;
        PortalAlertNotifier.notifyUpdates(app, newNotices, newMessages);
    }

    private static int checkType(Context context, boolean notices) {
        String raw = PortalFetcher.fetch(
                context,
                notices ? NOTICE_URL : MESSAGE_URL,
                notices ? NOTICE_BODY : MESSAGE_BODY);
        if (raw == null) return 0;

        List<String> ids;
        try {
            ids = notices ? PortalParser.parseNoticeIds(raw) : PortalParser.parseMessageIds(raw);
        } catch (JSONException error) {
            ChangeDebug.warn(context, "门户提醒解析失败: " + error.getClass().getSimpleName());
            return 0;
        }

        if (!PortalAlertStore.isReady(context, notices)) {
            PortalAlertStore.writeRecentIds(context, notices, ids);
            PortalAlertStore.setReady(context, notices, true);
            return 0;
        }

        Set<String> previous = PortalAlertStore.readIds(context, notices);
        List<String> fresh = PortalAlertEngine.newIds(previous, ids);
        PortalAlertStore.writeRecentIds(context, notices, ids);
        return fresh.size();
    }

    public static void setEnabled(Context context, boolean notices, boolean enabled) {
        Context app = context.getApplicationContext();
        if (notices) {
            PortalAlertStore.setNoticesEnabled(app, enabled);
        } else {
            PortalAlertStore.setMessagesEnabled(app, enabled);
        }
        if (enabled) {
            // 开启只关注之后的新内容，避免把关闭期间的旧内容补发出来
            PortalAlertStore.resetType(app, notices);
            ScheduleCheckScheduler.scheduleAll(app);
        } else {
            PortalAlertNotifier.cancel(app);
            if (!BackgroundCheckPolicy.isAnyEnabled(app)) {
                ScheduleCheckScheduler.cancelAll(app);
            }
        }
    }

    public static void clearForLogout(Context context) {
        Context app = context.getApplicationContext();
        PortalAlertNotifier.cancel(app);
        PortalAlertStore.clearBaselines(app);
    }
}
