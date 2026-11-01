package com.lzqlove.jiaowu.change;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 新内容判定：只返回当前列表里基线没有的 ID；首次基线由调用方静默写入。
 */
public final class PortalAlertEngine {

    private PortalAlertEngine() {
    }

    public static List<String> newIds(Set<String> previous, List<String> current) {
        List<String> result = new ArrayList<>();
        if (current == null) return result;
        Set<String> baseline = previous == null ? new HashSet<>() : previous;
        for (String id : current) {
            if (id != null && !id.isEmpty() && !baseline.contains(id)) result.add(id);
        }
        return result;
    }

    /** 保持当前列表顺序并截断到 50，防止偏好文件无限增长。 */
    public static List<String> recentList(List<String> current) {
        if (current == null) return new ArrayList<>();
        if (current.size() <= 50) return new ArrayList<>(current);
        return new ArrayList<>(current.subList(0, 50));
    }
}
