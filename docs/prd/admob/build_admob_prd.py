# Renders admob_prd_content.py to AppCleaner_AdMob_PRD_v1_0.pdf (reportlab), in the look of the
# Jedy AdMob PRDs: blue headings and table headers, amber callouts, a cover page.
# Run from this folder: python3 build_admob_prd.py
import re
from admob_prd_content import B, INFO, HEADER, TITLE, SUBTITLE, VERSION

from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.colors import HexColor, white
from reportlab.lib.units import cm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.fonts import addMapping
from reportlab.platypus import (BaseDocTemplate, PageTemplate, Frame, Paragraph, Spacer, Table, TableStyle,
                                PageBreak, CondPageBreak, Preformatted, KeepTogether)

OUT = "../AppCleaner_AdMob_PRD_v1_0.pdf"
BLUE = "#1F4E99"; BODY = "#333333"; GREY = "#666666"; BORDER = "#C9D3E3"; ZEBRA = "#EEF3FA"
AMBER_BG = "#FFF6DB"; AMBER = "#E0A800"; CODE_BG = "#F4F6F9"

F = "/System/Library/Fonts/Supplemental/"
for n, f in (("A", "Arial.ttf"), ("A-B", "Arial Bold.ttf"), ("A-I", "Arial Italic.ttf"),
             ("A-BI", "Arial Bold Italic.ttf"), ("C", "Courier New.ttf"), ("U", "Arial Unicode.ttf")):
    pdfmetrics.registerFont(TTFont(n, F + f))
addMapping("A", 0, 0, "A"); addMapping("A", 1, 0, "A-B"); addMapping("A", 0, 1, "A-I"); addMapping("A", 1, 1, "A-BI")

TOKEN = re.compile(r"(\*\*.+?\*\*|`.+?`|\*[^*\s][^*]*?\*)")

def esc(s): return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

def mk(text):
    out = []
    for part in TOKEN.split(text):
        if not part: continue
        if part.startswith("**"): out.append(f"<b>{esc(part[2:-2])}</b>")
        elif part.startswith("`"): out.append(f'<font name="C" size="8.4">{esc(part[1:-1])}</font>')
        elif part.startswith("*") and len(part) > 2: out.append(f"<i>{esc(part[1:-1])}</i>")
        else: out.append(esc(part))
    return "".join(out).replace("—", '<font name="U">—</font>').replace("\n", "<br/>")

body = ParagraphStyle("body", fontName="A", fontSize=9.8, leading=13.6, textColor=HexColor(BODY), spaceAfter=5)
h1s = ParagraphStyle("h1", parent=body, fontName="A-B", fontSize=17, leading=22, textColor=HexColor(BLUE),
                     spaceBefore=10, spaceAfter=6)
h2s = ParagraphStyle("h2", parent=body, fontName="A-B", fontSize=12.5, leading=16, textColor=HexColor(BLUE),
                     spaceBefore=8, spaceAfter=5)
h3s = ParagraphStyle("h3", parent=body, fontName="A-B", fontSize=10.2, textColor=HexColor("#111111"), spaceBefore=6)
cell = ParagraphStyle("cell", parent=body, fontSize=8.6, leading=11.2, spaceAfter=0)
head = ParagraphStyle("head", parent=cell, fontName="A-B", textColor=white)
b1s = ParagraphStyle("b1", parent=body, leftIndent=14, bulletIndent=3, spaceAfter=3)
codes = ParagraphStyle("code", fontName="C", fontSize=7.9, leading=10, textColor=HexColor("#222222"))

def grid(rows, widths, avail, header=True, zebra=True):
    total = sum(widths)
    data = [[Paragraph(mk(c), head if (header and r == 0) else cell) for c in row] for r, row in enumerate(rows)]
    t = Table(data, colWidths=[w / total * avail for w in widths], repeatRows=1 if header else 0, splitByRow=True)
    st = [("GRID", (0, 0), (-1, -1), 0.5, HexColor(BORDER)), ("VALIGN", (0, 0), (-1, -1), "TOP"),
          ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
          ("LEFTPADDING", (0, 0), (-1, -1), 5), ("RIGHTPADDING", (0, 0), (-1, -1), 5)]
    if header: st.append(("BACKGROUND", (0, 0), (-1, 0), HexColor(BLUE)))
    if zebra:
        for r in range(1 if header else 0, len(rows)):
            if r % 2 == 0: st.append(("BACKGROUND", (0, r), (-1, r), HexColor(ZEBRA)))
    t.setStyle(TableStyle(st))
    return t

def box(flowable, bg, border, avail):
    t = Table([[flowable]], colWidths=[avail])
    t.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), HexColor(bg)), ("BOX", (0, 0), (-1, -1), 0.8, HexColor(border)),
                           ("TOPPADDING", (0, 0), (-1, -1), 7), ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
                           ("LEFTPADDING", (0, 0), (-1, -1), 8), ("RIGHTPADDING", (0, 0), (-1, -1), 8)]))
    return t

def build():
    doc = BaseDocTemplate(OUT, pagesize=A4, leftMargin=2 * cm, rightMargin=2 * cm, topMargin=2.1 * cm,
                          bottomMargin=1.9 * cm, title=f"App Cleaner AdMob PRD v{VERSION}", author="Jedy Apps")
    avail = doc.width

    def deco(c, d):
        c.saveState(); c.setFont("A", 8); c.setFillColor(HexColor(GREY))
        if d.page > 1:
            c.drawString(2 * cm, A4[1] - 1.25 * cm, HEADER)
            c.setStrokeColor(HexColor(BORDER)); c.setLineWidth(0.5)
            c.line(2 * cm, A4[1] - 1.4 * cm, A4[0] - 2 * cm, A4[1] - 1.4 * cm)
        c.drawString(2 * cm, 1.1 * cm, "jedyapps.com | Confidential")
        c.drawRightString(A4[0] - 2 * cm, 1.1 * cm, f"Page {d.page}")
        c.restoreState()

    story = [Spacer(1, 6.2 * cm),
             Paragraph("PRODUCT REQUIREMENTS DOCUMENT", ParagraphStyle("k", parent=body, fontName="A-B", fontSize=10.5,
                                                                      textColor=HexColor("#444444"), spaceAfter=10)),
             Paragraph(mk(TITLE), ParagraphStyle("t", parent=body, fontName="A-B", fontSize=27, leading=33,
                                                 textColor=HexColor(BLUE), spaceAfter=12))]
    story += [Paragraph(mk(s), ParagraphStyle("s", parent=body, fontSize=13, leading=19, textColor=HexColor(GREY)))
              for s in SUBTITLE]
    story += [Spacer(1, 1.2 * cm), grid([[k, v] for k, v in INFO], [30, 70], avail, header=False, zebra=False),
              PageBreak()]

    first_h1 = True
    for blk in B:
        kind = blk[0]
        if kind == "h1":
            story += [CondPageBreak(4 * cm), Paragraph(mk(blk[1]), h1s)]
        elif kind == "h2": story += [CondPageBreak(3 * cm), Paragraph(mk(blk[1]), h2s)]
        elif kind == "h3": story += [CondPageBreak(2.5 * cm), Paragraph(mk(blk[1]), h3s)]
        elif kind == "p": story.append(Paragraph(mk(blk[1]), body))
        elif kind == "b1": story.append(Paragraph(mk(blk[1]), b1s, bulletText="•"))
        elif kind == "code":
            story += [box(Preformatted(blk[1], codes), CODE_BG, BORDER, avail), Spacer(1, 6)]
        elif kind == "note":
            story += [Spacer(1, 2), box(Paragraph(mk(blk[1]), cell), AMBER_BG, AMBER, avail), Spacer(1, 8)]
        elif kind == "table":
            story += [grid(blk[2], blk[1], avail), Spacer(1, 8)]

    doc.addPageTemplates([PageTemplate(frames=[Frame(doc.leftMargin, doc.bottomMargin, doc.width, doc.height, id="f")],
                                       onPage=deco)])
    doc.build(story)

build(); print("built", OUT)
