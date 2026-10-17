import json
from pathlib import Path
import fitz
import pypdf
from pypdf import PdfReader

base = Path(__file__).parent
results = {"independent_readers": ["PyMuPDF " + fitz.VersionBind, "pypdf " + pypdf.__version__], "fixtures": []}

def inspect(name, title, expect_marker):
    path = base / name
    data = path.read_bytes()
    assert data.startswith(b"%PDF-"), f"{name}: missing PDF signature"
    doc = fitz.open(path)
    reader = PdfReader(str(path))
    pages = []
    all_spans = []
    all_words = []
    for page in doc:
        text_dict = page.get_text("dict")
        spans = [span for block in text_dict["blocks"] if "lines" in block for line in block["lines"] for span in line["spans"]]
        words = page.get_text("words")
        for span in spans:
            x0, y0, x1, y1 = span["bbox"]
            assert x0 >= -0.1 and y0 >= -0.1 and x1 <= page.rect.width + 0.1 and y1 <= page.rect.height + 0.1, f"{name}: text bounds outside page: {span}"
        pages.append({"width_pt": page.rect.width, "height_pt": page.rect.height, "text": page.get_text(), "span_sizes_pt": sorted({round(float(s["size"]), 2) for s in spans})})
        all_spans.extend(spans)
        all_words.extend(str(word[4]) for word in words)
    fitz_text = "\n".join(page["text"] for page in pages)
    reader_text = "\n".join(page.extract_text() or "" for page in reader.pages)
    assert len(doc) == 2, f"{name}: expected 2 pages, got {len(doc)}"
    assert all(abs(p["width_pt"] - 612) < 0.1 and abs(p["height_pt"] - 792) < 0.1 for p in pages), f"{name}: unexpected page size"
    assert title in fitz_text and title in reader_text, f"{name}: title missing in independent reader"
    assert "Sección y formato" in fitz_text and "Segunda página" in fitz_text and "Correcto" in fitz_text, f"{name}: expected content missing"
    if expect_marker:
        assert "WORD_BOUNDARY_MARKER" in all_words, f"{name}: marker was split across extracted words: {all_words}"
    title_spans = [s for s in all_spans if title in s["text"]]
    heading_spans = [s for s in all_spans if "Sección y formato" in s["text"]]
    body_spans = [s for s in all_spans if "Cierre:" in s["text"]]
    assert title_spans and all(abs(float(s["size"]) - 20) < 0.1 for s in title_spans), f"{name}: title size is not 20pt"
    assert heading_spans and all(abs(float(s["size"]) - 16) < 0.1 for s in heading_spans), f"{name}: heading size is not 16pt"
    assert body_spans and all(abs(float(s["size"]) - 12) < 0.1 for s in body_spans), f"{name}: body size is not 12pt"
    return {"file": name, "bytes": len(data), "sha256": __import__("hashlib").sha256(data).hexdigest(), "reader_pages": len(reader.pages), "page_geometry_pt": [[round(p["width_pt"], 2), round(p["height_pt"], 2)] for p in pages], "font_sizes_pt": sorted({round(float(s["size"]), 2) for s in all_spans}), "title_font_sizes_pt": sorted({round(float(s["size"]), 2) for s in title_spans}), "heading_font_sizes_pt": sorted({round(float(s["size"]), 2) for s in heading_spans}), "body_font_sizes_pt": sorted({round(float(s["size"]), 2) for s in body_spans}), "marker_is_single_word": ("WORD_BOUNDARY_MARKER" in all_words) if expect_marker else None, "all_text_bounds_inside_page": True, "checks": "PASS"}

results["fixtures"].append(inspect("fixture-v1.pdf", "Informe de prueba", True))
results["fixtures"].append(inspect("fixture-v2.pdf", "Informe revisado", False))
(base / "independent-validation.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
print(json.dumps(results, ensure_ascii=False, indent=2))
