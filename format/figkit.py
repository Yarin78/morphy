"""Drawing primitives shared by the figure generators of both formats.

Each format keeps its own make.py next to its figures; this module is what they
have in common, so that the specifications look alike:

    import figkit
    fig = figkit.Fig(860, 300, "what the figure shows, for a reader who cannot see it")
    fig.cell(...)
    fig.save("name.svg", out_dir)

Two constraints shape the output:

- **No CSS custom properties.** Not every SVG renderer supports them, and a
  var() that does not resolve paints the whole figure black, so each file
  writes its colours out twice: once for light and once inside a
  prefers-color-scheme media query. Renderers that ignore the query keep the
  light palette, which is legible on any background.
- **No web fonts.** An SVG loaded as an image may not fetch them, so the
  figures ask for the system sans and monospace faces.
"""
import html
import os

LIGHT = dict(surface="#ffffff", rule="#d3dad6", ink="#16201c", muted="#56625c", faint="#8a958f",
             wash="#eff2f0", accent="#16805a", accentw="#ddefe6", key="#b07614", keyw="#f6ead3",
             slot="#5b5fa8", slotw="#e4e5f3", rose="#a4405f", rosew="#f6e2e8")
DARK = dict(surface="#17201d", rule="#2c3833", ink="#e2e8e5", muted="#9daaa3", faint="#6f7c75",
            wash="#1d2824", accent="#2fa877", accentw="#16362a", key="#b98322", keyw="#3a2d14",
            slot="#8c90d8", slotw="#262844", rose="#d4738f", rosew="#3a1f28")


def _rules(p):
    """The colour half of the stylesheet, for one palette."""
    return f"""
  .bg {{ fill: {p['surface']}; stroke: {p['rule']}; }}
  text {{ fill: {p['ink']}; }}
  .mut {{ fill: {p['muted']}; }} .fnt {{ fill: {p['faint']}; }}
  .acc {{ fill: {p['accent']}; }} .keyc {{ fill: {p['key']}; }} .slotc {{ fill: {p['slot']}; }}
  .rosec {{ fill: {p['rose']}; }}
  .box {{ fill: {p['surface']}; stroke: {p['rule']}; }}
  .wash {{ fill: {p['wash']}; stroke: {p['rule']}; }}
  .accb {{ fill: {p['accentw']}; stroke: {p['accent']}; }}
  .keyb {{ fill: {p['keyw']}; stroke: {p['key']}; }}
  .slotb {{ fill: {p['slotw']}; stroke: {p['slot']}; }}
  .roseb {{ fill: {p['rosew']}; stroke: {p['rose']}; }}
  .ln, .lndash {{ stroke: {p['faint']}; }}
  .lnacc {{ stroke: {p['accent']}; }}
  .hfnt {{ fill: {p['faint']}; }} .hacc {{ fill: {p['accent']}; }}
"""


STYLE = f"""
  text {{ font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
          font-size: 12px; }}
  .m {{ font-family: ui-monospace, SFMono-Regular, "SF Mono", Menlo, Consolas, monospace; }}
  .sm {{ font-size: 11px; }} .xs {{ font-size: 10px; }} .lg {{ font-size: 13px; }}
  .b {{ font-weight: 600; }}
  .box, .wash, .accb, .keyb, .slotb, .roseb {{ stroke-width: 1.2; }}
  .ln, .lnacc, .lndash {{ fill: none; stroke-width: 1.5; }}
  .lndash {{ stroke-width: 1.2; stroke-dasharray: 4 4; }}
{_rules(LIGHT)}
  @media (prefers-color-scheme: dark) {{
{_rules(DARK)}
  }}
"""

# A style name gives a box class and the colour its text takes, so that a field
# and the note about it can be coloured alike.
FILLS = {"box": "box", "wash": "wash", "acc": "accb", "key": "keyb", "slot": "slotb",
         "rose": "roseb"}
INK = {"box": "", "wash": "mut", "acc": "acc", "key": "keyc", "slot": "slotc", "rose": "rosec"}


class Fig:
    """One figure. Coordinates are plain SVG units; the page is 860 wide, which
    is about as much as a markdown column shows without scaling."""

    def __init__(self, width, height, description):
        self.width, self.height = width, height
        self.description = description  # becomes the alt text a reader hears
        self.parts = []

    def add(self, element):
        self.parts.append("  " + element)
        return self

    def rect(self, x, y, w, h, cls="box", r=0):
        corner = f' rx="{r}"' if r else ""
        return self.add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" '
                        f'class="{cls}"{corner}/>')

    def text(self, x, y, s, cls="", anchor="start"):
        a = f' text-anchor="{anchor}"' if anchor != "start" else ""
        c = f' class="{cls}"' if cls else ""
        return self.add(f'<text x="{x}" y="{y}"{c}{a}>{html.escape(str(s), quote=False)}</text>')

    def path(self, d, cls="ln", head=None):
        m = f' marker-end="url(#{head})"' if head else ""
        return self.add(f'<path d="{d}" class="{cls}"{m}/>')

    def line(self, x1, y1, x2, y2, cls="ln", head=None):
        return self.path(f"M{x1} {y1} L{x2} {y2}", cls, head)

    def cell(self, x, y, w, h, label, style="box", sub=None, r=0):
        """A box with its label centred, and an optional second line under it."""
        self.rect(x, y, w, h, FILLS[style], r)
        ink = INK[style]
        if sub:
            self.text(x + w / 2, y + h / 2 - 2, label, f"sm b {ink}".strip(), "middle")
            self.text(x + w / 2, y + h / 2 + 12, sub, "xs mut", "middle")
        elif label:
            self.text(x + w / 2, y + h / 2 + 4, label, f"sm {ink}".strip(), "middle")
        return self

    def marker(self, name, cls="hfnt"):
        """An arrowhead, referred to by name from path(..., head=name)."""
        return self.add(
            f'<marker id="{name}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
            f'markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" '
            f'class="{cls}"/></marker>')

    out_dir = None  # set by the generator that owns the figures

    def save(self, name):
        body = "\n".join(self.parts)
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.width} {self.height}" '
               f'width="{self.width}" height="{self.height}" role="img" '
               f'aria-label="{html.escape(self.description, quote=True)}">\n'
               f'  <title>{html.escape(self.description, quote=False)}</title>\n'
               f'  <style>{STYLE}  </style>\n'
               f'  <rect class="bg" x="0.5" y="0.5" width="{self.width - 1}" '
               f'height="{self.height - 1}" rx="5"/>\n'
               f'{body}\n</svg>\n')
        with open(os.path.join(self.out_dir, name), "w", encoding="utf-8") as out:
            out.write(svg)
        print(f"wrote {name}")
