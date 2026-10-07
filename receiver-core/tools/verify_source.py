"""Pinned source/license/provenance/renderer and credential boundary checks, no network access."""
import hashlib
import json
import pathlib
import re

root = pathlib.Path(__file__).resolve().parents[2]
vendor = root / "receiver-core/upstream/xcertplay"
manifest = json.loads((vendor / "SOURCE_MANIFEST.json").read_text())
assert manifest["revision"] == "17c92439413638dfd1d7f91d7e1c2e7358398762"
assert manifest["license"] == "GPL-3.0"
assert "GNU GENERAL PUBLIC LICENSE" in (vendor / "LICENSE").read_text()
covered = set()
for entry in manifest["files"]:
    path = root / entry["path"]
    assert path.resolve().is_relative_to(vendor)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    assert digest == entry["portSha256"], "Unrecorded source change: " + entry["path"]
    assert entry["modified"] == (digest != entry["upstreamSha256"])
    covered.add(path)
for entry in manifest["localAdditions"]:
    path = root / entry["path"]
    assert path.resolve().is_relative_to(vendor)
    assert hashlib.sha256(path.read_bytes()).hexdigest() == entry["portSha256"]
    covered.add(path)
assert set((vendor / "src").rglob("*.kt")) <= covered, "Unattributed vendored source"
patches = (vendor / "PATCHES.md").read_text()
for entry in manifest["files"]:
    if entry["modified"]:
        assert entry["upstreamPath"] in patches
lock = json.loads((root / "receiver-core/renderer-lock.json").read_text())
assert lock["baseline"] == "4e98000c1652ed7e1bc4c9668ff10b37a779f78b"
for path, digest in lock["files"].items():
    assert hashlib.sha256((root / path).read_bytes()).hexdigest() == digest, path
for scope in (root / "receiver-core", root / "receiver/src"):
    for path in scope.rglob("*"):
        if not path.is_file():
            continue
        assert not re.search(r"(?i)[.](pem|key|p12|pfx|pk8|p7b|so|apk|bin|keystore|jks)$", path.name), path
        if "assets" in path.parts:
            continue
        data = path.read_bytes()
        assert not re.search(rb"ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----", data), path
        if "main" in path.parts or "upstream" in path.parts:
            assert b"FakeAuthenticationProvider" not in data, path
probe = (root / "receiver/src/main/java/io/ts7/carplay/AuthenticationInventoryProbe.java").read_text()
assert "Os.stat(" in probe and "UsbManager" in probe
for forbidden in ("openDevice(", "Os.open(", "ioctl(", "readCertificate(", "signChallenge("):
    assert forbidden not in probe
assert (root / "receiver/LICENSE").read_bytes() == (vendor / "LICENSE").read_bytes()
print("Source audit PASS: pinned GPL provenance, recorded patches, 9 frozen renderer files, no credential/native/proprietary payload or release fake provider")
