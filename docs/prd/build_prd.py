# Renders prd_content.py to AppCleaner_PRD_v1_1.docx (python-docx) and .pdf (reportlab).
import re, sys
from prd_content import B, INFO, INTRO_NOTE, HEADER, APP

ACCENT = "0F9D8A"; BODY = "333F48"; BORDER = "D0D5DA"; SHADE = "EEF1F3"; CODE = "0B6E61"
OUT = "AppCleaner_PRD_v1_1"
TOKEN = re.compile(r"(\*\*.+?\*\*|`.+?`|\*[^*\s][^*]*?\*)")

def runs(text):
    for part in TOKEN.split(text):
        if not part: continue
        if part.startswith("**"): yield part[2:-2], "b"
        elif part.startswith("`"): yield part[1:-1], "c"
        elif part.startswith("*") and len(part) > 2: yield part[1:-1], "i"
        else: yield part, ""

# ------------------------------------------------------------------ DOCX
def build_docx():
    from docx import Document
    from docx.shared import Pt, RGBColor, Cm, Twips
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.oxml.ns import qn
    from docx.oxml import OxmlElement

    d = Document()
    sec = d.sections[0]
    sec.page_width, sec.page_height = Twips(12240), Twips(15840)
    for m in ("left_margin", "right_margin"): setattr(sec, m, Cm(2.0))
    sec.top_margin = sec.bottom_margin = Cm(2.0)
    st = d.styles["Normal"]; st.font.name = "Arial"; st.font.size = Pt(10); st.font.color.rgb = RGBColor.from_string(BODY)
    st.paragraph_format.space_after = Pt(4); st.paragraph_format.line_spacing = 1.15
    for name, size in (("Heading 1", 14), ("Heading 2", 11.5)):
        h = d.styles[name]; h.font.name = "Arial"; h.font.size = Pt(size); h.font.bold = True
        h.font.color.rgb = RGBColor.from_string(ACCENT if name == "Heading 1" else "1F2937")
        h.paragraph_format.space_before = Pt(14 if name == "Heading 1" else 10); h.paragraph_format.space_after = Pt(6)
        h.paragraph_format.keep_with_next = True
        rpr = h.element.get_or_add_rPr(); f = rpr.find(qn("w:rFonts"))
        if f is None: f = OxmlElement("w:rFonts"); rpr.append(f)
        for a in ("w:ascii", "w:hAnsi", "w:cs"): f.set(qn(a), "Arial")
    for name in ("List Bullet", "List Bullet 2", "List Number"):
        s = d.styles[name]; s.font.name = "Arial"; s.font.size = Pt(10); s.paragraph_format.space_after = Pt(3)

    def fill(par, text, size=None, color=None):
        for t, k in runs(text):
            r = par.add_run(t)
            if k == "b": r.bold = True
            if k == "i": r.italic = True
            if k == "c": r.font.name = "Courier New"; r.font.color.rgb = RGBColor.from_string(CODE); r.font.size = Pt(9)
            if size and k != "c": r.font.size = Pt(size)
            if color and k != "c": r.font.color.rgb = RGBColor.from_string(color)
        return par

    def border_bottom(par, color=ACCENT, sz="8"):
        pPr = par._p.get_or_add_pPr(); b = OxmlElement("w:pBdr"); e = OxmlElement("w:bottom")
        for k, v in (("w:val", "single"), ("w:sz", sz), ("w:space", "2"), ("w:color", color)): e.set(qn(k), v)
        b.append(e); pPr.append(b)

    def shade(cell, color):
        tcPr = cell._tc.get_or_add_tcPr(); s = OxmlElement("w:shd")
        s.set(qn("w:val"), "clear"); s.set(qn("w:color"), "auto"); s.set(qn("w:fill"), color); tcPr.append(s)

    def borders(table):
        tblPr = table._tbl.tblPr; b = OxmlElement("w:tblBorders")
        for edge in ("top", "left", "bottom", "right", "insideH", "insideV"):
            e = OxmlElement(f"w:{edge}")
            for k, v in (("w:val", "single"), ("w:sz", "4"), ("w:color", BORDER)): e.set(qn(k), v)
            b.append(e)
        tblPr.append(b)

    def cant_split(row):
        trPr = row._tr.get_or_add_trPr(); e = OxmlElement("w:cantSplit"); trPr.append(e)

    def set_widths(table, widths):
        table.autofit = False
        for row in table.rows:
            for c, w in zip(row.cells, widths): c.width = Twips(w)

    # running header + page-number footer
    hp = sec.header.paragraphs[0]; fill(hp, HEADER, 8.5, "6B7280"); border_bottom(hp, BORDER, "4")
    fp = sec.footer.paragraphs[0]; fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    def field(par, instr):
        r = par.add_run(); r.font.size = Pt(8.5)
        for tag, txt in (("begin", None), (None, instr), ("end", None)):
            if tag: e = OxmlElement("w:fldChar"); e.set(qn("w:fldCharType"), tag)
            else: e = OxmlElement("w:instrText"); e.set(qn("xml:space"), "preserve"); e.text = txt
            r._r.append(e)
    r = fp.add_run("Page "); r.font.size = Pt(8.5); field(fp, "PAGE")
    r = fp.add_run(" of "); r.font.size = Pt(8.5); field(fp, "NUMPAGES")

    # title block
    t = d.add_paragraph(); fill(t, "Product Requirements Document (PRD)", 20, "111827"); t.runs[0].bold = True
    s = d.add_paragraph(); fill(s, f"{APP} — Uninstall Apps  ·  Android V1", 12, ACCENT)
    tb = d.add_table(rows=0, cols=2); borders(tb); tb.alignment = WD_TABLE_ALIGNMENT.LEFT
    for k, v in INFO:
        row = tb.add_row(); cant_split(row)
        fill(row.cells[0].paragraphs[0], f"**{k}**"); shade(row.cells[0], SHADE)
        fill(row.cells[1].paragraphs[0], v)
    set_widths(tb, [2400, 6960])
    n = d.add_paragraph(); n.paragraph_format.space_before = Pt(8); fill(n, INTRO_NOTE, 9.5)

    num = 0
    for blk in B:
        kind = blk[0]
        if kind == "h1":
            h = d.add_heading(level=1); fill(h, blk[1]); border_bottom(h)
        elif kind == "h2": fill(d.add_heading(level=2), blk[1])
        elif kind == "sub":
            par = d.add_paragraph(); par.paragraph_format.space_before = Pt(8); par.paragraph_format.keep_with_next = True
            fill(par, f"**{blk[1]}**", 10.5, "1F2937")
        elif kind == "p": fill(d.add_paragraph(), blk[1])
        elif kind == "b1": fill(d.add_paragraph(style="List Bullet"), blk[1])
        elif kind == "b2": fill(d.add_paragraph(style="List Bullet 2"), blk[1])
        elif kind == "n1":
            num += 1
            par = d.add_paragraph(); pf = par.paragraph_format
            pf.left_indent = Twips(420); pf.first_line_indent = Twips(-420); pf.tab_stops.add_tab_stop(Twips(420))
            fill(par, f"{num}.\t" + blk[1])
        elif kind == "pagebreak":
            from docx.enum.text import WD_BREAK
            d.add_paragraph().add_run().add_break(WD_BREAK.PAGE)
        elif kind == "table":
            widths, rows = blk[1], blk[2]
            tbl = d.add_table(rows=0, cols=len(widths)); borders(tbl)
            for i, rdata in enumerate(rows):
                row = tbl.add_row(); cant_split(row)
                for c, txt in zip(row.cells, rdata):
                    lines = txt.split("\n"); par = c.paragraphs[0]
                    fill(par, f"**{lines[0]}**" if i == 0 else lines[0], 9)
                    for extra in lines[1:]: fill(c.add_paragraph(), extra, 9)
                    if i == 0: shade(c, SHADE)
            set_widths(tbl, widths)
    d.save(OUT + ".docx")

# ------------------------------------------------------------------ PDF
def build_pdf():
    from reportlab.lib.pagesizes import letter
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.lib.colors import HexColor
    from reportlab.lib.units import cm
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.lib.fonts import addMapping
    from reportlab.platypus import (BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table,
                                    TableStyle, PageBreak, ListFlowable, ListItem, KeepTogether, CondPageBreak)
    F = "/System/Library/Fonts/Supplemental/"
    for n, f in (("A", "Arial.ttf"), ("A-B", "Arial Bold.ttf"), ("A-I", "Arial Italic.ttf"),
                 ("A-BI", "Arial Bold Italic.ttf"), ("C", "Courier New.ttf"), ("U", "Arial Unicode.ttf")):
        pdfmetrics.registerFont(TTFont(n, F + f))
    addMapping("A", 0, 0, "A"); addMapping("A", 1, 0, "A-B"); addMapping("A", 0, 1, "A-I"); addMapping("A", 1, 1, "A-BI")

    def esc(s): return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    def mk(text):
        out = []
        for t, k in runs(text):
            t = esc(t).replace("→", '<font name="U">→</font>').replace("≥", '<font name="U">≥</font>').replace("−", '<font name="U">−</font>')
            if k == "b": out.append(f"<b>{t}</b>")
            elif k == "i": out.append(f"<i>{t}</i>")
            elif k == "c": out.append(f'<font name="C" size="8.6" color="#{CODE}">{t}</font>')
            else: out.append(t)
        return "".join(out)

    body = ParagraphStyle("body", fontName="A", fontSize=9.6, leading=13.4, textColor=HexColor("#" + BODY), spaceAfter=4)
    h1s = ParagraphStyle("h1", parent=body, fontName="A-B", fontSize=14, leading=18, textColor=HexColor("#" + ACCENT),
                         spaceBefore=12, spaceAfter=2)
    h2s = ParagraphStyle("h2", parent=body, fontName="A-B", fontSize=11.2, leading=15, textColor=HexColor("#1F2937"),
                         spaceBefore=9, spaceAfter=4)
    subs = ParagraphStyle("sub", parent=body, fontName="A-B", fontSize=10.2, textColor=HexColor("#1F2937"), spaceBefore=6)
    cell = ParagraphStyle("cell", parent=body, fontSize=8.6, leading=11.4, spaceAfter=0)

    from reportlab.platypus.flowables import HRFlowable
    def rule(): return HRFlowable(width="100%", thickness=1, color=HexColor("#" + ACCENT), spaceBefore=1, spaceAfter=0)

    b1s = ParagraphStyle("b1", parent=body, leftIndent=14, bulletIndent=3, bulletFontName="U", bulletFontSize=9.6,
                         bulletColor=HexColor("#" + ACCENT), spaceAfter=3)
    b2s = ParagraphStyle("b2", parent=b1s, leftIndent=30, bulletIndent=18)
    def bullets(items, level):
        st = b1s if level == 1 else b2s
        return [Paragraph(mk(t), st, bulletText="•" if level == 1 else "◦") for t in items]

    story = [Paragraph("Product Requirements Document (PRD)", ParagraphStyle("t", parent=body, fontName="A-B", fontSize=20,
                                                                          leading=25, textColor=HexColor("#111827"))),
             Paragraph(f"{APP} — Uninstall Apps  ·  Android V1", ParagraphStyle("s", parent=body, fontSize=12, leading=16,
                                                                               textColor=HexColor("#" + ACCENT), spaceAfter=8))]
    info = Table([[Paragraph(f"<b>{k}</b>", cell), Paragraph(esc(v), cell)] for k, v in INFO], colWidths=[4.2 * cm, None])
    info.setStyle(TableStyle([("GRID", (0, 0), (-1, -1), 0.5, HexColor("#" + BORDER)),
                              ("BACKGROUND", (0, 0), (0, -1), HexColor("#" + SHADE)), ("VALIGN", (0, 0), (-1, -1), "MIDDLE")]))
    story += [info, Spacer(1, 8), Paragraph(mk(INTRO_NOTE), ParagraphStyle("n", parent=body, fontSize=9.2, leading=12.8))]

    num, i = 0, 0
    while i < len(B):
        blk = B[i]; kind = blk[0]
        if kind in ("b1", "b2"):
            # group consecutive bullets; b2 nest under the preceding b1
            group = []
            while i < len(B) and B[i][0] in ("b1", "b2"): group.append(B[i]); i += 1
            flow, pending2 = [], []
            def flush2():
                if pending2: flow.extend(bullets(list(pending2), 2)); pending2.clear()
            for g in group:
                if g[0] == "b1": flush2(); flow.extend(bullets([g[1]], 1))
                else: pending2.append(g[1])
            flush2(); story += flow; continue
        if kind == "h1":
            story += [CondPageBreak(3 * cm), Paragraph(mk(blk[1]), h1s), rule(), Spacer(1, 6)]
        elif kind == "h2": story += [CondPageBreak(2.5 * cm), Paragraph(mk(blk[1]), h2s)]
        elif kind == "sub": story += [CondPageBreak(2.5 * cm), Paragraph(mk(blk[1]), subs)]
        elif kind == "p": story.append(Paragraph(mk(blk[1]), body))
        elif kind == "n1":
            num += 1
            story.append(Paragraph(mk(blk[1]), ParagraphStyle("n1", parent=body, leftIndent=18, bulletIndent=0),
                                   bulletText=f"{num}."))
        elif kind == "pagebreak": story.append(PageBreak())
        elif kind == "table":
            widths, rows = blk[1], blk[2]; total = sum(widths); avail = letter[0] - 4 * cm
            data = [[Paragraph((f"<b>{mk(c)}</b>" if r == 0 else mk(c)).replace("\n", "<br/>"), cell) for c in row]
                    for r, row in enumerate(rows)]
            t = Table(data, colWidths=[w / total * avail for w in widths], repeatRows=1, splitByRow=True)
            t.setStyle(TableStyle([("GRID", (0, 0), (-1, -1), 0.5, HexColor("#" + BORDER)),
                                   ("BACKGROUND", (0, 0), (-1, 0), HexColor("#" + SHADE)), ("VALIGN", (0, 0), (-1, -1), "TOP"),
                                   ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 4)]))
            story += [t, Spacer(1, 8)]
        i += 1

    def deco(c, doc):
        c.saveState(); c.setFont("A", 8.2); c.setFillColor(HexColor("#6B7280"))
        c.drawString(2 * cm, letter[1] - 1.3 * cm, HEADER)
        c.setStrokeColor(HexColor("#" + BORDER)); c.setLineWidth(0.5)
        c.line(2 * cm, letter[1] - 1.45 * cm, letter[0] - 2 * cm, letter[1] - 1.45 * cm)
        c.drawCentredString(letter[0] / 2, 1.1 * cm, f"Page {doc.page}")
        c.restoreState()

    doc = BaseDocTemplate(OUT + ".pdf", pagesize=letter, leftMargin=2 * cm, rightMargin=2 * cm, topMargin=2 * cm,
                          bottomMargin=1.8 * cm, title=f"{APP} PRD v1.0", author="Jedy Apps")
    doc.addPageTemplates([PageTemplate(frames=[Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="f")],
                                       onPage=deco)])
    doc.build(story)

build_docx(); build_pdf(); print("built")
