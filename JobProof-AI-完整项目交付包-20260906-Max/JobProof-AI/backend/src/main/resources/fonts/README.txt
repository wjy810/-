NotoSansSC-Regular.ttf
NotoSerifSC-Variable.ttf

Purpose: embedded, subsetted CJK font for portable resume PDF output.
Families: Noto Sans SC and Noto Serif SC
License: SIL Open Font License 1.1 (see OFL.txt)
Upstream: https://github.com/notofonts/noto-cjk
Serif upstream: https://github.com/google/fonts/tree/main/ofl/notoserifsc
Static instance: wght=400 from the upstream TrueType variable font
PDF extraction fix: CJK Radicals Supplement and Kangxi Radicals cmap aliases
were removed with scripts/sanitize_pdf_font.py. Shared glyph IDs otherwise make
PDFBox map common characters such as 大 and 人 to radical code points.
SHA-256: C442EA84310B259FD3DBEFCF7F69BBDED799FD18EFBFE2F907424E488C76957A
Noto Serif SC SHA-256: 857BB567173C07EEDBB8A2407099EFC906AC0486224D669F77FD7C6AD3D7A876

The font is bundled so PDF rendering does not depend on fonts installed on the
application host or on the viewer machine.
