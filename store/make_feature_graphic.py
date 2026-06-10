"""
Generate the Google Play *feature graphic* (1024 x 500) for Blood Pressure Tracking.

Branded-banner layout:
  - app-themed mint gradient background
  - the hero phone screenshot fitted (undistorted) on the right, rounded + soft shadow
  - app name + fitness-positioned tagline + feature lines on the left

Run:  python store/make_feature_graphic.py
Out:  store/feature-graphic.png   (1024x500, PNG)
"""
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import os

HERE = os.path.dirname(os.path.abspath(__file__))
SHOT = os.path.join(HERE, "screenshots",
                    "Screenshot_2026-06-09-13-40-55-904_com.zandaulion.bpdigitized.jpg")
OUT = os.path.join(HERE, "feature-graphic.png")

W, H = 1024, 500

# ---- palette (sampled from the app screenshot) -------------------------------
BG_TOP  = (245, 251, 249)   # #F5FBF9 light mint
BG_BOT  = (224, 238, 232)   # slightly deeper mint for a subtle gradient
TEAL    = (106, 171, 165)   # #6AABA5 brand teal (the FAB / chips)
TEAL_DK = (60, 138, 130)    # deeper teal for the eyebrow text
TITLE   = (23, 42, 37)      # near-black slate-teal
BODY    = (58, 78, 72)      # muted slate for body lines

FONTS = r"C:\Windows\Fonts"
def font(name, size):
    return ImageFont.truetype(os.path.join(FONTS, name), size)

f_eyebrow = font("segoeuib.ttf", 21)
f_title   = font("segoeuib.ttf", 60)
f_tag     = font("segoeuisl.ttf", 30)   # semilight
f_feat    = font("segoeui.ttf", 22)
f_trust   = font("segoeui.ttf", 21)

# ---- background: vertical mint gradient --------------------------------------
bg = Image.new("RGB", (W, H), BG_TOP)
px = bg.load()
for y in range(H):
    t = y / (H - 1)
    r = round(BG_TOP[0] + (BG_BOT[0] - BG_TOP[0]) * t)
    g = round(BG_TOP[1] + (BG_BOT[1] - BG_TOP[1]) * t)
    b = round(BG_TOP[2] + (BG_BOT[2] - BG_TOP[2]) * t)
    for x in range(W):
        px[x, y] = (r, g, b)
img = bg.convert("RGBA")

# ---- subtle teal depth blob behind the phone ---------------------------------
blob = Image.new("RGBA", (W, H), (0, 0, 0, 0))
bd = ImageDraw.Draw(blob)
bd.ellipse([700, -120, 1180, 360], fill=TEAL + (38,))
blob = blob.filter(ImageFilter.GaussianBlur(70))
img = Image.alpha_composite(img, blob)

# ---- phone screenshot: fit height, round corners, soft shadow ----------------
shot = Image.open(SHOT).convert("RGBA")
ph_h = 470
ph_w = round(ph_h * shot.width / shot.height)
shot = shot.resize((ph_w, ph_h), Image.LANCZOS)

radius = 30
mask = Image.new("L", (ph_w, ph_h), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, ph_w, ph_h], radius=radius, fill=255)
shot.putalpha(mask)

# position: vertically centered, anchored toward the right
ph_x = W - ph_w - 70
ph_y = (H - ph_h) // 2

# shadow
shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
sd = ImageDraw.Draw(shadow)
sd.rounded_rectangle([ph_x, ph_y + 12, ph_x + ph_w, ph_y + ph_h + 12],
                     radius=radius, fill=(18, 40, 35, 105))
shadow = shadow.filter(ImageFilter.GaussianBlur(22))
img = Image.alpha_composite(img, shadow)
img.alpha_composite(shot, (ph_x, ph_y))

# ---- text column (left) ------------------------------------------------------
draw = ImageDraw.Draw(img)
LX = 70  # left margin for the text block

def tracked(xy, text, fnt, fill, tracking):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=fnt, fill=fill)
        x += draw.textlength(ch, font=fnt) + tracking
    return x

# brand tab
draw.rounded_rectangle([LX, 74, LX + 46, 80], radius=3, fill=TEAL)

# eyebrow
tracked((LX, 96), "FITNESS  &  WELLNESS", f_eyebrow, TEAL_DK, 1.5)

# title (two lines)
draw.text((LX, 134), "Blood Pressure", font=f_title, fill=TITLE)
draw.text((LX, 200), "Tracking",       font=f_title, fill=TITLE)

# tagline
draw.text((LX, 286), "Part of your fitness routine.", font=f_tag, fill=BODY)

# feature line with teal dots
def dot(x, cy):
    draw.ellipse([x, cy - 4, x + 8, cy + 4], fill=TEAL)

fy = 356
x = LX
dot(x, fy + 12); x += 20
x = draw.textlength("Log in seconds", font=f_feat) + x
draw.text((LX + 20, fy), "Log in seconds", font=f_feat, fill=BODY)
x += 18; dot(x, fy + 12); x += 20
draw.text((x, fy), "Spot your trends", font=f_feat, fill=BODY)
x += draw.textlength("Spot your trends", font=f_feat) + 18
dot(x, fy + 12); x += 20
draw.text((x, fy), "Export a PDF", font=f_feat, fill=BODY)

# trust line
draw.text((LX, 402), "Private & on-device  ·  No account needed",
          font=f_trust, fill=(110, 128, 122))

# ---- save --------------------------------------------------------------------
img.convert("RGB").save(OUT, "PNG")
print("wrote", OUT)
print("size", Image.open(OUT).size)
print("bytes", os.path.getsize(OUT))
