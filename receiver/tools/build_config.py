"""Generate only non-secret public endpoint configuration in ignored build output."""
import json
import pathlib
import sys
from urllib.parse import urlsplit

output, endpoint = sys.argv[1:]
if endpoint:
    parsed = urlsplit(endpoint)
    if (parsed.scheme != "https" or parsed.hostname != "ts7-carplay-lite-relay.vercel.app"
            or parsed.path != "/api/carplay-diagnostics" or parsed.query or parsed.fragment
            or parsed.username or parsed.password or parsed.port):
        raise SystemExit("Only the public alpha relay endpoint is allowed")
pathlib.Path(output).write_text(
    "package io.ts7.carplay;\n"
    "public final class BuildConfig {\n"
    '    public static final String VERSION_NAME = "1.0.0-dev";\n'
    "    public static final String UPLOAD_URL = " + json.dumps(endpoint) + ";\n"
    "    private BuildConfig() {}\n}\n", encoding="utf-8")
