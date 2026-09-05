package com.webapp.crazyshit;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** In-memory timings for this app session. No URLs, saved history or network reporting. */
final class AppPerformance {
    private static final long STARTED = SystemClock.elapsedRealtime();
    private static final Map<String, long[]> TIMINGS = new LinkedHashMap<>();
    private static final WeakHashMap<Activity, Window.OnFrameMetricsAvailableListener> LISTENERS = new WeakHashMap<>();
    private static Handler frameHandler;
    private static long frames, slowFrames;
    private static boolean firstDraw;
    private AppPerformance() { }
    static void begin() { }

    static synchronized void record(String label, long milliseconds) {
        if (milliseconds < 0) return;
        long[] totals = TIMINGS.computeIfAbsent(label, key -> new long[3]);
        totals[0]++; totals[1] += milliseconds; totals[2] = milliseconds;
    }

    static void started(Activity activity) {
        if (!(activity instanceof NativeMainActivity || activity instanceof SearchActivity
                || activity instanceof NativeFeedBrowserActivity || activity instanceof DownloadedActivity)) return;
        if (frameHandler == null) {
            HandlerThread thread = new HandlerThread("app-frame-timings"); thread.start(); frameHandler = new Handler(thread.getLooper());
        }
        if (activity instanceof NativeMainActivity && !firstDraw) {
            firstDraw = true;
            activity.getWindow().getDecorView().postOnAnimation(() -> record("Main screen first draw", SystemClock.elapsedRealtime() - STARTED));
        }
        float rate = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
        long budget = (long) (1_000_000_000d / Math.max(30, rate));
        Window.OnFrameMetricsAvailableListener listener = (window, metrics, dropped) -> {
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return;
            synchronized (AppPerformance.class) {
                frames++;
                if (metrics.getMetric(FrameMetrics.TOTAL_DURATION) > budget) slowFrames++;
            }
        };
        LISTENERS.put(activity, listener);
        activity.getWindow().addOnFrameMetricsAvailableListener(listener, frameHandler);
    }

    static void stopped(Activity activity) {
        Window.OnFrameMetricsAvailableListener listener = LISTENERS.remove(activity);
        if (listener != null) activity.getWindow().removeOnFrameMetricsAvailableListener(listener);
    }

    static synchronized String summary() {
        StringBuilder text = new StringBuilder("This app session\n\n");
        for (Map.Entry<String, long[]> entry : TIMINGS.entrySet()) {
            long[] totals = entry.getValue();
            text.append(entry.getKey()).append(": ").append(totals[2]).append(" ms last, ")
                    .append(totals[1] / Math.max(1, totals[0])).append(" ms average\n");
        }
        text.append("\nFrames over the display's timing budget: ").append(slowFrames).append(" / ").append(frames);
        text.append("\n\nTimings reset when the app process restarts. They stay on your device.");
        return text.toString();
    }
}
