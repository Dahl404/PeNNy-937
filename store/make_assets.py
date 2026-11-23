import subprocess, pathlib

OUT = pathlib.Path(__file__).parent

BG = '''<rect width="108" height="108" fill="#F7F1E3"/>
<path fill="#EDE5D2" d="M0,84c26,-10 44,4 62,0s30,-10 46,-6v30h-108z"/>'''

PEN = '''<path fill="#000000" fill-opacity="0.08" d="M47,44h24v34l-9,17a3.2,3.2 0 0,1 -6,0l-9,-17z"/>
<path fill="#1D1D1F" d="M40,34a12,12 0 0,1 12,-12h4a12,12 0 0,1 12,12v42h-28z"/>
<path fill="#1D1D1F" d="M40,74h28l-11,19a3.4,3.4 0 0,1 -6,0z"/>
<path fill="#F7F1E3" d="M51,88h6l-3,5.4z"/>
<path fill="#0A84FF" d="M40,63h28v9h-28z"/>
<path fill="#F7F1E3" d="M44,36a10,10 0 0,1 20,0v20a10,10 0 0,1 -20,0z"/>
<circle cx="48" cy="43" r="1.9" fill="#1D1D1F"/>
<circle cx="60" cy="43" r="1.9" fill="#1D1D1F"/>
<path d="M50,49q4,4.4 8,0" stroke="#1D1D1F" stroke-linecap="round" stroke-width="2" fill="none"/>
<rect x="44" y="34" width="3" height="26" fill="#FFFFFF" fill-opacity="0.22"/>'''

def pen(scale=1.0, cx=54, cy=56):
    return (f'<g transform="translate({cx},{cy}) scale({scale}) translate({-cx},{-cy})">'
            f'<g transform="rotate(-13 54 56)">{PEN}</g></g>')

# ---------- Play Store icon: 512x512, full bleed, no baked rounding ----------
icon = f'''<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">{BG}{pen(1.14)}</svg>'''
(OUT / "icon_512.svg").write_text(icon)

# ---------- Feature graphic: 1024x500 ----------
FONT = "Inter"
def frames(x0, y0, size, gap):
    """Four timeline frames: a ball travelling along an arc, ghosted behind."""
    xs = [0.26, 0.46, 0.66, 0.84]
    hs = [0.26, 0.56, 0.70, 0.40]
    out = []
    for i, (fx, h) in enumerate(zip(xs, hs)):
        x = x0 + i * (size + gap)
        bx, by = x + size * fx, y0 + size * (1 - h)
        out.append(f'<rect x="{x}" y="{y0}" width="{size}" height="{size}" rx="12" '
                   f'fill="#FFFFFF" stroke="#1D1D1F" stroke-opacity="0.16" stroke-width="2"/>')
        # dashed guide arc, same geometry in every frame where it is visible
        if i < 3:
            out.append(f'<path d="M{x + size*0.16:.1f},{y0 + size*0.86:.1f} Q{x + size*0.50:.1f},'
                       f'{y0 + size*0.16:.1f} {x + size*0.84:.1f},{y0 + size*0.72:.1f}" '
                       f'stroke="#1D1D1F" stroke-opacity="0.13" stroke-width="1.6" fill="none" '
                       f'stroke-dasharray="5 6"/>')
        if i > 0:
            gx, gh_ = xs[i-1], hs[i-1]
            out.append(f'<circle cx="{x + size*gx:.1f}" cy="{y0 + size*(1-gh_):.1f}" r="{size*0.072:.1f}" '
                       f'fill="#0A84FF" fill-opacity="0.26"/>')
        out.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="{size*0.072:.1f}" fill="#1D1D1F"/>')
        out.append(f'<circle cx="{bx:.1f}" cy="{by:.1f}" r="{size*0.108:.1f}" fill="none" '
                   f'stroke="#0A84FF" stroke-width="2.2" stroke-opacity="0.5"/>')
    return "".join(out)

dots = "".join(
    f'<circle cx="{x}" cy="{y}" r="1.6" fill="#1D1D1F" fill-opacity="0.10"/>'
    for y in range(28, 500, 34) for x in range(22, 1024, 34))

feature = f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs>
  <linearGradient id="hill" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#EDE5D2"/><stop offset="1" stop-color="#E6DCC6"/>
  </linearGradient>
</defs>
<rect width="1024" height="500" fill="#F7F1E3"/>
{dots}
<path d="M0,392c140,-46 250,26 366,-2 128,-31 208,-64 340,-40 122,22 212,24 318,-12v162h-1024z" fill="url(#hill)"/>
<g transform="translate(150,250) scale(2.55) translate(-54,-56)">{pen(1.0)}</g>
<text x="392" y="196" font-family="{FONT}" font-size="96" font-weight="700" fill="#1D1D1F"
      letter-spacing="-2">PeNNy-937</text>
<text x="396" y="252" font-family="{FONT}" font-size="31" font-weight="500" fill="#1D1D1F" fill-opacity="0.72">Frame-by-frame animation, on Android</text>
<text x="396" y="300" font-family="{FONT}" font-size="24" font-weight="400" fill="#1D1D1F" fill-opacity="0.55">Vector inks · layers · onion skin · MP4 and GIF</text>
{frames(398, 330, 124, 18)}
</svg>'''
(OUT / "feature_1024x500.svg").write_text(feature)

for name, w, h in [("icon_512", 512, 512), ("feature_1024x500", 1024, 500)]:
    subprocess.run(["rsvg-convert", "-w", str(w), "-h", str(h),
                    str(OUT / f"{name}.svg"), "-o", str(OUT / f"{name}.png")], check=True)
    print(name, "ok")
