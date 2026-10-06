package io.ts7diag.tool;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MediaCodecProbe implements DiagnosticProbe {
    @Override
    public String getKey() {
        return "mediaCodec";
    }

    @Override
    public ProbeResult run(Context context) {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> codecs = enumerateAvcDecoders();
            data.put("videoAvcDecoderCount", codecs.size());
            data.put("decoders", codecs);
            data.put("startupMode", "safe enumeration only; decoder was not initialized");
            return ProbeResult.pass(getKey(), elapsed(started), data);
        } catch (Throwable error) {
            return ProbeResult.failed(getKey(), ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        }
    }

    public ProbeResult runAdvancedAvcTest() {
        long started = System.currentTimeMillis();
        Map<String, Object> data = new LinkedHashMap<>();
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createDecoderByType("video/avc");
            data.put("decoderInstantiation", "PASS");
            data.put("warning", "initialization only; no input stream was decoded");
            return ProbeResult.pass("mediaCodecAdvanced", elapsed(started), data);
        } catch (Throwable error) {
            data.put("decoderInstantiation", "FAIL");
            return ProbeResult.failed("mediaCodecAdvanced", ProbeResult.Status.FAILED, elapsed(started), data, error.getClass().getSimpleName());
        } finally {
            if (codec != null) {
                try {
                    codec.release();
                } catch (Throwable ignored) {
                    // Vendor implementations may throw during cleanup; the result is already recorded.
                }
            }
        }
    }

    private static List<Map<String, Object>> enumerateAvcDecoders() {
        List<Map<String, Object>> result = new ArrayList<>();
        int count = MediaCodecList.getCodecCount();
        for (int index = 0; index < count; index++) {
            MediaCodecInfo info;
            try {
                info = MediaCodecList.getCodecInfoAt(index);
            } catch (Throwable ignored) {
                continue;
            }
            if (info == null || info.isEncoder()) {
                continue;
            }
            boolean supportsAvc = false;
            String[] types;
            try {
                types = info.getSupportedTypes();
            } catch (Throwable ignored) {
                continue;
            }
            for (String type : types) {
                if ("video/avc".equalsIgnoreCase(type)) {
                    supportsAvc = true;
                    break;
                }
            }
            if (!supportsAvc) {
                continue;
            }
            Map<String, Object> codec = new LinkedHashMap<>();
            String name = info.getName();
            codec.put("name", name);
            codec.put("classification", likelySoftware(name) ? "likely software" : "likely hardware/vendor");
            try {
                MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType("video/avc");
                MediaCodecInfo.VideoCapabilities video = capabilities.getVideoCapabilities();
                if (video != null) {
                    codec.put("supportedWidths", String.valueOf(video.getSupportedWidths()));
                    codec.put("supportedHeights", String.valueOf(video.getSupportedHeights()));
                    codec.put("supportedFrameRates", String.valueOf(video.getSupportedFrameRates()));
                    codec.put("bitrateRange", String.valueOf(video.getBitrateRange()));
                }
            } catch (Throwable capabilityError) {
                codec.put("capabilityStatus", capabilityError.getClass().getSimpleName());
            }
            result.add(codec);
        }
        return result;
    }

    private static boolean likelySoftware(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.US);
        return lower.contains("google") || lower.contains("software") || lower.contains("ffmpeg");
    }

    private static long elapsed(long started) {
        return Math.max(0L, System.currentTimeMillis() - started);
    }
}
