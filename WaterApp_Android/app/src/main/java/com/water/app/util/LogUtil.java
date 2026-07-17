package com.water.app.util;

import java.util.ArrayList;
import java.util.List;

public class LogUtil {
    private static final List<String> logs = new ArrayList<>();
    private static LogListener listener;

    public interface LogListener {
        void onNewLog(String msg);
    }

    public static void setListener(LogListener l) { listener = l; }

    public static void add(String msg) {
        String line = java.time.LocalTime.now().toString() + " - " + msg;
        logs.add(line);
        if (listener != null) listener.onNewLog(line);
    }

    public static String getAll() {
        StringBuilder sb = new StringBuilder();
        for (String log : logs) sb.append(log).append("\n");
        return sb.toString();
    }

    public static void clear() {
        logs.clear();
        if (listener != null) listener.onNewLog("日志已清除");
    }
}
