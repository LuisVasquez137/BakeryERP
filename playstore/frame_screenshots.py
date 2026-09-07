"""
Enmarca capturas reales de la app con un marco de marca BakeOS + título.

USO:
  1. Toma capturas en el emulador/dispositivo (relación 9:16, ej. 1080x1920).
  2. Guárdalas en  playstore/raw_screenshots/  numeradas y con el título como
     nombre de archivo, ej:
        01 - Recetas con porcentaje del panadero.png
        02 - Producción por lote y turno.png
  3. Ejecuta:  python playstore/frame_screenshots.py
  4. Resultado en  playstore/screenshots/  (1242x2208, listas para subir).
"""
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw_screenshots")
OUT = os.path.join(ROOT, "screenshots")
os.makedirs(RAW, exist_ok=True)
os.makedirs(OUT, exist_ok=True)

BLUE = (0, 91, 141)
BLUE_DARK = (0, 58, 92)
GOLD = (217, 164, 65)
WHITE = (255, 255, 255)

CANVAS = (1242, 2208)   # 9:16 Play Store phone
TITLE_H = 300
MARGIN = 70

def font(cands, size):
    for p in cands:
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()

BOLD = ["C:/Windows/Fonts/arialbd.ttf", "C:/Windows/Fonts/segoeuib.ttf"]

def vgrad(w, h, top, bottom):
    base = Image.new("RGB", (w, h), top)
    ov = Image.new("RGB", (w, h), bottom)
    m = Image.new("L", (1, h))
    for y in range(h):
        m.putpixel((0, y), int(255 * y / max(1, h - 1)))
    return Image.composite(ov, base, m.resize((w, h)))

def wrap(draw, text, fnt, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        t = (cur + " " + w).strip()
        if draw.textlength(t, font=fnt) <= max_w:
            cur = t
        else:
            lines.append(cur); cur = w
    if cur:
        lines.append(cur)
    return lines

def process(path, title):
    cw, ch = CANVAS
    canvas = vgrad(cw, ch, BLUE, BLUE_DARK).convert("RGBA")
    d = ImageDraw.Draw(canvas)

    # título
    ft = font(BOLD, 64)
    lines = wrap(d, title, ft, cw - 2 * MARGIN)
    y = 80
    for ln in lines:
        tw = d.textlength(ln, font=ft)
        d.text(((cw - tw) / 2, y), ln, font=ft, fill=WHITE)
        y += 78
    # acento dorado
    d.rectangle([(cw - 120) / 2, y + 14, (cw + 120) / 2, y + 22], fill=GOLD)

    # screenshot
    shot = Image.open(path).convert("RGBA")
    avail_h = ch - TITLE_H - MARGIN
    avail_w = cw - 2 * MARGIN
    r = min(avail_w / shot.width, avail_h / shot.height)
    shot = shot.resize((int(shot.width * r), int(shot.height * r)), Image.LANCZOS)
    sx = (cw - shot.width) // 2
    sy = TITLE_H + (avail_h - shot.height) // 2 - 10

    # sombra + borde redondeado simulado
    radius = 48
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, shot.width, shot.height], radius=radius, fill=255)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [sx + 8, sy + 14, sx + shot.width + 8, sy + shot.height + 14], radius=radius, fill=(0, 0, 0, 90))
    canvas = Image.alpha_composite(canvas, shadow)
    canvas.paste(shot, (sx, sy), mask)
    return canvas.convert("RGB")

def main():
    files = sorted(f for f in os.listdir(RAW) if f.lower().endswith((".png", ".jpg", ".jpeg")))
    if not files:
        print("No hay capturas en", RAW)
        return
    for i, f in enumerate(files, 1):
        title = os.path.splitext(f)[0]
        # quita prefijo "01 - "
        if " - " in title:
            title = title.split(" - ", 1)[1]
        out = process(os.path.join(RAW, f), title)
        name = f"screenshot_{i:02d}.png"
        out.save(os.path.join(OUT, name), "PNG")
        print("OK ->", name, "|", title)

if __name__ == "__main__":
    main()
