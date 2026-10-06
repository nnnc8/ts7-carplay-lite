package io.ts7diag.tool;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class MainActivity extends Activity {
    private static final long PROBE_TIMEOUT_MS = 4000L;
    private static final long ADVANCED_TEST_TIMEOUT_MS = 5000L;

    private final Map<String, TextView> statusRows = new LinkedHashMap<>();
    private DiagnosticReport report;
    private TextView statusText;
    private TextView uploadStatus;
    private ProgressBar progressBar;
    private Button copyButton;
    private Button saveButton;
    private Button uploadButton;
    private Button copyUrlButton;
    private Button advancedButton;
    private String lastGithubUrl;
    private ExecutorService probeExecutor;
    private ExecutorService uploadExecutor;
    private ExecutorService advancedExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("TS7 Diagnostic v0.2");
        getWindow().setStatusBarColor(Color.rgb(25, 45, 70));
        report = new DiagnosticReport(BuildConfig.VERSION_NAME);
        buildUi();
        startDiagnostics();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 24, 28, 36);
        content.setBackgroundColor(Color.rgb(247, 249, 251));
        scrollView.addView(content);

        TextView title = textView("TS7 Diagnostic v0.2", 24, Color.rgb(20, 35, 50));
        content.addView(title, fullWidth());
        statusText = textView("Status: Starting diagnostic...", 16, Color.DKGRAY);
        content.addView(statusText, fullWidthWithTopMargin(12));
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        content.addView(progressBar, fullWidthWithTopMargin(10));

        TextView heading = textView("Diagnostic progress", 18, Color.rgb(20, 35, 50));
        content.addView(heading, fullWidthWithTopMargin(22));
        addStatusRow(content, "device", "Android / Build");
        addStatusRow(content, "memory", "CPU / Memory");
        addStatusRow(content, "display", "Display");
        addStatusRow(content, "storage", "Storage");
        addStatusRow(content, "graphics", "Graphics / Features");
        addStatusRow(content, "network", "Wi-Fi / Network");
        addStatusRow(content, "bluetooth", "Bluetooth");
        addStatusRow(content, "usb", "USB");
        addStatusRow(content, "mediaCodec", "MediaCodec enumeration");

        copyButton = button("Copy public report", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyPublicReport();
            }
        });
        copyButton.setEnabled(false);
        content.addView(copyButton, fullWidthWithTopMargin(22));

        saveButton = button("Save local report", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveLocalReport();
            }
        });
        saveButton.setEnabled(false);
        content.addView(saveButton, fullWidthWithTopMargin(8));

        uploadButton = button("Upload report to GitHub", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmUpload();
            }
        });
        uploadButton.setEnabled(false);
        content.addView(uploadButton, fullWidthWithTopMargin(8));

        uploadStatus = textView("Upload status: Not uploaded", 14, Color.DKGRAY);
        content.addView(uploadStatus, fullWidthWithTopMargin(8));

        copyUrlButton = button("Copy GitHub URL", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyGithubUrl();
            }
        });
        copyUrlButton.setEnabled(false);
        content.addView(copyUrlButton, fullWidthWithTopMargin(8));

        TextView warning = textView(
                "Advanced H.264 test initializes the device decoder. Run only after the basic report has been saved or uploaded.",
                14,
                Color.rgb(110, 70, 20));
        content.addView(warning, fullWidthWithTopMargin(22));

        advancedButton = button("Run advanced H.264 test", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runAdvancedTest();
            }
        });
        content.addView(advancedButton, fullWidthWithTopMargin(8));

        setContentView(scrollView);
    }

    private void addStatusRow(LinearLayout content, String key, String label) {
        TextView row = textView(label + "    Waiting", 15, Color.DKGRAY);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusRows.put(key, row);
        content.addView(row, fullWidthWithTopMargin(8));
    }

    private void startDiagnostics() {
        final List<DiagnosticProbe> probes = new ArrayList<>();
        probes.add(new DeviceProbe());
        probes.add(new MemoryProbe());
        probes.add(new DisplayProbe());
        probes.add(new StorageProbe());
        probes.add(new GraphicsProbe());
        probes.add(new NetworkProbe());
        probes.add(new BluetoothProbe());
        probes.add(new UsbProbe());
        probes.add(new MediaCodecProbe());

        probeExecutor = Executors.newFixedThreadPool(probes.size());
        final List<Future<ProbeResult>> futures = new ArrayList<>();
        for (final DiagnosticProbe probe : probes) {
            setRow(probe.getKey(), "Running", Color.rgb(50, 80, 120));
            futures.add(probeExecutor.submit(new java.util.concurrent.Callable<ProbeResult>() {
                @Override
                public ProbeResult call() {
                    try {
                        return probe.run(getApplicationContext());
                    } catch (Throwable error) {
                        return ProbeResult.failed(
                                probe.getKey(),
                                ProbeResult.Status.FAILED,
                                0L,
                                new LinkedHashMap<String, Object>(),
                                error.getClass().getSimpleName());
                    }
                }
            }));
        }

        Thread coordinator = new Thread(new Runnable() {
            @Override
            public void run() {
                int completed = 0;
                for (int index = 0; index < futures.size(); index++) {
                    Future<ProbeResult> future = futures.get(index);
                    DiagnosticProbe probe = probes.get(index);
                    ProbeResult result;
                    try {
                        result = future.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException timeout) {
                        future.cancel(true);
                        result = ProbeResult.failed(
                                probe.getKey(),
                                ProbeResult.Status.TIMEOUT,
                                PROBE_TIMEOUT_MS,
                                new LinkedHashMap<String, Object>(),
                                "ProbeTimeout");
                    } catch (Throwable error) {
                        result = ProbeResult.failed(
                                probe.getKey(),
                                ProbeResult.Status.FAILED,
                                0L,
                                new LinkedHashMap<String, Object>(),
                                error.getClass().getSimpleName());
                    }
                    report.addResult(result);
                    completed++;
                    final ProbeResult completedResult = result;
                    final int completedCount = completed;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateRow(completedResult);
                            statusText.setText("Status: " + completedCount + "/" + futures.size() + " probes complete");
                        }
                    });
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setIndeterminate(false);
                        progressBar.setProgress(100);
                        statusText.setText("Status: Basic diagnostic complete");
                        copyButton.setEnabled(true);
                        saveButton.setEnabled(true);
                        uploadButton.setEnabled(true);
                        if (BuildConfig.DIAGNOSTIC_UPLOAD_URL.length() == 0) {
                            uploadStatus.setText("Upload status: Relay URL not configured; copy/save remain available");
                        }
                    }
                });
                probeExecutor.shutdown();
            }
        }, "ts7-diagnostic-coordinator");
        coordinator.start();
    }

    private void updateRow(ProbeResult result) {
        String status = result.getStatus().name();
        String text = rowLabel(result.getKey()) + "    " + status + " (" + result.getDurationMs() + " ms)";
        int color = result.getStatus() == ProbeResult.Status.PASS
                ? Color.rgb(20, 115, 60)
                : Color.rgb(150, 65, 35);
        setRow(result.getKey(), text.substring(rowLabel(result.getKey()).length() + 4), color);
    }

    private String rowLabel(String key) {
        if ("device".equals(key)) return "Android / Build";
        if ("memory".equals(key)) return "CPU / Memory";
        if ("display".equals(key)) return "Display";
        if ("storage".equals(key)) return "Storage";
        if ("graphics".equals(key)) return "Graphics / Features";
        if ("network".equals(key)) return "Wi-Fi / Network";
        if ("bluetooth".equals(key)) return "Bluetooth";
        if ("usb".equals(key)) return "USB";
        return "MediaCodec enumeration";
    }

    private void setRow(String key, String status, int color) {
        TextView row = statusRows.get(key);
        if (row != null) {
            row.setText(rowLabel(key) + "    " + status);
            row.setTextColor(color);
        }
    }

    private void copyPublicReport() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            statusText.setText("Status: Clipboard unavailable; use Save local report");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("TS7 Diagnostic v0.2 public report", report.publicText()));
        statusText.setText("Status: Public report copied to clipboard");
    }

    private void saveLocalReport() {
        File root = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) {
            statusText.setText("Status: Local storage unavailable");
            return;
        }
        if (!root.exists() && !root.mkdirs()) {
            statusText.setText("Status: Could not create local report directory");
            return;
        }
        File file = new File(root, "TS7-Diagnostic-v0.2-local.json");
        try {
            FileOutputStream output = new FileOutputStream(file);
            output.write(report.localText().getBytes(Charset.forName("UTF-8")));
            output.close();
            statusText.setText("Status: Local report saved to " + file.getAbsolutePath());
        } catch (Throwable error) {
            statusText.setText("Status: Local report save failed safely");
        }
    }

    private void confirmUpload() {
        if (BuildConfig.DIAGNOSTIC_UPLOAD_URL.length() == 0) {
            uploadStatus.setText("Upload status: Relay URL not configured");
            return;
        }
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo network = connectivity == null ? null : connectivity.getActiveNetworkInfo();
        if (network == null || !network.isConnected()) {
            uploadStatus.setText("Upload status: No network connection. Report remains available locally.");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Upload sanitized report?")
                .setMessage("Only sanitized hardware diagnostic information will be uploaded publicly to GitHub.\n\nThe report does NOT include IMEI, Wi-Fi SSID/BSSID, MAC addresses, account information, location, Android ID or serial number.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Upload", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        uploadPublicReport();
                    }
                })
                .show();
    }

    private void uploadPublicReport() {
        uploadButton.setEnabled(false);
        uploadStatus.setText("Upload status: Uploading sanitized report...");
        if (uploadExecutor != null) {
            uploadExecutor.shutdownNow();
        }
        uploadExecutor = Executors.newSingleThreadExecutor();
        uploadExecutor.submit(new Runnable() {
            @Override
            public void run() {
                final UploadResult result = NetworkUploader.upload(
                        BuildConfig.DIAGNOSTIC_UPLOAD_URL,
                        report.publicJson());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        uploadButton.setEnabled(true);
                        if (result.isSuccess()) {
                            lastGithubUrl = result.getGithubUrl();
                            uploadStatus.setText("Upload successful\nGitHub report: " + lastGithubUrl);
                            copyUrlButton.setEnabled(true);
                        } else {
                            uploadStatus.setText("Upload status: " + result.getMessage());
                        }
                    }
                });
            }
        });
    }

    private void copyGithubUrl() {
        if (lastGithubUrl == null || lastGithubUrl.length() == 0) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("TS7 GitHub report", lastGithubUrl));
            uploadStatus.setText("Upload successful; GitHub URL copied");
        }
    }

    private void runAdvancedTest() {
        advancedButton.setEnabled(false);
        statusText.setText("Status: Running advanced H.264 initialization test...");
        if (advancedExecutor != null) {
            advancedExecutor.shutdownNow();
        }
        advancedExecutor = Executors.newSingleThreadExecutor();
        final Future<ProbeResult> future = advancedExecutor.submit(new java.util.concurrent.Callable<ProbeResult>() {
            @Override
            public ProbeResult call() {
                return new MediaCodecProbe().runAdvancedAvcTest();
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ProbeResult result;
                try {
                    result = future.get(ADVANCED_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException timeout) {
                    future.cancel(true);
                    result = ProbeResult.failed("mediaCodecAdvanced", ProbeResult.Status.TIMEOUT, ADVANCED_TEST_TIMEOUT_MS, new LinkedHashMap<String, Object>(), "AdvancedTestTimeout");
                } catch (Throwable error) {
                    result = ProbeResult.failed("mediaCodecAdvanced", ProbeResult.Status.FAILED, 0L, new LinkedHashMap<String, Object>(), error.getClass().getSimpleName());
                }
                report.addResult(result);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        advancedButton.setEnabled(true);
                        statusText.setText("Status: Advanced H.264 test " + result.getStatus().name());
                    }
                });
            }
        }, "ts7-advanced-codec-watchdog").start();
    }

    private TextView textView(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthWithTopMargin(int margin) {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = margin;
        return params;
    }

    @Override
    protected void onDestroy() {
        if (probeExecutor != null) {
            probeExecutor.shutdownNow();
        }
        if (uploadExecutor != null) {
            uploadExecutor.shutdownNow();
        }
        if (advancedExecutor != null) {
            advancedExecutor.shutdownNow();
        }
        super.onDestroy();
    }
}
