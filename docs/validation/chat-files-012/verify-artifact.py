"""Record executed checks and compare native hashes with the verified 0.1.11 baseline."""
from pathlib import Path
import hashlib
import json
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[3]
evidence = Path(__file__).resolve().parent
apk = root / "dist/apk/local-ai-workspace-0.1.12-chat-files-arm64.apk"
baseline = json.loads((root / "docs/validation/apk-011.json").read_text())

def digest(stream):
    value = hashlib.sha256()
    while block := stream.read(1024 * 1024):
        value.update(block)
    return value.hexdigest()

def file_hash(path):
    with path.open("rb") as stream:
        return digest(stream)

tests = {}
check_log = (evidence / "gradle-checks-corrected.log").read_text()
test_task_cache = {}
for module in ("app", "litert-compat", "llama-runtime"):
    totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    files = list((root / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    for path in files:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.get(key, "0"))
        target = evidence / "unit-tests" / module / path.name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
    tests[module] = totals
    test_task_cache[module] = next((result for result in ("FROM-CACHE", "UP-TO-DATE", "NO-SOURCE")
        if f":{module}:testDebugUnitTest {result}" in check_log), "EXECUTED")
assert tests["app"]["tests"] >= 167
assert not any(counts[key] for counts in tests.values() for key in ("failures", "errors", "skipped"))

lint = {"errors": 0, "warnings": 0}
for path in root.glob("*/build/reports/lint-results-debug.xml"):
    for issue in ET.parse(path).getroot().findall("issue"):
        severity = issue.get("severity")
        if severity in ("Error", "Fatal"):
            lint["errors"] += 1
        elif severity == "Warning":
            lint["warnings"] += 1
    shutil.copy2(path, evidence / f"{path.parents[2].name}-lint.xml")
assert lint["errors"] == 0
for log in ("gradle-checks-corrected.log", "release-build.log"):
    assert "BUILD SUCCESSFUL" in (evidence / log).read_text()

signature = (evidence / "signature.log").read_text()
cert = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", signature).group(1)
assert cert == "f9085e76beaabc923e5f81bee66d35aca8bbc1aac59b9a3e29e07a6bf7afca1b"
badging = (evidence / "badging.log").read_text()
assert "package: name='com.localai.workspace' versionCode='13' versionName='0.1.12'" in badging
assert "sdkVersion:'29'" in badging and "targetSdkVersion:'35'" in badging
native = {}
with zipfile.ZipFile(apk) as new:
    libs = [name for name in new.namelist() if name.startswith("lib/") and name.endswith(".so")]
    assert libs and all(name.startswith("lib/arm64-v8a/") for name in libs)
    assert set(libs) == set(baseline["nativeSha256"])
    for name in libs:
        with new.open(name) as stream:
            native[name] = digest(stream)
        assert native[name] == baseline["nativeSha256"][name], name
    markers = {text: False for text in ("Start chat", "Start project", "Active model", "ChatSessions", "Local files", "Summarize", "DOCUMENT_CONTEXT_UNAVAILABLE", "Stop generation", "LITERT_INTEGRITY_REUSE", "ChatRuntimePool")}
    for name in new.namelist():
        if re.fullmatch(r"classes\d*\.dex", name):
            data = new.read(name)
            for text in markers:
                markers[text] |= text.encode() in data
    assert all(markers.values()), markers
    assert not any(name.endswith((".litertlm", ".gguf")) for name in new.namelist())

manifest = {
    "name": apk.name, "path": str(apk), "sizeBytes": apk.stat().st_size, "sha256": file_hash(apk),
    "package": "com.localai.workspace", "versionName": "0.1.12", "versionCode": 13,
    "minSdk": 29, "targetSdk": 35, "abi": "arm64-v8a", "certificateSha256": cert,
    "nativeSha256": native, "nativeIdenticalTo011": True,
    "nativeComparisonReference": "Previously verified nativeSha256 values in docs/validation/apk-011.json",
    "dexMarkers": markers,
    "tests": tests, "testTaskResults": test_task_cache, "lint": lint, "roomSchemaVersion": 3,
    "validation": {"testDebugUnitTest": "PASS", "assembleDebug": "PASS", "assembleRelease": "PASS",
        "lintDebug": "PASS_WITH_WARNINGS" if lint["warnings"] else "PASS", "signature": "PASS",
        "nativeByteComparison": "PASS", "androidDeviceValidation": "NOT_EXECUTED",
        "newRuntimeValidation": "NOT_EXECUTED"},
}
target = root / "docs/validation/apk-012.json"
if target.exists():
    existing = json.loads(target.read_text())
    if existing.get("sha256") == manifest["sha256"]:
        for key in ("drive", "executedBuildLogs", "initialCheck", "limitations", "sourceChanges"):
            if key in existing:
                manifest[key] = existing[key]
        if "driveDelivery" in existing.get("validation", {}):
            manifest["validation"]["driveDelivery"] = existing["validation"]["driveDelivery"]
target.write_text(json.dumps(manifest, indent=2) + "\n")
print(json.dumps({"sizeBytes": manifest["sizeBytes"], "sha256": manifest["sha256"], "tests": tests,
    "lint": lint, "nativeIdenticalTo011": True, "dexMarkers": markers}, indent=2))
