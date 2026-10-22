"""Builds small test theses: a DOCX that fails the format rules, one that passes, and a PDF."""
import sys
import zipfile

W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"'

CT = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

DOC_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""


def styles(font, size_half, line):
    return f"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles {W}>
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="{font}" w:hAnsi="{font}"/><w:sz w:val="{size_half}"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:line="{line}" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:rPr><w:b/><w:sz w:val="28"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TOC1"><w:name w:val="toc 1"/><w:basedOn w:val="Normal"/></w:style>
</w:styles>"""


def p(text, style=None):
    ppr = f'<w:pPr><w:pStyle w:val="{style}"/></w:pPr>' if style else ''
    return f'<w:p>{ppr}<w:r><w:t xml:space="preserve">{text}</w:t></w:r></w:p>'


def sect(left, fmt, last=False):
    num = f'<w:pgNumType w:fmt="{fmt}" w:start="1"/>' if fmt else ''
    body = (f'<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>'
            f'<w:pgMar w:top="1418" w:right="1418" w:bottom="1418" w:left="{left}" w:header="708" w:footer="708" w:gutter="0"/>'
            f'{num}</w:sectPr>')
    return body if last else f'<w:p><w:pPr>{body}</w:pPr></w:p>'


BODY = ("This chapter sets out the research problem, the questions the study answers and the methods used to answer them. "
        "It explains why the topic matters for smallholder farmers and how the evidence was collected over two seasons.")


def document(good):
    left = 2155 if good else 1418  # 3.8 cm vs 2.5 cm
    parts = [p("Sensor networks for rain-fed agriculture", "Heading1" if not good else None),
             p("A thesis submitted to the Graduate School"),
             p("Declaration", "Heading1"), p(BODY),
             p("Abstract", "Heading1"), p(" ".join(["word"] * (180 if good else 340))),
             p("Acknowledgements", "Heading1"), p(BODY)]
    if good:
        parts.append(p("Contents", "Heading1"))
        parts.append('<w:p><w:pPr><w:pStyle w:val="TOC1"/></w:pPr><w:r><w:fldChar w:fldCharType="begin"/></w:r>'
                     '<w:r><w:instrText xml:space="preserve"> TOC \\o "1-3" \\h </w:instrText></w:r>'
                     '<w:r><w:fldChar w:fldCharType="separate"/></w:r><w:r><w:t>Chapter 1 Introduction 1</w:t></w:r></w:p>')
        parts.append('<w:p><w:pPr><w:pStyle w:val="TOC1"/></w:pPr><w:r><w:t>References 9</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>')
        parts.append(sect(left, "lowerRoman"))
    parts += [p("Chapter 1 Introduction", "Heading1"), p(BODY), p(BODY), p(BODY),
              p("Chapter 2 Methods", "Heading1"), p(BODY), p(BODY),
              p("References", "Heading1"), p("Owusu, Y. (2024). Irrigation in northern Ghana. Journal of Agriculture, 12(3), 44-60."),
              p("Appendices", "Heading1"), p(BODY)]
    parts.append(sect(left, None if good else None, last=True))
    return f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {W}><w:body>{"".join(parts)}</w:body></w:document>'


def write_docx(path, good):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", CT)
        z.writestr("_rels/.rels", RELS)
        z.writestr("word/_rels/document.xml.rels", DOC_RELS)
        z.writestr("word/document.xml", document(good))
        z.writestr("word/styles.xml", styles("Times New Roman" if good else "Arial", 24 if good else 22, 360 if good else 240))


def write_pdf(path, text="Evidence"):
    content = f"BT /F1 18 Tf 120 700 Td ({text}) Tj ET".encode()
    objs = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
            b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"\nendstream",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for i, o in enumerate(objs, 1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + o + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objs) + 1}\n0000000000 65535 f \n".encode()
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objs) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    open(path, "wb").write(bytes(out))


if __name__ == "__main__":
    d = sys.argv[1]
    write_docx(f"{d}/thesis-bad.docx", False)
    write_docx(f"{d}/thesis-good.docx", True)
    write_pdf(f"{d}/evidence.pdf", "Signed panel sheet")
    write_pdf(f"{d}/paper.pdf", "Full text of the paper")
    write_pdf(f"{d}/report.pdf", "Provider screening report")
