import sys
from PIL import Image

def band_stats(path, y0, y1):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    region = im.crop((40, y0, w-40, y1))
    px = list(region.getdata())
    n = len(px)
    avg = sum((r+g+b) for r, g, b in px[::7]) / max(1, len(px[::7]))
    dark = sum(1 for r, g, b in px if (r+g+b) < 360)
    darkfrac = dark / n
    # brightness profile per row
    rows = []
    yr = region.height
    for dy in range(0, yr, 8):
        rowsum = 0; cnt = 0
        for dx in range(0, region.width, 4):
            r, g, b = region.getpixel((dx, dy))
            rowsum += r+g+b; cnt += 1
        rows.append((y0+dy, round(rowsum/cnt)))
    return avg, darkfrac, rows

for p in sys.argv[1:]:
    avg, dfrac, rows = band_stats(p, 1780, 1910)
    darkband = [r for r in rows if r[1] < 340]
    print(f'{p}: bandavg={avg:.1f} darkfrac={dfrac:.3f} darkrows={darkband[:6]}')

print()
print('win_snack.png (should have snackbar at +0.7s):')
avg, dfrac, rows = band_stats('win_snack.png', 1780, 1910)
print(f'  avg={avg:.1f} darkfrac={dfrac:.3f} minrow={min(r[1] for r in rows)}')
print('c1_snackbar.png (known-good, had snackbar):')
avg, dfrac, rows = band_stats('c1_snackbar.png', 1780, 1910)
print(f'  avg={avg:.1f} darkfrac={dfrac:.3f} minrow={min(r[1] for r in rows)}')
print('now.png (rest, no snackbar):')
avg, dfrac, rows = band_stats('now.png', 1780, 1910)
print(f'  avg={avg:.1f} darkfrac={dfrac:.3f} minrow={min(r[1] for r in rows)}')