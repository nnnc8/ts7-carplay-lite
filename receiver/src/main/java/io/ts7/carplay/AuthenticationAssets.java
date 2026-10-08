package io.ts7.carplay;

import android.content.res.AssetManager;
import io.ts7.carplay.core.ExperimentalDiPlayAuthenticationProvider;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** Explicit build-supplied DiPlay inputs only; never scan storage, another app or a hardware bus. */
final class AuthenticationAssets {
    private AuthenticationAssets() {}

    static void initialize(AssetManager assets, ExperimentalDiPlayAuthenticationProvider provider) {
        byte[] key = null;
        byte[] certificate = null;
        try {
            key = read(assets, "offline-mfi/identity.pk8");
            certificate = read(assets, "offline-mfi/certificate.p7b");
            provider.initialize(key, certificate);
        } catch (IOException ignored) {
            // An identity-free source/CI APK remains usable for diagnostics and local rendering.
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
            if (certificate != null) Arrays.fill(certificate, (byte) 0);
        }
    }

    private static byte[] read(AssetManager assets, String name) throws IOException {
        byte[] buffer = new byte[16 * 1024 + 1];
        try (InputStream input = assets.open(name)) {
            int size = 0;
            while (size < buffer.length) {
                int count = input.read(buffer, size, buffer.length - size);
                if (count < 0) break;
                size += count;
            }
            if (size == 0 || size > 16 * 1024) throw new IOException("AUTH_ASSET_BOUND");
            return Arrays.copyOf(buffer, size);
        } finally { Arrays.fill(buffer, (byte) 0); }
    }
}
