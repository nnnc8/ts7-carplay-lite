package io.ts7.carplay;

import android.app.ActivityManager;
import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Diagnostics {
    private Diagnostics() {}

    public static String report(Context context, SessionMachine session, RadioMonitor radio,
            SurfaceRenderer renderer, VideoProfile profile, String mode, SessionMachine.Reason playbackReason, int reconnects,
            PcmAudioOutput audio, EventRing events, PlatformReadiness readiness) {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        time.setTimeZone(TimeZone.getTimeZone("UTC"));
        String decoder = renderer == null ? "NOT_STARTED" : renderer.decoderName();
        // Decoder identifiers have a narrow grammar; never serialize arbitrary exception text.
        if (!decoder.matches("[A-Za-z0-9_.-]{1,128}")) decoder = "UNKNOWN";
        StringBuilder json = new StringBuilder(20000);
        json.append("{\"schemaVersion\":1,\"reportType\":\"carplay-alpha\",\"appVersion\":\"0.2.1-platform\"")
            .append(",\"timestamp\":\"").append(time.format(new Date())).append('"')
            .append(",\"mode\":\"").append(mode).append('"')
            .append(",\"carplayState\":\"").append(session.state().name()).append('"')
            .append(",\"bluetoothState\":\"").append(session.bluetooth().name()).append('"')
            .append(",\"wifiState\":\"").append(session.wifi().name()).append('"')
            .append(",\"authentication\":\"BLOCKED_BY_AUTHENTICATION_REQUIREMENT\"")
            .append(",\"decoderName\":\"").append(decoder).append('"')
            .append(",\"lastDisconnectReason\":\"").append(session.lastReason().name()).append('"')
            .append(",\"lastPlaybackReason\":\"").append(playbackReason.name()).append('"');
        number(json, "sessionUptimeMs", session.uptimeMs());
        number(json, "videoWidth", renderer == null ? 0 : renderer.width());
        number(json, "videoHeight", renderer == null ? 0 : renderer.height());
        number(json, "targetFps", profile.fps);
        number(json, "measuredFps", renderer == null ? 0 : renderer.measuredFps());
        number(json, "renderedFrames", renderer == null ? 0 : renderer.renderedFrames());
        number(json, "droppedVideoFrames", renderer == null ? 0 : renderer.droppedFrames());
        number(json, "droppedPackets", renderer == null ? 0 : renderer.droppedPackets());
        number(json, "videoQueueDepth", renderer == null ? 0 : renderer.queueDepth());
        number(json, "decoderLatencyMs", renderer == null ? 0 : renderer.latencyMs());
        number(json, "decoderRestartCount", renderer == null ? 0 : renderer.restartCount());
        number(json, "carplayReconnectCount", reconnects);
        number(json, "availableRamMb", memory.availMem / 1048576);
        json.append(",\"lowMemory\":").append(memory.lowMemory);
        json.append(",\"networkConnected\":").append(radio.connected);
        number(json, "wifiRssiDbm", radio.rssiDbm);
        number(json, "wifiLinkSpeedMbps", radio.linkSpeedMbps);
        number(json, "wifiFrequencyMHz", radio.frequencyMHz);
        number(json, "audioSampleRate", audio.sampleRate());
        number(json, "audioChannels", audio.channels());
        number(json, "audioUnderruns", audio.underruns());
        return json.append(",\"platformReadiness\":").append(readiness.json())
            .append(",\"events\":").append(events.json(200)).append('}').toString();
    }

    private static void number(StringBuilder json, String key, double value) {
        double bounded = Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
        json.append(",\"").append(key).append("\":").append(bounded);
    }
}
