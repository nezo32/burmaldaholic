import sys
from PIL import Image, ImageDraw
w = int(sys.argv[1]); h = int(sys.argv[2]); src = sys.argv[3]; out = sys.argv[4]
im = Image.new('RGB', (w, h), (30, 94, 58))
d = ImageDraw.Draw(im)
tb = 22 if h < 330 else 26
if h < 255: tb = 18
dock = 44 if h < 330 else 56
if h < 255: dock = 36
d.rectangle([0, 0, w, tb - 1], fill=(24, 10, 40))
d.rectangle([0, h - dock, w, h], fill=(24, 10, 40))
cols = {'felt': None}
for line in open(src):
    n, x, y, bw, bh = line.split()
    x, y, bw, bh = map(int, (x, y, bw, bh))
    r = [x, y, x + bw - 1, y + bh - 1]
    if n == 'felt':
        continue
    if n.startswith('felt.'):
        d.rectangle(r, outline=(255, 214, 64) if n == 'felt.pass' else (232, 217, 160))
    elif n.startswith('glyph') or n.startswith('fglyph') or n.endswith('Word') or n == 'caption':
        d.rectangle(r, fill=(150, 140, 100))
    elif n.startswith('die'):
        d.rectangle(r, fill=(244, 236, 248))
    elif n == 'total':
        d.rectangle(r, fill=(24, 10, 40), outline=(176, 112, 16))
    elif n.startswith('puck'):
        d.ellipse(r, fill=(240, 240, 240) if n == 'puck8' else (30, 30, 30))
    elif n.startswith('stack'):
        d.ellipse([x, y + bh - bw, x + bw - 1, y + bh - 1], fill=(200, 40, 40))
    elif n == 'banner':
        d.rectangle(r, outline=(92, 232, 224))
im = im.resize((w * 4, h * 4), Image.NEAREST)
im.save(out)
