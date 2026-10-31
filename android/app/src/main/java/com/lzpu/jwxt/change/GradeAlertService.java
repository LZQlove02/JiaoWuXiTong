package com.lzpu.jwxt.change;

import android.content.Context;

import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * 成绩提醒编排：用课表基线里的学期与学号抓成绩，首次静默建基线，
 * 之后只对「新变为不合格」的课程发通知。
 */
public final class GradeAlertService {

    private GradeAlertService() {
    }

    public static void checkNow(Context context) {
        Context app = context.getApplicationContext();
        if (!GradeAlertStore.isEnabled(app)) return;

        ScheduleChangeStore.Baseline schedule = ScheduleChangeStore.readBaseline(app);
        if (schedule == null || schedule.term == null || !schedule.term.isValid()) return;

        GradeFetcher.Result fetched = GradeFetcher.fetch(app, schedule.term);
        if (!fetched.ok) return;

        GradeAlertStore.Baseline baseline = GradeAlertStore.readBaseline(app);
        Map<String, String> current = GradeAlertEngine.statusMap(fetched.records);
        boolean sameOwner = baseline != null
                && baseline.term != null
                && baseline.term.sameAs(schedule.term)
                && baseline.studentId.equals(schedule.studentId);

        // 首次启用、换学期或换账号：只建立基线，不补发历史不及格
        if (!sameOwner) {
            writeBaseline(app, schedule.term, schedule.studentId, current);
            return;
        }

        List<GradeRecord> failures = GradeAlertEngine.newFailures(baseline.courses, fetched.records);
        writeBaseline(app, schedule.term, schedule.studentId, current);

        if (failures.isEmpty()) return;
        // 前台不弹系统通知，避免打断当前操作；基线已更新，不会在下次检查重复提醒
        if (AppVisibility.isForeground()) return;
        GradeAlertNotifier.notifyFailures(app, failures);
    }

    public static void setEnabled(Context context, boolean enabled) {
        Context app = context.getApplicationContext();
        GradeAlertStore.setEnabled(app, enabled);
        if (enabled) {
            // 重新开启只关注之后的新成绩，避免把关闭期间的旧不及格补发出来
            GradeAlertStore.clearBaseline(app);
            ScheduleCheckScheduler.scheduleAll(app);
        } else {
            GradeAlertNotifier.cancelAll(app);
            if (!BackgroundCheckPolicy.isAnyEnabled(app)) {
                ScheduleCheckScheduler.cancelAll(app);
            }
        }
    }

    /** 退出登录：先取消通知，再清基线，避免通知栏残留上一账号的成绩提醒。 */
    public static void clearForLogout(Context context) {
        Context app = context.getApplicationContext();
        GradeAlertNotifier.cancelAll(app);
        GradeAlertStore.clearBaseline(app);
    }

    private static void writeBaseline(Context context, TermInfo term, String studentId,
                                      Map<String, String> courses) {
        GradeAlertStore.writeBaseline(context, term, studentId, courses,
                DateUtils.isoNow(Calendar.getInstance()));
    }
}
