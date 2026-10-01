#!/usr/bin/env python3
"""Generate DF IPTV launcher icons / TV banner from the site logo (assets/logo.png).

Produces:
  - legacy square launcher PNGs (all densities)
  - round launcher PNGs (all densities)
  - adaptive icon foreground (1024 canvas, logo centered in the 66% safe zone)
  - Android TV banner 320x180 with gradient wordmark
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGO = os.path.join(ROOT, "assets", "logo.png")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

BG = (11, 16, 32, 255)          # #0b1020 site background
VIOLET = (139, 92, 246, 255)    # #8b5cf6
CYAN = (34, 211, 238, 255)      # #22d3ee
MUTED = (147, 160, 200, 255)    # #93a0c8


def rounded_mask(size: int, radius_ratio: float) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    r = int(size * radius_ratio)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    return m


def circle_mask(size: int) -> Image.Image:
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def fit_font(draw: ImageDraw.ImageDraw, font_path: str, text: str,
             max_w: int, start: int, min_size: int = 10) -> ImageFont.FreeTypeFont:
    size = start
    while size > min_size:
        f = ImageFont.truetype(font_path, size)
        w = draw.textbbox((0, 0), text, font=f)[2]
        if w <= max_w:
            return f
        size -= 1
    return ImageFont.truetype(font_path, min_size)


def main() -> None:
    logo = Image.open(LOGO).convert("RGBA")

    # ---- legacy + round launcher icons ----
    for density, px in DENSITIES.items():
        out_dir = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        sq = logo.resize((px, px), Image.LANCZOS)
        sq.putalpha(rounded_mask(px, 0.21))
        sq.save(os.path.join(out_dir, "ic_launcher.png"))

        rnd = logo.resize((px, px), Image.LANCZOS)
        rnd.putalpha(circle_mask(px))
        rnd.save(os.path.join(out_dir, "ic_launcher_round.png"))

    # ---- adaptive foreground: 1024 canvas, logo in the 66% safe zone ----
    canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    inner = int(1024 * 0.66)
    small = logo.resize((inner, inner), Image.LANCZOS)
    small.putalpha(rounded_mask(inner, 0.21))
    off = (1024 - inner) // 2
    canvas.alpha_composite(small, (off, off))
    os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
    canvas.save(os.path.join(RES, "drawable-nodpi", "ic_launcher_foreground.png"))

    # ---- TV banner 320x180 ----
    banner = Image.new("RGBA", (320, 180), BG)
    glow = Image.new("RGBA", (320, 180), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([-80, -120, 160, 60], fill=(139, 92, 246, 60))
    gd.ellipse([180, -100, 420, 80], fill=(34, 211, 238, 44))
    glow = glow.filter(ImageFilter.GaussianBlur(38))
    banner.alpha_composite(glow)

    logo_b = logo.resize((92, 92), Image.LANCZOS)
    logo_b.putalpha(rounded_mask(92, 0.21))
    banner.alpha_composite(logo_b, (22, 44))

    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    draw = ImageDraw.Draw(banner)
    tx = 128
    max_w = 320 - tx - 12
    word = "DF IPTV"
    sub = "ТВ • фильмы • музыка"
    f_big = fit_font(draw, font_path, word, max_w, 34)
    f_small = fit_font(draw, font_path, sub, max_w, 15)

    # gradient wordmark
    alpha_layer = Image.new("L", (320, 180), 0)
    ImageDraw.Draw(alpha_layer).text((tx, 48), word, font=f_big, fill=255)
    gx = Image.new("L", (320, 1), 0)
    for x in range(320):
        t = max(0.0, min(1.0, (x - tx) / max(1.0, max_w * 0.9)))
        gx.putpixel((x, 0), int(255 * t))
    grad_row = gx.resize((320, 180))
    violet_layer = Image.new("RGBA", (320, 180), VIOLET)
    cyan_layer = Image.new("RGBA", (320, 180), CYAN)
    color_layer = Image.composite(cyan_layer, violet_layer, grad_row)
    wordmark = Image.new("RGBA", (320, 180), (0, 0, 0, 0))
    wordmark.paste(color_layer, (0, 0), alpha_layer)
    banner.alpha_composite(wordmark)

    sub_y = 48 + draw.textbbox((0, 0), word, font=f_big)[3] + 10
    ImageDraw.Draw(banner).text((tx, sub_y), sub, font=f_small, fill=MUTED)

    banner.save(os.path.join(RES, "drawable-nodpi", "banner.png"))
    print("icons generated OK")


if __name__ == "__main__":
    main()
