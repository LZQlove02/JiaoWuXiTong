package com.lzpu.jwxt.change;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 纯函数 diff：旧会话 + 新会话 + 当前周 + 当前时间 → 本周变更条目。
 *
 * 判定顺序：先在全量会话上配对分类，再按「本周」过滤；反过来会把
 * 「周次调整」误判成停课。配对规则见设计文档 §4.3。
 */
public final class ScheduleChangeEngine {

    private ScheduleChangeEngine() {
    }

    public static List<ChangeEntry> diff(List<ScheduleSession> oldSessions,
                                         List<ScheduleSession> newSessions,
                                         TermInfo term,
                                         Map<Integer, String> sectionTimes,
                                         Calendar now,
                                         String detectedBy) {
        List<ChangeEntry> entries = new ArrayList<>();
        // 首次基线 / 接口异常（新课表为空）都不产生变更，避免误报「全部停课」
        if (oldSessions == null || oldSessions.isEmpty()) return entries;
        if (newSessions == null || newSessions.isEmpty()) return entries;

        int currentWeek = DateUtils.currentWeek(term == null ? "" : term.startDate, now);
        Map<String, List<ScheduleSession>> oldByCourse = groupByCourse(oldSessions);
        Map<String, List<ScheduleSession>> newByCourse = groupByCourse(newSessions);
        TreeSet<String> courseIds = new TreeSet<>(oldByCourse.keySet());
        courseIds.addAll(newByCourse.keySet());

        List<Candidate> candidates = new ArrayList<>();
        for (String courseId : courseIds) {
            List<ScheduleSession> olds = new ArrayList<>(oldByCourse.getOrDefault(courseId, new ArrayList<>()));
            List<ScheduleSession> news = new ArrayList<>(newByCourse.getOrDefault(courseId, new ArrayList<>()));
            classify(courseId, olds, news, candidates);
        }

        for (Candidate candidate : candidates) {
            ScheduleSession reference = referenceOf(candidate, currentWeek);
            if (reference == null || !reference.containsWeek(currentWeek)) continue;
            Calendar occurrence = DateUtils.occurrenceInCurrentWeek(now, reference.weekday);
            String range = sectionTimes == null ? null : sectionTimes.get(reference.endSection);
            ChangeEntry entry = buildEntry(candidate, term, reference, occurrence, range, now, detectedBy);
            entries.add(entry);
        }
        entries.sort(entryOrder());
        return entries;
    }

    private static Map<String, List<ScheduleSession>> groupByCourse(List<ScheduleSession> sessions) {
        Map<String, List<ScheduleSession>> grouped = new TreeMap<>();
        for (ScheduleSession session : sessions) {
            if (session == null) continue;
            grouped.computeIfAbsent(session.courseId, key -> new ArrayList<>()).add(session);
        }
        return grouped;
    }

    private static void classify(String courseId, List<ScheduleSession> olds,
                                 List<ScheduleSession> news, List<Candidate> candidates) {
        List<ScheduleSession> remainingOld = new ArrayList<>();
        List<ScheduleSession> remainingNew = new ArrayList<>(news);

        // 1. 完全相同的判定键：未变化；只有教室不同时记一条 room
        for (ScheduleSession old : olds) {
            int match = indexOfKey(remainingNew, old.key());
            if (match >= 0) {
                ScheduleSession found = remainingNew.remove(match);
                if (!found.room.equals(old.room)) {
                    candidates.add(Candidate.room(courseId, old, found));
                }
            } else {
                remainingOld.add(old);
            }
        }

        // 2. 同课程 + 相同周次：按星期/节次距离配对 → 调课
        pairByCost(courseId, remainingOld, remainingNew, true, candidates);
        // 3. 仍剩余的同课程一对一配对（周次也不同）→ 调课（改时间同时改周次）
        pairByCost(courseId, remainingOld, remainingNew, false, candidates);

        // 4. 剩余：旧 = 停课，新 = 新增
        for (ScheduleSession old : remainingOld) {
            candidates.add(new Candidate(ChangeEntry.TYPE_CANCEL, courseId, old.courseName, old, null));
        }
        for (ScheduleSession added : remainingNew) {
            candidates.add(new Candidate(ChangeEntry.TYPE_ADD, courseId, added.courseName, null, added));
        }
    }

    private static int indexOfKey(List<ScheduleSession> sessions, String key) {
        for (int i = 0; i < sessions.size(); i += 1) {
            if (sessions.get(i).key().equals(key)) return i;
        }
        return -1;
    }

    private static void pairByCost(String courseId, List<ScheduleSession> olds,
                                   List<ScheduleSession> news, boolean sameWeeksOnly,
                                   List<Candidate> candidates) {
        if (olds.isEmpty() || news.isEmpty()) return;
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < olds.size(); i += 1) {
            for (int j = 0; j < news.size(); j += 1) {
                ScheduleSession old = olds.get(i);
                ScheduleSession fresh = news.get(j);
                if (sameWeeksOnly && !old.weeksSignature().equals(fresh.weeksSignature())) continue;
                int cost = Math.abs(old.weekday - fresh.weekday) * 10
                        + Math.abs(old.startSection - fresh.startSection);
                if (!sameWeeksOnly && !old.weeksSignature().equals(fresh.weeksSignature())) {
                    cost += 100;
                }
                pairs.add(new Pair(cost, i, j));
            }
        }
        pairs.sort(Comparator.comparingInt((Pair pair) -> pair.cost)
                .thenComparingInt(pair -> pair.oldIndex)
                .thenComparingInt(pair -> pair.newIndex));

        boolean[] usedOld = new boolean[olds.size()];
        boolean[] usedNew = new boolean[news.size()];
        for (Pair pair : pairs) {
            if (usedOld[pair.oldIndex] || usedNew[pair.newIndex]) continue;
            usedOld[pair.oldIndex] = true;
            usedNew[pair.newIndex] = true;
            ScheduleSession old = olds.get(pair.oldIndex);
            ScheduleSession fresh = news.get(pair.newIndex);
            candidates.add(new Candidate(ChangeEntry.TYPE_MOVE, courseId,
                    fresh.courseName.isEmpty() ? old.courseName : fresh.courseName, old, fresh));
        }

        List<ScheduleSession> oldLeft = new ArrayList<>();
        List<ScheduleSession> newLeft = new ArrayList<>();
        for (int i = 0; i < olds.size(); i += 1) {
            if (!usedOld[i]) oldLeft.add(olds.get(i));
        }
        for (int j = 0; j < news.size(); j += 1) {
            if (!usedNew[j]) newLeft.add(news.get(j));
        }
        olds.clear();
        olds.addAll(oldLeft);
        news.clear();
        news.addAll(newLeft);
    }

    private static ScheduleSession referenceOf(Candidate candidate, int currentWeek) {
        switch (candidate.type) {
            case ChangeEntry.TYPE_CANCEL:
                return candidate.from;
            case ChangeEntry.TYPE_ADD:
                return candidate.to;
            case ChangeEntry.TYPE_ROOM:
                return candidate.from;
            case ChangeEntry.TYPE_MOVE:
            default:
                if (candidate.to != null && candidate.to.containsWeek(currentWeek)) return candidate.to;
                return candidate.from;
        }
    }

    private static ChangeEntry buildEntry(Candidate candidate, TermInfo term, ScheduleSession reference,
                                          Calendar occurrence, String sectionRange,
                                          Calendar now, String detectedBy) {
        ChangeEntry entry = new ChangeEntry();
        entry.id = idOf(candidate);
        entry.type = candidate.type;
        entry.courseId = candidate.courseId;
        entry.courseName = candidate.courseName;
        entry.from = snapshotOf(candidate.from);
        entry.to = snapshotOf(candidate.to);
        entry.detectedAt = DateUtils.isoNow(now);
        entry.detectedBy = detectedBy == null ? "foreground" : detectedBy;
        entry.quiet = DateUtils.isPast(occurrence, sectionRange, now);
        entry.read = false;
        entry.notified = false;
        return entry;
    }

    private static ChangeEntry.Snapshot snapshotOf(ScheduleSession session) {
        if (session == null) return null;
        return new ChangeEntry.Snapshot(
                session.weekday,
                session.startSection,
                session.endSection,
                session.room,
                session.weeksLabel());
    }

    private static String idOf(Candidate candidate) {
        String raw = candidate.type + "|" + candidate.courseId + "|"
                + (candidate.from == null ? "" : candidate.from.key())
                + "|"
                + (candidate.to == null ? "" : candidate.to.key())
                + "|"
                + (candidate.from == null ? "" : candidate.from.room)
                + "|"
                + (candidate.to == null ? "" : candidate.to.room);
        return "c_" + sha1(raw).substring(0, 12);
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format(java.util.Locale.US, "%02x", item));
            }
            return builder.toString();
        } catch (Exception error) {
            return String.format(java.util.Locale.US, "%012x", (long) value.hashCode());
        }
    }

    private static Comparator<ChangeEntry> entryOrder() {
        return (left, right) -> {
            int leftDay = left.from != null ? left.from.weekday : (left.to != null ? left.to.weekday : 0);
            int rightDay = right.from != null ? right.from.weekday : (right.to != null ? right.to.weekday : 0);
            if (leftDay != rightDay) return Integer.compare(leftDay, rightDay);
            int leftStart = left.from != null ? left.from.startSection
                    : (left.to != null ? left.to.startSection : 0);
            int rightStart = right.from != null ? right.from.startSection
                    : (right.to != null ? right.to.startSection : 0);
            if (leftStart != rightStart) return Integer.compare(leftStart, rightStart);
            return typePriority(left.type) - typePriority(right.type);
        };
    }

    private static int typePriority(String type) {
        if (ChangeEntry.TYPE_CANCEL.equals(type)) return 0;
        if (ChangeEntry.TYPE_MOVE.equals(type)) return 1;
        if (ChangeEntry.TYPE_ADD.equals(type)) return 2;
        return 3;
    }

    private static final class Pair {
        final int cost;
        final int oldIndex;
        final int newIndex;

        Pair(int cost, int oldIndex, int newIndex) {
            this.cost = cost;
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
        }
    }

    private static final class Candidate {
        final String type;
        final String courseId;
        final String courseName;
        final ScheduleSession from;
        final ScheduleSession to;

        Candidate(String type, String courseId, String courseName,
                  ScheduleSession from, ScheduleSession to) {
            this.type = type;
            this.courseId = courseId;
            this.courseName = courseName == null ? "" : courseName;
            this.from = from;
            this.to = to;
        }

        static Candidate room(String courseId, ScheduleSession old, ScheduleSession fresh) {
            return new Candidate(ChangeEntry.TYPE_ROOM, courseId, fresh.courseName, old, fresh);
        }
    }
}