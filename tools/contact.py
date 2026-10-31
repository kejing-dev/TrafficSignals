"""Contact sheet: python3 -I tools/contact.py OUT.png COLS WIDTH img1 img2 ... (labels = file stems)."""
import sys, os
from PIL import Image, ImageDraw
out, cols, w = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
files = sys.argv[4:]
ims = [Image.open(f).convert("RGB") for f in files]
h = int(w * ims[0].height / ims[0].width)
rows = (len(ims) + cols - 1) // cols
sheet = Image.new("RGB", (cols * w, rows * (h + 18)), (30, 30, 34))
d = ImageDraw.Draw(sheet)
for i, (f, im) in enumerate(zip(files, ims)):
    x, y = (i % cols) * w, (i // cols) * (h + 18)
    sheet.paste(im.resize((w, h)), (x, y + 18))
    d.text((x + 4, y + 3), os.path.splitext(os.path.basename(f))[0], fill=(235, 235, 235))
sheet.save(out)
print(out, sheet.size)
