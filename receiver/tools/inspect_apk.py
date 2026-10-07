"""Reject accidental native ABI restriction, secrets, proprietary assets and pixel-copy APIs."""
import hashlib
import pathlib
import re
import sys
import subprocess
import zipfile
import struct

apk = pathlib.Path(sys.argv[1])
badging = subprocess.check_output([sys.argv[2], "dump", "badging", str(apk)], text=True)
assert re.search(r"(?:minSdkVersion|sdkVersion):'27'", badging) and "targetSdkVersion:'27'" in badging
assert "package: name='io.ts7.carplay'" in badging and "versionName='0.1-alpha-core-preview'" in badging
assert "native-code:" not in badging
assert "launchable-activity: name='io.ts7.carplay.MainActivity'" in badging
permissions = set(re.findall(r"uses-permission: name='([^']+)'", badging))
assert permissions == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN", "android.permission.CHANGE_WIFI_STATE",
    "android.permission.ACCESS_COARSE_LOCATION",
}, "Unexpected permission or missing declared purpose"
instrumented = apk.name.endswith("-instrumented.apk")
if not instrumented:
    assert "application-debuggable" not in badging, "Release preview must not be debuggable"
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert "classes.dex" in names and "AndroidManifest.xml" in names
    assert "assets/ts7-pattern.h264" in names
    assert "assets/licenses/GPL-3.0.txt" in names
    assert "assets/licenses/Kotlin-Apache-2.0.txt" in names
    assert "assets/licenses/BouncyCastle-MIT.txt" in names
    assert not any(name.startswith("lib/") for name in names), "Java-only build must not become host-native"
    assert not any(re.search(r"(?i)(tlink|zlink|offline-mfi|[.](pk8|p7b|pem|key|so|p12|pfx|jks|keystore)$)", name) for name in names)
    dex_files = [archive.read(name) for name in names if re.fullmatch(r"classes(?:[0-9]+)?[.]dex", name)]
    dex = b"".join(dex_files)
    assert instrumented == (b"Lio/ts7/carplay/RendererInstrumentation;" in dex), "Test entry point must be CI-only"
    assert not re.search(rb"ghp_|github_pat_|GITHUB_TOKEN|BEGIN [A-Z ]*PRIVATE KEY", dex)
    for forbidden in (b"Landroid/graphics/Bitmap;", b"Landroid/webkit/WebView;", b"Landroid/graphics/Canvas;",
                      b"Landroid/location/LocationManager;", b"Landroid/media/AudioRecord;",
                      b"FakeAuthenticationProvider", b"Landroid/accounts/AccountManager;"):
        assert forbidden not in dex, "Forbidden API reference: " + forbidden.decode()
    # Resolve method owners: protocol models legitimately have e.g. getDeviceId/getPublicKey.
    # A raw string ban would confuse those with Android device-identity APIs.
    def methods(data):
        def u32(at):
            return struct.unpack_from("<I", data, at)[0]
        strings = []
        for index in range(u32(56)):
            at = u32(u32(60) + index * 4)
            while data[at] & 128:
                at += 1
            at += 1
            end = data.index(0, at)
            strings.append(data[at:end].decode("utf-8", errors="replace"))
        types = [strings[u32(u32(68) + index * 4)] for index in range(u32(64))]
        for index in range(u32(88)):
            owner, _, name = struct.unpack_from("<HHI", data, u32(92) + index * 8)
            yield types[owner], strings[name]
    denied = {"getSSID", "getBSSID", "getMacAddress", "getIpAddress", "getSerial",
              "getDeviceId", "getImei", "getSubscriberId", "getLastKnownLocation", "requestLocationUpdates"}
    for data in dex_files:
        for owner, method in methods(data):
            assert not (owner.startswith("Landroid/") and method in denied), (owner, method)
    asset = archive.read("assets/ts7-pattern.h264")
    source = pathlib.Path(__file__).resolve().parents[1] / "src/main/assets/ts7-pattern.h264"
    assert hashlib.sha256(asset).digest() == hashlib.sha256(source.read_bytes()).digest()
    assert len(asset) <= 1024 * 1024
print("APK inspection PASS: API-27 manifest, Java-only/ARMv7-compatible, synthetic H.264 asset, no credentials/proprietary binaries/pixel-copy APIs")
