from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent
manifest_path = root / "SHA256SUMS"
excluded = {"RESULTS.md", "RESULTS.json", "SHA256SUMS"}
entries = []
for path in sorted(p for p in root.rglob("*") if p.is_file() and p.relative_to(root).as_posix() not in excluded):
    rel = path.relative_to(root).as_posix()
    entries.append((rel, hashlib.sha256(path.read_bytes()).hexdigest()))
manifest_text = "".join(f"{digest}  {rel}\n" for rel, digest in entries)
manifest_path.write_text(manifest_text, encoding="utf-8")
manifest_hash = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
for rel, digest in entries:
    assert hashlib.sha256((root / rel).read_bytes()).hexdigest() == digest
report_path = root / "RESULTS.json"
report = json.loads(report_path.read_text(encoding="utf-8"))
report["manifest"] = {
    "path": "docs/validation/master-program-docx-numbered-lists-20261019/SHA256SUMS",
    "entries": len(entries),
    "sha256": manifest_hash,
    "locallyVerified": f"{len(entries)}/{len(entries)}",
    "excludes": ["RESULTS.md", "RESULTS.json", "SHA256SUMS"],
}
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"entries={len(entries)}")
print(f"manifestSha256={manifest_hash}")
print(f"verified={len(entries)}/{len(entries)}")
