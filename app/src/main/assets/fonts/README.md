# PDF font assets

The PDF renderer embeds Noto Sans static regular and bold TrueType fonts so PDF text outside the
PDF Standard 14 / WinAnsi repertoire can be measured, drawn, and extracted without replacing or
dropping supported glyphs. PDFBox subsets the fonts into each generated PDF.

- Upstream: `https://github.com/notofonts/noto-fonts`
- Pinned upstream commit: `ffebf8c1ee449e544955a7e813c54f9b73848eac`
- Regular: `hinted/ttf/NotoSans/NotoSans-Regular.ttf`, 569,208 bytes,
  SHA-256 `b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5`
- Bold: `hinted/ttf/NotoSans/NotoSans-Bold.ttf`, 575,740 bytes,
  SHA-256 `c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a`
- License: SIL Open Font License 1.1, copied as [OFL.txt](OFL.txt),
  SHA-256 `0dab92d0544f7b233403f14b84a663bdbfa746982eda629e7f4f9ffe1b036feb`.

Coverage was checked from the pinned font `cmap`: Spanish, Polish, Greek, Vietnamese, and Euro
symbols used by the regression fixture are present. CJK and emoji glyphs are not present in these
two files; generation continues to fail explicitly for a missing glyph rather than silently
substituting or deleting it. Adding further script-specific fonts requires a separate size and
license review.
