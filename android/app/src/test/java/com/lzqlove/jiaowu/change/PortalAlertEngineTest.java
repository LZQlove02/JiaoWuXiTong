package com.lzqlove.jiaowu.change;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 新通知/新消息只对基线之外的 ID 触发。 */
public class PortalAlertEngineTest {

    @Test
    public void detectsOnlyNewIds() {
        Set<String> previous = new HashSet<>(Arrays.asList("A", "B"));
        List<String> current = Arrays.asList("B", "C", "D");
        List<String> fresh = PortalAlertEngine.newIds(previous, current);
        assertEquals(2, fresh.size());
        assertEquals("C", fresh.get(0));
        assertEquals("D", fresh.get(1));
    }

    @Test
    public void ignoresExistingIds() {
        Set<String> previous = new HashSet<>(Arrays.asList("A", "B"));
        List<String> current = Arrays.asList("A", "B");
        assertTrue(PortalAlertEngine.newIds(previous, current).isEmpty());
    }

    @Test
    public void recentListKeepsFirstFifty() {
        String[] ids = new String[60];
        for (int i = 0; i < ids.length; i += 1) ids[i] = "id" + i;
        List<String> recent = PortalAlertEngine.recentList(Arrays.asList(ids));
        assertEquals(50, recent.size());
        assertEquals("id0", recent.get(0));
        assertEquals("id49", recent.get(49));
    }
}
