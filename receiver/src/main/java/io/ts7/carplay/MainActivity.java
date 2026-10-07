package io.ts7.carplay;

import android.app.Activity;
import android.app.AlertDialog;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;
import io.ts7.carplay.core.DiPlayReceiverCore;
import io.ts7.carplay.auth.UnavailableAuthenticationProvider;

public final class MainActivity extends Activity implements SurfaceHolder.Callback {
    private final Handler main = new Handler();
    private final EventRing events = new EventRing();
    private final SessionMachine session = new SessionMachine(events);
    private DiPlayReceiverCore core;
    private final RetryBudget reconnect = new RetryBudget();
    private final PcmAudioOutput audio = new PcmAudioOutput(events);
    private final float[] touch = new float[2];
    private RadioMonitor radio;
    private SurfaceView surface;
    private TextView heading;
    private TextView status;
    private LinearLayout bar;
    private volatile SurfaceRenderer renderer;
    private volatile PatternSource pattern;
    private volatile String mode = "IDLE";
    private VideoProfile profile = VideoProfile.DEFAULT;
    private volatile int generation;
    private volatile int connectionGeneration;
    private int recoveryGeneration;
    private volatile boolean coreActive;
    private int reconnectCount;
    private boolean resumed;
    private boolean uploading;
    private boolean fullscreen;
    private TextView diagnosticText;
    private SessionMachine.Reason playbackReason = SessionMachine.Reason.NONE;
    private boolean touchPressed;
    private int touchPointer;

    private final Runnable monitor = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            radio.refresh();
            SurfaceRenderer current = renderer;
            if (current != null && !"IDLE".equals(mode)) current.watchdog();
            updateStatus();
            if (diagnosticText != null) diagnosticText.setText(report());
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        heading = text("TS7 CarPlay Lite · DiPlay v0.2 Preview", 20);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading, new LinearLayout.LayoutParams(-1, 44));
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        surface.getHolder().setFixedSize(1280, 720);
        surface.setOnTouchListener(this::surfaceTouch);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        stage.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            int fittedWidth = Math.min(width, height * 1280 / 720);
            int fittedHeight = fittedWidth * 720 / 1280;
            FrameLayout.LayoutParams layout = (FrameLayout.LayoutParams) surface.getLayoutParams();
            if (layout.width != fittedWidth || layout.height != fittedHeight) {
                layout.width = fittedWidth;
                layout.height = fittedHeight;
                surface.setLayoutParams(layout);
            }
        });
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        status = text("Waiting for iPhone · authentication blocked", 14);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        button(bar, "Settings", this::settings);
        button(bar, "Diagnostics", this::diagnostics);
        root.addView(bar, new LinearLayout.LayoutParams(-1, 54));
        setContentView(root); // No probes, assets or MediaCodec work precede visible UI.
        events.add(EventCode.APP_OPEN);
        radio = new RadioMonitor(this, session);
        core = new DiPlayReceiverCore(this, new UnavailableAuthenticationProvider());
        new Thread(() -> {
            core.initialize();
            main.post(() -> { if (!isDestroyed()) updateStatus(); });
        }, "ts7-diplay-initialize").start();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        radio.start();
        main.removeCallbacks(monitor);
        main.post(monitor);
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(monitor);
        radio.stop();
        // Start stopping before Android destroys the Surface during a background transition.
        if (coreActive || !"IDLE".equals(mode)) stopPlayback(SessionMachine.Reason.USER_STOP);
        super.onPause();
    }

    @Override protected void onStop() {
        if (!"IDLE".equals(mode)) stopPlayback(SessionMachine.Reason.USER_STOP);
        super.onStop();
    }

    @Override protected void onDestroy() {
        stopPlayback(SessionMachine.Reason.USER_STOP);
        core.close();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { updateStatus(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        SurfaceRenderer current = renderer;
        if (current != null && !current.stopped()) {
            events.add(EventCode.SURFACE_LOST);
            stopPlayback(SessionMachine.Reason.SURFACE_LOST);
            // Acknowledge ordinary worker shutdown, but never freeze UI on a hung vendor call.
            if (!current.awaitStopped(250)) events.add(EventCode.CODEC_CALL_TIMEOUT);
        }
    }

    @Override public void onBackPressed() {
        if (fullscreen) showChrome();
        else super.onBackPressed();
    }

    private void settings() {
        boolean developer = getPreferences(0).getBoolean("developerPattern", false);
        String[] options = {"Connect iPhone (authentication unavailable)", "System Bluetooth pairing",
            "System Wi-Fi settings", "Video profile: " + profile.name(),
            "Developer test mode: " + (developer ? "ON" : "OFF"),
            "Start developer H.264 pattern", "Stop playback",
            "Wireless discovery permission", "Select paired iPhone"};
        new AlertDialog.Builder(this).setTitle("Settings · Technical preview")
            .setItems(options, (dialog, index) -> {
                if (index == 0) connect();
                if (index == 1) startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                if (index == 2) startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                if (index == 3) profiles();
                if (index == 4) {
                    getPreferences(0).edit().putBoolean("developerPattern", !developer).apply();
                    if (developer && "TEST_PATTERN".equals(mode)) stopPlayback(SessionMachine.Reason.USER_STOP);
                }
                if (index == 5) startPattern();
                if (index == 6) stopPlayback(SessionMachine.Reason.USER_STOP);
                if (index == 7) discoveryPermission();
                if (index == 8) selectIphone();
            }).setNegativeButton("Close", null).show();
    }

    private void discoveryPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Wireless discovery permission granted. No location is collected.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Wi-Fi / Bluetooth discovery")
            .setMessage("Android 8 requires this permission for Wi-Fi/Bluetooth discovery. TS7 CarPlay Lite does not collect or upload location.")
            .setPositiveButton("Continue", (dialog, which) ->
                requestPermissions(new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 27))
            .setNegativeButton("Cancel", null).show();
    }

    @SuppressWarnings("deprecation")
    private void selectIphone() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth and pair an iPhone in Android settings.", Toast.LENGTH_LONG).show();
            return;
        }
        final BluetoothDevice[] devices = adapter.getBondedDevices().toArray(new BluetoothDevice[0]);
        if (devices.length == 0) {
            Toast.makeText(this, "No paired device. Pair your iPhone in Android settings.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[devices.length];
        for (int index = 0; index < devices.length; index++) {
            String name = devices[index].getName();
            names[index] = name == null ? "Paired device " + (index + 1) : name.substring(0, Math.min(64, name.length()));
        }
        new AlertDialog.Builder(this).setTitle("Select paired iPhone · local only")
            .setItems(names, (dialog, index) -> {
                stopPlayback(SessionMachine.Reason.USER_STOP);
                core.selectPairedAddress(devices[index].getAddress());
                Toast.makeText(this, "Selected locally; authentication remains blocked.", Toast.LENGTH_LONG).show();
            }).setNegativeButton("Cancel", null).show();
    }

    private void profiles() {
        new AlertDialog.Builder(this).setTitle("1280×720 video profile")
            .setItems(new String[] {"Default: 30 fps", "Balanced: 25 fps", "Stability: 20 fps"}, (dialog, index) -> {
                stopPlayback(SessionMachine.Reason.USER_STOP);
                profile = VideoProfile.values()[index];
                updateStatus();
            }).show();
    }

    private void connect() {
        stopPlayback(SessionMachine.Reason.USER_STOP);
        session.begin(core.hasLawfulAuthentication());
        if (core.hasLawfulAuthentication()) {
            reconnect.reset();
            coreActive = true;
            core.connect(profile, coreListener(++connectionGeneration));
        }
        else new AlertDialog.Builder(this).setTitle("Authentication blocked")
            .setMessage("A lawful CarPlay authentication component is required. This technical preview cannot connect an iPhone yet. The developer H.264 pattern can test the decoder and display.")
            .setPositiveButton("OK", null).show();
        updateStatus();
    }

    // Package-visible for the separately packaged CI instrumentation build.
    void enableDeveloperTest() { getPreferences(0).edit().putBoolean("developerPattern", true).commit(); }
    SurfaceRenderer rendererForTest() { return renderer; }
    String modeForTest() { return mode; }
    SessionMachine sessionForTest() { return session; }
    String coreStatusForTest() { return core.initializationStatus(); }
    boolean coreAuthForTest() { return core.hasLawfulAuthentication(); }

    void startPattern() {
        if (!getPreferences(0).getBoolean("developerPattern", false)) {
            Toast.makeText(this, "Enable Developer test mode in Settings first", Toast.LENGTH_LONG).show();
            return;
        }
        stopPlayback(SessionMachine.Reason.USER_STOP); // Also cancels any negotiating core.
        if (!prepareRenderer("TEST_PATTERN")) return;
        try {
            pattern = new PatternSource(getAssets(), renderer, profile);
            renderer.start();
            pattern.start();
            events.add(EventCode.TEST_START);
        } catch (Exception error) {
            stopPlayback(SessionMachine.Reason.INPUT_REJECTED);
            Toast.makeText(this, "Test input unavailable", Toast.LENGTH_LONG).show();
        }
        updateStatus();
    }

    private boolean prepareRenderer(String requestedMode) {
        stopVideo(SessionMachine.Reason.USER_STOP);
        if (renderer != null && !renderer.stopped()) {
            Toast.makeText(this, "Previous decoder is still stopping; wait before restarting", Toast.LENGTH_LONG).show();
            return false;
        }
        if (!surface.getHolder().getSurface().isValid()) return false;
        mode = requestedMode;
        playbackReason = SessionMachine.Reason.NONE;
        int currentGeneration = ++generation;
        renderer = new SurfaceRenderer(surface.getHolder().getSurface(), profile, events, main,
            new SurfaceRenderer.Listener() {
                public void firstFrame() {
                    main.post(() -> {
                        if (generation != currentGeneration) return;
                        if ("CARPLAY".equals(mode) && session.authenticated() && core.frameRendered()) {
                            if (session.state() == SessionMachine.State.CARPLAY_NEGOTIATING
                                    || session.state() == SessionMachine.State.RECOVERING) {
                                session.firstCarPlayFrame();
                                recoveryGeneration++; // Invalidates timers from this recovery cycle.
                                reconnect.reset();
                            }
                            hideChrome();
                        }
                        updateStatus();
                    });
                }
                public void keyframeNeeded() {
                    if (generation != currentGeneration) return;
                    PatternSource current = pattern;
                    if (current != null && "TEST_PATTERN".equals(mode)) current.requestKeyframe();
                    else if ("CARPLAY".equals(mode)) core.requestKeyframe();
                }
                public void failed(SessionMachine.Reason reason) {
                    main.post(() -> {
                        if (generation != currentGeneration) return;
                        stopPlayback(reason);
                        Toast.makeText(MainActivity.this, "Playback stopped: " + reason.name(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        return true;
    }

    private void stopPlayback(SessionMachine.Reason reason) {
        releaseTouch();
        connectionGeneration++;
        recoveryGeneration++;
        coreActive = false;
        core.disconnect();
        reconnect.reset();
        if (session.state() != SessionMachine.State.IDLE
                && session.state() != SessionMachine.State.ERROR) session.stop(reason);
        stopVideo(reason);
    }

    private void stopVideo(SessionMachine.Reason reason) {
        releaseTouch();
        generation++;
        if (pattern != null) { pattern.stop(); pattern = null; events.add(EventCode.TEST_STOP); }
        if (renderer != null) renderer.stop(); // Never wait for a vendor call on the UI thread.
        audio.stop();
        mode = "IDLE";
        playbackReason = reason;
        showChrome();
        updateStatus();
    }

    private boolean surfaceTouch(View view, MotionEvent event) {
        if (!"CARPLAY".equals(mode) || session.state() != SessionMachine.State.STREAMING) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) { releaseTouch(); return true; }
        if (action == MotionEvent.ACTION_DOWN) {
            int index = event.getActionIndex();
            touchPointer = event.getPointerId(index);
            touchPressed = TouchMapper.map(event.getX(index), event.getY(index), view.getWidth(), view.getHeight(),
                profile.width, profile.height, touch) && core.touch(MotionEvent.ACTION_DOWN, touch[0], touch[1]);
            return true;
        }
        if (!touchPressed) return true;
        int index = event.findPointerIndex(touchPointer);
        if (index < 0) { releaseTouch(); return true; }
        boolean terminates = action == MotionEvent.ACTION_UP
            || (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == touchPointer);
        if (action == MotionEvent.ACTION_MOVE || terminates) {
            // Keep last valid coordinates if dragged outside; UP/CANCEL always terminates.
            boolean mapped = TouchMapper.map(event.getX(index), event.getY(index), view.getWidth(), view.getHeight(),
                profile.width, profile.height, touch);
            if (terminates) releaseTouch();
            else if (mapped) core.touch(MotionEvent.ACTION_MOVE, touch[0], touch[1]);
        }
        return true;
    }

    private void releaseTouch() {
        if (touchPressed) core.touch(MotionEvent.ACTION_UP, touch[0], touch[1]);
        touchPressed = false;
    }

    private boolean connectionCurrent(int expected) {
        return expected == connectionGeneration && coreActive && resumed && !isFinishing() && !isDestroyed();
    }

    private ReceiverCore.Listener coreListener(final int expected) {
        return new ReceiverCore.Listener() {
            public void bluetoothBootstrapConfirmed() { bluetoothBootstrapConfirmed(() -> true); }
            public void bluetoothBootstrapConfirmed(BooleanSupplier attemptCurrent) {
                main.post(() -> {
                    if (attemptCurrent.getAsBoolean() && connectionCurrent(expected) && session.state() == SessionMachine.State.BT_DISCOVERY)
                        session.bootstrapConfirmed();
                });
            }
            public void wifiSessionLinkConfirmed() { wifiSessionLinkConfirmed(() -> true); }
            public void wifiSessionLinkConfirmed(BooleanSupplier attemptCurrent) {
                main.post(() -> {
                    if (attemptCurrent.getAsBoolean() && connectionCurrent(expected) && session.state() == SessionMachine.State.WIFI_CONNECTING)
                        session.sessionWifiConfirmed();
                });
            }
            public void authenticatedSessionStarted() { authenticatedSessionStarted(() -> true); }
            public void authenticatedSessionStarted(BooleanSupplier attemptCurrent) {
                main.post(() -> {
                    if (!attemptCurrent.getAsBoolean() || !connectionCurrent(expected)) return;
                    SessionMachine.State state = session.state();
                    if (state != SessionMachine.State.CARPLAY_NEGOTIATING && state != SessionMachine.State.RECOVERING) return;
                    if (session.authenticated()) return; // Duplicate provider callback, not a new session.
                    if (prepareRenderer("CARPLAY")) {
                        recoveryGeneration++; // Do not retry over an authenticated decoder startup.
                        session.authenticationConfirmed();
                        renderer.start();
                        core.videoSinkReady(); // Source must preserve initial SPS/PPS/IDR until this handshake.
                    } else handleDisconnect(expected, SessionMachine.Reason.SURFACE_LOST);
                });
            }
            public boolean videoAccessUnit(byte[] bytes, int offset, int length, long timestampUs) {
                SurfaceRenderer current = renderer;
                return expected == connectionGeneration && coreActive && "CARPLAY".equals(mode)
                    && session.authenticated() && current != null && current.offer(bytes, offset, length, timestampUs);
            }
            public void streamReset() {
                SurfaceRenderer current = renderer;
                if (expected == connectionGeneration && coreActive && "CARPLAY".equals(mode) && current != null)
                    current.streamReset();
            }
            public void disconnected(SessionMachine.Reason reason) { disconnected(reason, () -> true); }
            public void disconnected(SessionMachine.Reason reason, BooleanSupplier attemptCurrent) {
                main.post(() -> { if (attemptCurrent.getAsBoolean()) handleDisconnect(expected, reason); });
            }
            public boolean audioFormat(int sampleRate, int channels) {
                if (!connectionCurrent(expected) || !session.authenticated() || !"CARPLAY".equals(mode)) return false;
                try { audio.start(sampleRate, channels); return true; }
                catch (Exception error) { events.add(EventCode.AUDIO_ERROR); return false; }
            }
            public int audioPcm(ByteBuffer pcm, int bytes) {
                if (!connectionCurrent(expected) || !session.authenticated() || !"CARPLAY".equals(mode)) return 0;
                int written = audio.write(pcm, bytes);
                if (written != bytes) events.add(EventCode.AUDIO_ERROR, bytes - written);
                return written;
            }
            public void audioStopped() {
                if (expected == connectionGeneration) audio.stop();
            }
        };
    }

    private void handleDisconnect(int expected, SessionMachine.Reason reason) {
        if (!connectionCurrent(expected)
                || (session.state() == SessionMachine.State.RECOVERING && !session.authenticated())) return;
        session.recovering(reason);
        stopVideo(reason); // Fresh authentication and a fresh rendered frame are needed.
        events.add(EventCode.RECOVERY_START);
        scheduleReconnect(expected, ++recoveryGeneration);
    }

    private boolean recoveryCurrent(int expectedConnection, int expectedRecovery) {
        return connectionCurrent(expectedConnection) && recoveryGeneration == expectedRecovery
            && session.state() == SessionMachine.State.RECOVERING && !session.authenticated();
    }

    private void scheduleReconnect(int expectedConnection, int expectedRecovery) {
        long delay = reconnect.peekDelayMs();
        if (delay < 0) {
            // The final attempt gets a completion window; never cancel it on the same UI turn.
            main.postDelayed(() -> {
                if (!recoveryCurrent(expectedConnection, expectedRecovery)) return;
                session.exhausted();
                stopPlayback(SessionMachine.Reason.RECOVERY_EXHAUSTED);
            }, 5000);
            return;
        }
        main.postDelayed(() -> {
            if (!recoveryCurrent(expectedConnection, expectedRecovery)) return;
            reconnect.nextDelayMs(); // Consume only an attempt actually dispatched.
            reconnectCount++;
            core.reconnect(); // Acceptance is not evidence of recovery: core must resume authenticated media.
            // Let synchronous provider callbacks posted to the UI complete first.
            main.post(() -> {
                if (recoveryCurrent(expectedConnection, expectedRecovery))
                    scheduleReconnect(expectedConnection, expectedRecovery);
            });
        }, delay);
    }

    private void diagnostics() {
        diagnosticText = text(report(), 13);
        diagnosticText.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(diagnosticText);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Diagnostics · DiPlay · " + core.initializationStatus())
            .setView(scroll).setPositiveButton("Copy diagnostics", (ignored, which) -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TS7 alpha diagnostics", report()));
            }).setNeutralButton("Upload diagnostics", (ignored, which) -> confirmUpload())
            .setNegativeButton("Close", null).create();
        dialog.setOnDismissListener(ignored -> diagnosticText = null);
        dialog.show();
    }

    private void confirmUpload() {
        if (uploading) return;
        final String publicReport = report();
        new AlertDialog.Builder(this).setTitle("Upload public diagnostics to Issue #13?")
            .setMessage("This sends only the displayed counters, radio metrics and fixed event codes to the public TS7 alpha testing issue. Test-pattern playback is clearly labeled. No automatic uploads.")
            .setPositiveButton("Upload", (dialog, which) -> {
                uploading = true;
                new Thread(() -> {
                    String result;
                    try { result = "Uploaded: " + DiagnosticUploader.upload(publicReport); }
                    catch (Exception error) { result = "Upload failed. You can still copy diagnostics offline."; }
                    final String message = result;
                    main.post(() -> {
                        uploading = false;
                        if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this)
                            .setTitle("Diagnostics upload").setMessage(message).setPositiveButton("OK", null).show();
                    });
                }, "ts7-explicit-upload").start();
            }).setNegativeButton("Cancel", null).show();
    }

    private String report() {
        return Diagnostics.report(this, session, radio, renderer, profile, mode, playbackReason, reconnectCount, audio, events);
    }

    private void updateStatus() {
        if (status == null) return;
        SurfaceRenderer current = renderer;
        String line = "Waiting for iPhone · authentication blocked";
        if ("DIPLAY_CORE_READY_AUTH_BLOCKED".equals(core.initializationStatus())) line += " · DiPlay ready";
        else if ("DIPLAY_CORE_INITIALIZATION_FAILED".equals(core.initializationStatus())) line += " · port initialization failed";
        if ("TEST_PATTERN".equals(mode) && current != null) {
            line = "TEST PATTERN (not CarPlay) · " + profile.fps + " fps target · "
                + String.format(java.util.Locale.US, "%.1f fps · q=%d", current.measuredFps(), current.queueDepth());
        } else if (coreActive) line = session.state().name();
        else if (playbackReason != SessionMachine.Reason.NONE && playbackReason != SessionMachine.Reason.USER_STOP) line += " · " + playbackReason.name();
        status.setText(line);
    }

    private void hideChrome() {
        fullscreen = true;
        heading.setVisibility(View.GONE);
        bar.setVisibility(View.GONE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showChrome() {
        if (heading == null) return;
        fullscreen = false;
        heading.setVisibility(View.VISIBLE);
        bar.setVisibility(View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(0);
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setPadding(8, 4, 8, 4);
        return text;
    }

    private void button(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(view -> action.run());
        parent.addView(button, new LinearLayout.LayoutParams(-2, -2));
    }
}
