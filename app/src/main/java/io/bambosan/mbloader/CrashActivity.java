package io.bambosan.mbloader;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CrashActivity extends AppCompatActivity {
    
    public static final String EXTRA_STACK_TRACE = "stack_trace";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);
        
        // Get the stack trace from the intent
        String stackTraceStr = getIntent().getStringExtra(EXTRA_STACK_TRACE);
        if (stackTraceStr == null) {
            stackTraceStr = "No stack trace available";
        }
        
        final String stackTrace = stackTraceStr;
        
        // Display the stack trace
        TextView stackTraceTextView = findViewById(R.id.crash_stack_trace);
        stackTraceTextView.setText(stackTrace);
        
        // Copy button functionality
        Button copyButton = findViewById(R.id.btn_copy);
        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Stack Trace", stackTrace);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Stack trace copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        
        // Restart button functionality
        Button restartButton = findViewById(R.id.btn_restart);
        restartButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
} 