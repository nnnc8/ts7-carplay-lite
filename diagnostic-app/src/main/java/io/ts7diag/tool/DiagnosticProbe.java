package io.ts7diag.tool;

import android.content.Context;

/** A single best-effort diagnostic operation. */
public interface DiagnosticProbe {
    String getKey();

    ProbeResult run(Context context);
}
