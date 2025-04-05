package io.bambosan.mbloader;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.LinkedList;
import java.util.Queue;

public class FPSOverlayService extends Service implements Choreographer.FrameCallback {
    private WindowManager windowManager;
    private View overlayView;
    private TextView fpsText;
    private WindowManager.LayoutParams params;
    private Handler handler;
    
    // FPS calculation variables
    private Queue<Long> frameIntervals;
    private static final int MAX_SAMPLE_SIZE = 60; // Store last 60 frames for average
    private long lastFrameTimeNanos;
    private float currentFps;
    
    // FPS thresholds and colors
    private static final float HIGH_FPS_THRESHOLD = 60.0f;
    private static final float LOW_FPS_THRESHOLD = 30.0f;
    private static final int COLOR_GOOD = Color.rgb(0, 255, 0);      // Green
    private static final int COLOR_MODERATE = Color.rgb(255, 255, 0); // Yellow
    private static final int COLOR_POOR = Color.rgb(255, 0, 0);      // Red
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Check if we have the overlay permission before attempting to create the overlay
        if (!android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        frameIntervals = new LinkedList<>();
        
        try {
            createOverlay();
            startFPSTracking();
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    private void createOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.fps_overlay, null);
        fpsText = overlayView.findViewById(R.id.fps_text);

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        );

        // Initial position
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        // Load saved position and size
        SharedPreferences prefs = getSharedPreferences("fps_settings", MODE_PRIVATE);
        params.x = prefs.getInt("overlay_x", 0);
        params.y = prefs.getInt("overlay_y", 100);
        
        // Make overlay draggable
        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;

                    case MotionEvent.ACTION_UP:
                        // Save position
                        SharedPreferences.Editor editor = getSharedPreferences("fps_settings", MODE_PRIVATE).edit();
                        editor.putInt("overlay_x", params.x);
                        editor.putInt("overlay_y", params.y);
                        editor.apply();
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, params);
    }

    private void startFPSTracking() {
        lastFrameTimeNanos = System.nanoTime();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private int getFpsColor(float fps) {
        if (fps >= HIGH_FPS_THRESHOLD) {
            return COLOR_GOOD;
        } else if (fps >= LOW_FPS_THRESHOLD) {
            return COLOR_MODERATE;
        } else {
            return COLOR_POOR;
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        // Calculate frame time
        long frameInterval = frameTimeNanos - lastFrameTimeNanos;
        lastFrameTimeNanos = frameTimeNanos;
        
        // Add to queue and maintain max size
        frameIntervals.offer(frameInterval);
        if (frameIntervals.size() > MAX_SAMPLE_SIZE) {
            frameIntervals.poll();
        }
        
        // Calculate average FPS over the sample window
        if (!frameIntervals.isEmpty()) {
            double averageFrameTime = 0;
            for (Long interval : frameIntervals) {
                averageFrameTime += interval;
            }
            averageFrameTime /= frameIntervals.size();
            
            // Convert to FPS (nanoseconds to seconds)
            currentFps = (float) (1_000_000_000.0 / averageFrameTime);
            
            // Update UI on main thread
            handler.post(() -> {
                if (fpsText != null) {
                    // Update both text and color
                    fpsText.setText(String.format("%.1f FPS", currentFps));
                    fpsText.setTextColor(getFpsColor(currentFps));
                    
                    // Add performance indicator emoji
                    String performanceEmoji = currentFps >= HIGH_FPS_THRESHOLD ? "🟢" :
                                            currentFps >= LOW_FPS_THRESHOLD ? "🟡" : "🔴";
                    fpsText.setText(String.format("%s %.1f FPS", performanceEmoji, currentFps));
                }
            });
        }
        
        // Register for next frame
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            windowManager.removeView(overlayView);
        }
        Choreographer.getInstance().removeFrameCallback(this);
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
} 