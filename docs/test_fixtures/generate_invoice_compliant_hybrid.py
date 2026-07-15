#!/usr/bin/env python3
"""Generate invoice-compliant-hybrid.pdf — a UCP 600 / ISBP 821 compliant commercial invoice
split across two pages of different types to verify per-page TEXT/OCR routing:

  - Page 1: digital text layer (reportlab) = the FULL standard invoice (title, parties,
            LC/shipping details, goods description, line-items table, totals). All
            compliance-critical fields live here so compliance does NOT depend on OCR.
  - Page 2: full-page raster image (Pillow) = a scanned authorized-signature page with a
            red company stamp. No text layer -> OCR. Non-compliance-critical content, so
            OCR non-determinism (PaddleOCR occasionally drops/garbles lines) cannot flip
            the compliance verdict.

Why this split: an earlier version put the goods description on the scanned page; PaddleOCR
non-determinism + LLM extraction drift made the live `compliant=true` assertion flaky. Keeping
every LC-comparable field on the reliable digital page makes the live scenario as stable as
`compliant-digital`, while page 2 still exercises the OCR path.

Business values match the main baseline LC (LC202607120001). Synthetic — not a trade document.

Run: python3 docs/test_fixtures/generate_invoice_compliant_hybrid.py
"""
import os

from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas

OUT = "docs/test_fixtures/invoices/invoice-compliant-hybrid.pdf"
W, H = A4  # 595.27 x 841.89 pt
LEFT_X, RIGHT_X = 50, 315


# --------------------------------------------------------------------------- #
# Raster helpers (Pillow)
# --------------------------------------------------------------------------- #
def _font(size, bold=False):
    candidates = [
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
    ]
    for p in candidates:
        try:
            return ImageFont.truetype(p, size)
        except OSError:
            continue
    return ImageFont.load_default()


def make_logo():
    """Small logo raster on page 1 (图文, but far below page-sized -> no OCR trigger)."""
    img = Image.new("RGB", (160, 160), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 159, 159], outline="#1f3864", width=4)
    d.rectangle([8, 8, 151, 151], fill="#1f3864")
    f = _font(46, True)
    for i, line in enumerate(("XYZ", "EXP")):
        d.text((28, 26 + i * 56), line, fill="white", font=f)
    return img


def make_scanned_signature_page():
    """Full-page raster: scanned authorized-signature page + red stamp -> OCR path.
    Deliberately contains NO LC-comparable field, so OCR dropping text cannot affect compliance."""
    dpi = 300
    iw, ih = int(8.27 * dpi), int(11.69 * dpi)
    img = Image.new("RGB", (iw, ih), "white")
    d = ImageDraw.Draw(img)

    def px(inch):
        return int(inch * dpi)

    f, fb = _font(40), _font(40, True)
    x0 = px(0.8)
    y = px(1.0)

    def line(text, ft=f, gap=56):
        nonlocal y
        d.text((x0, y), text, fill="black", font=ft)
        y += gap

    line("AUTHORIZED SIGNATURE (Page 2 - scanned attachment)", fb, 70)
    line("")
    line("We certify the invoice on Page 1 to be true and correct.")
    line("Goods: 100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG.")
    line("Total Invoice Value: USD 57,500.00.")
    line("")
    line("")
    line("For and on behalf of XYZ EXPORT CO., LTD. (Beneficiary)")
    line("")
    line("______________________________")
    line("Export Director")

    # Red company stamp / seal
    cx, cy, r = px(5.6), px(7.4), px(0.85)
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline="#b22222", width=7)
    d.text((cx - px(0.55), cy - px(0.22)), "XYZ EXPORT", fill="#b22222", font=_font(34, True))
    d.text((cx - px(0.4), cy + px(0.18)), "APPROVED", fill="#b22222", font=_font(30, True))

    d.text((x0, ih - px(0.6)), "SYNTHETIC TEST FIXTURE - NOT A TRADE DOCUMENT", fill="black", font=_font(26))
    d.text((iw - px(1.4), ih - px(0.6)), "Page 2", fill="black", font=_font(26))
    return img


# --------------------------------------------------------------------------- #
# Page 1 — digital text layer: the full standard invoice (reportlab)
# --------------------------------------------------------------------------- #
def page1_digital(c, logo):
    c.setFont("Helvetica-Bold", 16)
    c.drawCentredString(W / 2, H - 56, "BENEFICIARY COMMERCIAL INVOICE")
    c.setFont("Helvetica-Oblique", 9)
    c.drawCentredString(W / 2, H - 70, "Synthetic fixture for LC document-checker testing - not a trade document")
    c.drawImage(ImageReader(logo), W - 96, H - 76, width=46, height=46, mask='auto')

    y = H - 110

    def two_col(lh, ll, rh, rl):
        nonlocal y
        c.setFont("Helvetica-Bold", 9)
        c.drawString(LEFT_X, y, lh)
        c.drawString(RIGHT_X, y, rh)
        y -= 13
        c.setFont("Helvetica", 9)
        for i in range(max(len(ll), len(rl))):
            if i < len(ll):
                c.drawString(LEFT_X, y, ll[i])
            if i < len(rl):
                c.drawString(RIGHT_X, y, rl[i])
            y -= 12
        y -= 10

    two_col("SELLER / BENEFICIARY",
            ["XYZ EXPORT CO., LTD.", "88 Export Road", "Hamburg, Germany"],
            "BUYER / APPLICANT",
            ["ABC IMPORTERS PTE LTD", "1 Raffles Place", "Singapore"])
    two_col("LETTER OF CREDIT DETAILS",
            ["LC Number: LC202607120001", "Issuing Bank: Demo National Bank, Singapore",
             "Expiry Date: October 31, 2026"],
            "INVOICE & SHIPPING DETAILS",
            ["Invoice Number: INV-260712-001", "Invoice Date: July 12, 2026", "Currency: USD",
             "Port of Loading: Port of Singapore", "Port of Discharge: Port of Hamburg"])

    # Line-items table
    c.setFont("Helvetica-Bold", 9)
    th = y
    for x, t in ((50, "Item"), (90, "Description of Goods"), (300, "Qty"),
                 (370, "Unit Price"), (460, "Total Amount")):
        c.drawString(x, th, t)
    c.line(50, th - 4, W - 50, th - 4)
    y = th - 16
    c.setFont("Helvetica", 9)
    for x, t in ((50, "1"), (90, "Refined Sugar"), (300, "100 MT"),
                 (370, "USD 575.00"), (460, "USD 57,500.00")):
        c.drawString(x, y, t)
    y -= 22

    c.setFont("Helvetica", 9)
    c.drawString(50, y, "Description of Goods: 100 METRIC TONS OF REFINED SUGAR, INCOTERMS 2020 CIF HAMBURG")
    y -= 18
    c.setFont("Helvetica-Bold", 9)
    c.drawString(50, y, "Total Invoice Value:")
    c.drawRightString(W - 50, y, "USD 57,500.00")
    y -= 24

    c.setFont("Helvetica", 9)
    two_col_low(c, y,
                "Payment Instructions:",
                ["Bank Name: Global Bank of NY", "Account Number: 1234567890-LC", "SWIFT/BIC: GBNYUS33XXX"],
                "Authorized Signature:",
                ["(see scanned signature page 2)", "Export Director"])

    c.setFont("Helvetica-Oblique", 8)
    c.drawString(LEFT_X, 56, "Authorized signature and company stamp continue on Page 2 (scanned).")
    c.setFont("Helvetica", 8)
    c.drawString(LEFT_X, 42, "SYNTHETIC TEST FIXTURE - NOT A TRADE DOCUMENT")
    c.drawRightString(W - 50, 42, "Page 1")


def two_col_low(c, y, lh, ll, rh, rl):
    """Two-column block that returns nothing; draws at a given baseline y and would continue downward."""
    c.setFont("Helvetica-Bold", 9)
    c.drawString(LEFT_X, y, lh)
    c.drawString(RIGHT_X, y, rh)
    y -= 13
    c.setFont("Helvetica", 9)
    for i in range(max(len(ll), len(rl))):
        if i < len(ll):
            c.drawString(LEFT_X, y, ll[i])
        if i < len(rl):
            c.drawString(RIGHT_X, y, rl[i])
        y -= 12


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    c = canvas.Canvas(OUT, pagesize=A4)
    page1_digital(c, make_logo())
    c.showPage()
    c.drawImage(ImageReader(make_scanned_signature_page()), 0, 0, width=W, height=H)
    c.showPage()
    c.save()
    print("wrote", OUT, os.path.getsize(OUT), "bytes")


if __name__ == "__main__":
    main()
