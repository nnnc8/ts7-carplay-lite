"""Reject accidental native ABI restriction, secrets, proprietary assets and pixel-copy APIs."""
import hashlib
import pathlib
import re
import sys
import subprocess
import zipfile

apk = pathlib.Path(sys.argv[1])
badging = subprocess.check_output([sys.argv[2], "dump", "badging", str(apk)], text=True)
assert re.search(r"(?:minSdkVersion|sdkVersion):'27'", badging) and "targetSdkVersion:'27'" in badging
assert "package: name='io.ts7.carplay'" in badging and "versionName='0.2-alpha'" in badging
assert set(re.search(r"native-code: (.*)", badging).group(1).replace("'", "").split()) == {"armeabi-v7a", "x86_64"}
assert "launchable-activity: name='io.ts7.carplay.MainActivity'" in badging
permissions = set(re.findall(r"uses-permission: name='([^']+)'", badging))
assert permissions == {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.ACCESS_WIFI_STATE", "android.permission.BLUETOOTH",
    "android.permission.BLUETOOTH_ADMIN", "android.permission.CHANGE_WIFI_STATE",
    "android.permission.CHANGE_WIFI_MULTICAST_STATE", "android.permission.ACCESS_FINE_LOCATION",
}, "Unexpected permission or missing declared purpose"
instrumented = apk.name.endswith("-instrumented.apk")
if not instrumented:
    assert "application-debuggable" not in badging, "Release preview must not be debuggable"
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert "classes.dex" in names and "AndroidManifest.xml" in names
    assert "assets/ts7-pattern.h264" in names
    libraries = [name for name in names if name.startswith("lib/")]
    assert set(libraries) == {"lib/armeabi-v7a/liblocal_hotspot_radio.so", "lib/x86_64/liblocal_hotspot_radio.so"}
    arm = archive.read("lib/armeabi-v7a/liblocal_hotspot_radio.so")
    assert arm[:5] == b"\x7fELF\x01" and int.from_bytes(arm[18:20], "little") == 40, "Actual ELF32 ARM required"
    x86 = archive.read("lib/x86_64/liblocal_hotspot_radio.so")
    assert x86[:5] == b"\x7fELF\x02" and int.from_bytes(x86[18:20], "little") == 62
    assert not any(re.search(r"(?i)(tlink|zlink|offline-mfi|[.](pk8|p7b|pem|key|p12|pfx|jks|keystore)$)", name) for name in names)
    assert "assets/licenses/DiPlay-GPL-3.0.txt" in names and "assets/licenses/TS7-NOTICES.md" in names
    dex = b"".join(archive.read(name) for name in names if re.fullmatch(r"classes\d*[.]dex", name))
    for core in (b"CarPlayController;", b"DiPlayReceiverCore;", b"CarPlayMediaEngine;", b"DiPlayMediaBridge;"):
        assert core in dex, "Missing actual DiPlay core/renderer seam"
    for excluded in (b"LocalMfiAuthenticationClient;", b"RemoteMfiAuthenticationClient;", b"BydNavigationOutputs;", b"DiPlayActivity;", b"FakeAuthenticationProvider;"):
        assert excluded not in dex, "Excluded upstream/test-only code was packaged"
    assert instrumented == (b"Lio/ts7/carplay/RendererInstrumentation;" in dex), "Test entry point must be CI-only"
    assert not re.search(rb"ghp_|github_pat_|GITHUB_TOKEN|BEGIN [A-Z ]*PRIVATE KEY", dex)
    for forbidden in (b"Landroid/graphics/Bitmap;", b"Landroid/webkit/WebView;", b"Landroid/graphics/Canvas;",
                      b"Landroid/location/LocationManager;", b"Landroid/telephony/TelephonyManager;",
                      b"Landroid/accounts/AccountManager;"):
        assert forbidden not in dex, "Forbidden API reference: " + forbidden.decode()
    asset = archive.read("assets/ts7-pattern.h264")
    source = pathlib.Path(__file__).resolve().parents[1] / "src/main/assets/ts7-pattern.h264"
    assert hashlib.sha256(asset).digest() == hashlib.sha256(source.read_bytes()).digest()
    assert len(asset) <= 1024 * 1024
print("APK inspection PASS: API27, actual source-built ELF32 ARM JNI, DiPlay core + unchanged Surface seam, no credentials/vendor UI/GPS/pixel-copy APIs")
