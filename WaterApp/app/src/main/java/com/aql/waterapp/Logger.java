
package com.aql.waterapp;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Logger {
    private static final String TAG = "WaterApp";
    private static final List<String> logLines = new ArrayList<>();

    public static void log(String msg) {
        Log.d(TAG, msg);
        synchronized (logLines) {
            logLines.add(msg);
        }
    }

    public static void logError(String msg) {
        Log.e(TAG, msg);
        synchronized (logLines) {
            logLines.add("[ERROR] " + msg);
        }
    }

    public static String getLogs() {
        synchronized (logLines) {
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    public static void clearLogs() {
        synchronized (logLines) {
            logLines.clear();
        }
    }
}
