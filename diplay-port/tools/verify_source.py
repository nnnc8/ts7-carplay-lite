#!/usr/bin/env python3
"""Offline exact-file provenance, frozen-renderer and credential exclusion gates."""
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
VENDOR = ROOT / "third_party/diplay-base"
PIN = "c8884adcc75bfda3c134db63877bd6c6f83beb74"
manifest = json.loads((VENDOR / "SOURCE_MANIFEST.json").read_text())
assert manifest["upstreamCommit"] == PIN
assert manifest["primaryBase"] == "https://github.com/programmerguohuajing/DiPlay-Legacy-Android"
entries = manifest["files"]
assert len(entries) == 71 and len({entry["path"] for entry in entries}) == 71
for entry in entries:
    path = Path(entry["path"])
    assert not path.is_absolute() and ".." not in path.parts
    data = (VENDOR / path).read_bytes()
    assert hashlib.sha256(data).hexdigest() == entry["portedSha256"], str(path)
    assert re.fullmatch(r"[0-9a-f]{40}", entry["upstreamGitBlob"])
actual = {str(path.relative_to(VENDOR)) for path in VENDOR.rglob("*") if path.is_file()}
assert actual == {entry["path"] for entry in entries} | {"SOURCE_MANIFEST.json", "PATCHES.md"}
lock = json.loads((ROOT / "diplay-port/renderer-lock.json").read_text())
for name, digest in lock["files"].items():
    assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == digest, name
assert len(lock["files"]) == 9
assert (ROOT / "LICENSE").read_bytes() == (VENDOR / "LICENSE").read_bytes()
for name in ("Apache-2.0.txt", "Bouncy-Castle-LICENSE.txt", "SLF4J-LICENSE.txt"):
    assert (VENDOR / "docs/licenses/dependencies" / name).stat().st_size > 500
roots = (VENDOR / "shared", ROOT / "diplay-port/src/main", ROOT / "receiver/src/main/java")
for base in roots:
    for path in base.rglob("*"):
        if not path.is_file():
            continue
        assert path.suffix in {".kt", ".java", ".c", ".mk"}, str(path)
        data = path.read_text()
        assert not re.search(r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY)", data), str(path)
        assert not re.search(r"(?:android\.location\.LocationManager|android\.telephony\.TelephonyManager|android\.accounts\.AccountManager|android\.hardware\.usb)", data), str(path)
        assert not re.search(r"class\s+(?:FakeAuthenticationProvider|OfflineMfiAuthenticator|LocalMfiAuthenticator|Baidu[A-Za-z]*|Byd[A-Za-z]*)\b", data), str(path)
        if base == VENDOR / "shared":
            assert not re.search(r"\bLog\.(?:v|d|i|w|e)\s*\(", data), str(path)
            assert 'InetAddress.getByName("0.0.0.0")' not in data, str(path)
            assert 'InetAddress.getByName("::")' not in data, str(path)
appmk = (VENDOR / "shared/src/main/jni/Application.mk").read_text()
assert "APP_PLATFORM := android-27" in appmk
assert "APP_ABI := armeabi-v7a x86_64" in appmk
native = (VENDOR / "shared/src/main/jni/Android.mk").read_text()
assert re.search(r"^LOCAL_SRC_FILES\s*:=\s*local_hotspot_radio\.c\s*$", native, re.MULTILINE)
assert len(re.findall(r"^LOCAL_MODULE\s*:=", native, re.MULTILINE)) == 1
print("Source audit PASS: 71 pinned source/notice hashes, GPL/notices, nine frozen renderer files, no embedded credentials/proprietary binaries/vendor or GPS providers")
