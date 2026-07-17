package com.example.waterapp.utils;

import android.util.Log;
import java.io.StringWriter;
import java.io.PrintWriter;

public class L {
    private static final String TAG = "WaterApp";
    private static StringBuilder logBuffer = new StringBuilder();

    public static void log(String msg) {
        Log.d(TAG, msg);
        logBuffer.append(msg).append("\n");
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        logBuffer.append("ERROR: ").append(msg).append("\n");
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        logBuffer.append(sw.toString()).append("\n");
    }

    public static String getLog() {
        return logBuffer.toString();
    }

    public static void clearLog() {
        logBuffer.setLength(0);
    }
}
