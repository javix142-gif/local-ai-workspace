#!/usr/bin/env python3
"""Reconcile this focused JUnit XML into RESULTS and the current program state."""

import hashlib
import json
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[3]
evidence = repo / "docs/validation/master-program-docx-list-markers-20261019"
results_path = evidence / "RESULTS.json"
results = json.loads(results_path.read_text(encoding="utf-8"))

for attempt in ("red", "intermediateGreen", "greenFinal"):
    record = results["host"][attempt]
    xml_path = evidence / record["xml"]
    root = ET.parse(xml_path).getroot()
    counts = {key: int(root.attrib.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
    record["counts"] = counts
    record["xmlSha256"] = hashlib.sha256(xml_path.read_bytes()).hexdigest()
    if attempt == "red":
        record["passed"] = counts["tests"] - counts["failures"] - counts["errors"] - counts["skipped"]
    else:
        record["passed"] = counts["tests"] - counts["failures"] - counts["errors"] - counts["skipped"]

manifest_path = evidence / "SHA256SUMS"
if manifest_path.is_file():
    manifest_lines = [line for line in manifest_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    results["manifest"] = {
        "path": "docs/validation/master-program-docx-list-markers-20261019/SHA256SUMS",
        "entries": len(manifest_lines),
        "sha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        "locallyVerified": f"{len(manifest_lines)}/{len(manifest_lines)}",
        "excludes": ["RESULTS.md", "RESULTS.json", "SHA256SUMS"],
    }

results_path.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

state_path = repo / "docs/development/EXECUTION_STATE.json"
state = json.loads(state_path.read_text(encoding="utf-8"))
continuation = state.setdefault("latestMasterProgramContinuation", {})
continuation.update({
    "status": "ACTIVE_PREPARATORY_IMPLEMENTATION",
    "codeSha": results["testedTreeSha"],
    "currentIncrement": "M02-03 explicit DOCX list marker extraction (preparatory)",
    "evidence": "docs/validation/master-program-docx-list-markers-20261019/RESULTS.md",
    "next": "Continue sequentially with a source-backed preparatory implementation gap; keep M02-03 TODO until M02-01 and the complete M02-03 definition of done are satisfied.",
})
state["latestM02_03DocxListMarkersPreparation"] = results
state_path.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

for key, record in results["host"].items():
    if key in ("red", "intermediateGreen", "greenFinal"):
        print(key, record["counts"], record["xmlSha256"])
print("updated RESULTS.json and EXECUTION_STATE.json; source task remains TODO")
