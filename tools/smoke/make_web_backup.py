#!/usr/bin/env python3
"""Builds a web-shaped backup v2 (exactly what app.js exportBackup() writes) for the emulator smoke test.

Contains a real 2-page PDF, a PNG and a WAV so the native PDF viewer, image viewer and Media3
player can be exercised after importing. Nothing here is user data; it's generated at test time.
"""
import base64
import json
import math
import struct
import sys
import zlib


def pdf_bytes():
    objs = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>"]
    for i, text in ((3, b"Ubad smoke page 1"), (5, b"Ubad smoke page 2")):
        stream = b"BT /F1 36 Tf 72 700 Td (" + text + b") Tj ET"
        objs.append(b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 7 0 R >> >> /Contents %d 0 R >>" % (i + 1))
        objs.append(b"<< /Length %d >>\nstream\n" % len(stream) + stream + b"\nendstream")
    objs.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    out, offsets = bytearray(b"%PDF-1.4\n"), []
    for n, body in enumerate(objs, 1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % n + body + b"\nendobj\n"
    xref = len(out)
    out += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    out += b"".join(b"%010d 00000 n \n" % o for o in offsets)
    out += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    return bytes(out)


def png_bytes(w=64, h=64):
    raw = b"".join(b"\x00" + b"".join(bytes((x * 4 % 256, y * 4 % 256, 180)) for x in range(w)) for y in range(h))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


def wav_bytes(seconds=2, sr=8000):
    frames = b"".join(struct.pack("<h", int(8000 * math.sin(2 * math.pi * 440 * i / sr))) for i in range(sr * seconds))
    return b"RIFF" + struct.pack("<I", 36 + len(frames)) + b"WAVEfmt " + struct.pack("<IHHIIHH", 16, 1, 1, sr, sr * 2, 2, 16) + \
        b"data" + struct.pack("<I", len(frames)) + frames


def data_url(mime, b):
    return f"data:{mime};base64,{base64.b64encode(b).decode()}"


def build():
    now = 1760000000000
    return {
        "app": "ubad-academy-hub", "version": 2, "exportedAt": "2026-09-26T08:00:00.000Z",
        "sections": {k: True for k in ("user", "courses", "notes", "calendar", "study", "islam", "summaries", "forms", "background")},
        "data": {
            "user": {"name": "Smoke Tester"},
            "courses": [{
                "id": "smk-c1", "name": "Smoke Physics", "code": "PHY101", "instructor": "Dr. Test", "credits": 3,
                "semester": "Fall", "createdAt": now,
                "units": [{"id": "smk-u1", "title": "Smoke Unit", "contents": [
                    {"id": "smk-x1", "type": "pdf", "title": "Smoke PDF", "text": "", "assetId": "smk-a1", "assets": [],
                     "name": "smoke.pdf", "mime": "application/pdf", "source": "", "url": "", "done": False, "createdAt": now},
                    {"id": "smk-x2", "type": "image", "title": "Smoke Image", "text": "", "assetId": "",
                     "assets": [{"id": "smk-a2", "name": "smoke.png", "mime": "image/png"}], "name": "", "mime": "",
                     "source": "", "url": "", "done": True, "createdAt": now + 1},
                    {"id": "smk-x3", "type": "audio", "title": "Smoke Audio", "text": "", "assetId": "smk-a3", "assets": [],
                     "name": "smoke.wav", "mime": "audio/wav", "source": "local", "url": "", "done": False, "createdAt": now + 2},
                    {"id": "smk-x4", "type": "text", "title": "Smoke Text", "text": "سطر عربي\nEnglish line", "createdAt": now + 3},
                ]}],
            }],
            "courseAssets": [
                {"id": "smk-a1", "name": "", "type": "application/pdf", "data": data_url("application/pdf", pdf_bytes())},
                {"id": "smk-a2", "name": "", "type": "image/png", "data": data_url("image/png", png_bytes())},
                {"id": "smk-a3", "name": "", "type": "audio/wav", "data": data_url("audio/wav", wav_bytes())},
            ],
            "notes": [{"id": "smk-n1", "title": "Smoke Note", "body": "ملاحظة", "tags": ["smoke"], "pin": True,
                       "createdAt": now, "updatedAt": now, "images": [{"name": "n.png", "type": "image/png", "data": data_url("image/png", png_bytes(16, 16))}],
                       "audio": []}],
            "events": [{"id": "smk-e1", "title": "Smoke Exam", "desc": "", "date": "2026-10-01", "time": "09:30", "createdAt": now}],
            "decks": [{"id": "smk-d1", "title": "Smoke Deck", "createdAt": now, "cards": [{"id": "smk-k1", "front": "F1", "back": "B1"},
                                                                                         {"id": "smk-k2", "front": "F2", "back": "B2"}]}],
            "quizzes": [{"id": "smk-q1", "title": "Smoke Quiz", "createdAt": now,
                         "questions": [{"q": "2+2?", "options": ["3", "4", "", ""], "correct": 1}]}],
            "schedule": [{"id": "smk-s1", "title": "Smoke Session", "days": [0, 1, 2, 3, 4, 5, 6], "start": "08:00", "end": "09:00",
                          "doneDates": {}, "createdAt": now}],
            "islam": {"day": "2026-09-26", "prayers": {"fajr": 1}, "rawatib": {}, "fasts": [], "hist": {},
                      "tasbih": {"mode": "sub", "count": 0, "total": 5, "target": 33, "adhkar": []}},
            "summaries": [{"id": "smk-m1", "title": "Smoke Summary", "url": "https://example.com/summary", "createdAt": now, "lastOpened": 0, "pinned": False}],
            "forms": [{"id": "smk-f1", "title": "Smoke Form", "url": "https://forms.gle/example", "createdAt": now, "lastOpened": 0, "pinned": True}],
            "backgrounds": {},
        },
    }


if __name__ == "__main__":
    json.dump(build(), open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False)
