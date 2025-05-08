package io.bambosan.mbloader;

import android.app.Application;
import android.content.Intent;
import android.os.Process;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MBLoaderApp extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Set up the default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String stackTraceString = sw.toString();
            
            // Start the crash activity with the stack trace
            Intent intent = new Intent(MBLoaderApp.this, CrashActivity.class);
            intent.putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTraceString);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            
            // Force close the app
            Process.killProcess(Process.myPid());
            System.exit(1);
        });
    }
} 