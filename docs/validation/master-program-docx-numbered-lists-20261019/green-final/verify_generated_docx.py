from pathlib import Path
import hashlib
import json
import zipfile
import docx
from docx import Document

path = Path(__file__).with_name("ordered-list.docx")
assert path.is_file(), "generated DOCX missing"
with zipfile.ZipFile(path) as archive:
    bad = archive.testzip()
    assert bad is None, f"ZIP CRC failed: {bad}"
    names = set(archive.namelist())
    required = {
        "[Content_Types].xml", "_rels/.rels", "word/document.xml",
        "word/_rels/document.xml.rels", "word/styles.xml", "word/numbering.xml",
    }
    assert required <= names, f"missing OPC parts: {sorted(required - names)}"

document = Document(str(path))
paragraphs = []
for paragraph in document.paragraphs:
    ppr = paragraph._p.pPr
    numpr = ppr.numPr if ppr is not None else None
    num_id = str(numpr.numId.val) if numpr is not None and numpr.numId is not None else None
    level = str(numpr.ilvl.val) if numpr is not None and numpr.ilvl is not None else None
    paragraphs.append({"text": paragraph.text, "numId": num_id, "ilvl": level})
assert paragraphs == [
    {"text": "Primer paso", "numId": "2", "ilvl": "0"},
    {"text": "Segundo paso", "numId": "2", "ilvl": "0"},
    {"text": "Texto intermedio", "numId": None, "ilvl": None},
    {"text": "Nuevo primer paso", "numId": "3", "ilvl": "0"},
    {"text": "Nota con viñeta", "numId": "1", "ilvl": "0"},
], paragraphs
result = {
    "reader": f"python-docx {docx.__version__}",
    "artifact": path.name,
    "bytes": path.stat().st_size,
    "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    "zipCrc": "PASS",
    "requiredOpcParts": "PASS",
    "paragraphs": paragraphs,
    "numberingIds": "PASS",
}
out = path.with_name("independent-reader.json")
out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(result, ensure_ascii=False, indent=2))
