import json
import sys
from pathlib import Path
import fitz
from pypdf import PdfReader

path = Path(sys.argv[1])
reader = PdfReader(str(path))
pypdf_pages = [page.extract_text() or "" for page in reader.pages]
doc = fitz.open(path)
pages = []
for index, page in enumerate(doc):
    text = page.get_text("text")
    header = text.find("Descripción")
    data = text.find("dato")
    words = page.get_text("words")
    bounds = page.rect
    out_of_bounds = [
        word for word in words
        if word[0] < bounds.x0 or word[1] < bounds.y0 or word[2] > bounds.x1 or word[3] > bounds.y1
    ]
    pages.append({
        "page": index + 1,
        "widthPoints": round(bounds.width, 2),
        "heightPoints": round(bounds.height, 2),
        "pypdfHasHeader": "Descripción" in pypdf_pages[index],
        "fitzHeaderBeforeCellText": header >= 0 and data >= 0 and header < data,
        "headerFound": header >= 0,
        "cellTextFound": data >= 0,
        "wordCount": len(words),
        "outOfBoundsWords": len(out_of_bounds),
    })
print(json.dumps({
    "readerVersions": {"pypdf": "6.10.0", "PyMuPDF": fitz.VersionBind},
    "pdfBytes": path.stat().st_size,
    "pages": len(pages),
    "pageResults": pages,
    "pypdfTotalDatoTokens": sum(text.count("dato") for text in pypdf_pages),
    "allPagesHaveHeaderInBothReaders": all(p["pypdfHasHeader"] and p["headerFound"] for p in pages),
    "allContinuationHeadersPrecedeCellText": all(p["fitzHeaderBeforeCellText"] for p in pages[1:]),
    "allPageTextWithinBounds": all(p["outOfBoundsWords"] == 0 for p in pages),
}, indent=2, ensure_ascii=False))
