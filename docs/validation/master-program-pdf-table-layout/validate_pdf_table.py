#!/usr/bin/env python3
"""Independent structural/text check for the generated synthetic PDF table."""
import hashlib
import json
import sys
from pathlib import Path

import fitz
import pypdf
from pypdf import PdfReader


pdf_path = Path(sys.argv[1])
preview_path = Path(sys.argv[2])
payload = pdf_path.read_bytes()
fitz_document = fitz.open(stream=payload, filetype="pdf")
pypdf_document = PdfReader(str(pdf_path))
fitz_text = "\n".join(page.get_text() for page in fitz_document)
pypdf_text = "\n".join(page.extract_text() or "" for page in pypdf_document.pages)
word_bounds = [
    (index, word[:4])
    for index, page in enumerate(fitz_document)
    for word in page.get_text("words")
    if not page.rect.contains(fitz.Rect(word[:4]))
]
drawings = [drawing for page in fitz_document for drawing in page.get_drawings()]
drawings_outside = [
    (index, drawing["rect"])
    for index, page in enumerate(fitz_document)
    for drawing in page.get_drawings()
    if not page.rect.contains(drawing["rect"])
]

assert len(fitz_document) == len(pypdf_document.pages) == 3
assert fitz_text.count("dato") == pypdf_text.count("dato") == 300
assert "INICIO" in fitz_text and "FINAL" in fitz_text and "Cierre visible" in fitz_text
assert "|" not in fitz_text and "|" not in pypdf_text
assert drawings, "No vector table grid was found"
assert not word_bounds, f"Text outside page bounds: {word_bounds[:5]}"
assert not drawings_outside, f"Drawing outside page bounds: {drawings_outside[:5]}"
assert all(abs(page.rect.width - 612) < 0.01 and abs(page.rect.height - 792) < 0.01 for page in fitz_document)

preview_path.parent.mkdir(parents=True, exist_ok=True)
fitz_document[0].get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False).save(preview_path)
result = {
    "status": "PASS",
    "readers": {"PyMuPDF": fitz.VersionBind, "pypdf": pypdf.__version__},
    "path": str(pdf_path),
    "bytes": len(payload),
    "sha256": hashlib.sha256(payload).hexdigest(),
    "pages": len(fitz_document),
    "pageSizePoints": [fitz_document[0].rect.width, fitz_document[0].rect.height],
    "tableVectorDrawingCount": len(drawings),
    "cellTextOccurrenceCount": fitz_text.count("dato"),
    "pipeDelimiterPresent": False,
    "textOutsidePageBounds": len(word_bounds),
    "drawingsOutsidePageBounds": len(drawings_outside),
    "allPagesLetterSize": True,
    "preview": str(preview_path),
    "previewBytes": preview_path.stat().st_size,
    "previewSha256": hashlib.sha256(preview_path.read_bytes()).hexdigest(),
}
print(json.dumps(result, ensure_ascii=False, indent=2))
