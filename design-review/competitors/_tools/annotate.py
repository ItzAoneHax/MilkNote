"""给截图加编号标注。

用法: python annotate.py <app_dir>
读取 <app_dir>/annotations.json:
{
  "app": "obsidian",
  "source_size": [2560, 1600],
  "shots": [
    {"file": "P001_x.png", "title": "...", "callouts": [
      {"n": 1, "box": [x1, y1, x2, y2], "label": "..."}]}
  ]
}
输出 <app_dir>/annotated/<同名>.png: 左侧缩放后的截图 + 编号框, 右侧图例面板。
box 坐标使用原始截图像素。
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "C:/Windows/Fonts/msyh.ttc"
FONT_BOLD = "C:/Windows/Fonts/msyhbd.ttc"
OUT_W = 1760            # 截图缩放后的宽度
PANEL_W = 700           # 右侧图例宽度
ACCENT = (220, 38, 38)
BG = (24, 24, 27)
FG = (240, 240, 245)
MUTED = (160, 160, 170)


def load_font(size, bold=False):
    try:
        return ImageFont.truetype(FONT_BOLD if bold else FONT_PATH, size)
    except OSError:
        return ImageFont.load_default()


def wrap(draw, text, font, max_w):
    lines, cur = [], ""
    for ch in text:
        if draw.textlength(cur + ch, font=font) <= max_w:
            cur += ch
        else:
            lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines


def annotate(app_dir: Path):
    spec = json.loads((app_dir / "annotations.json").read_text(encoding="utf-8"))
    src_w, src_h = spec["source_size"]
    out_h = round(OUT_W * src_h / src_w)
    (app_dir / "annotated").mkdir(exist_ok=True)
    f_title = load_font(30, bold=True)
    f_label = load_font(21)
    f_num = load_font(21, bold=True)
    f_num_big = load_font(34, bold=True)
    f_foot = load_font(18)

    for shot in spec["shots"]:
        src = app_dir / "screenshots" / shot["file"]
        if not src.exists():
            print(f"skip (missing): {src}")
            continue
        img = Image.open(src).convert("RGB")
        if img.size != (src_w, src_h):
            print(f"warning: {shot['file']} is {img.size}, spec says {(src_w, src_h)}")
            img = img.resize((OUT_W, out_h), Image.LANCZOS)
        else:
            img = img.resize((OUT_W, out_h), Image.LANCZOS)
        canvas = Image.new("RGB", (OUT_W + PANEL_W, out_h), BG)
        canvas.paste(img, (0, 0))
        draw = ImageDraw.Draw(canvas)
        sx, sy = OUT_W / src_w, out_h / src_h
        callouts = shot.get("callouts", [])

        # 截图上的编号框
        for c in callouts:
            x1, y1, x2, y2 = c["box"]
            x1, y1, x2, y2 = x1 * sx, y1 * sy, x2 * sx, y2 * sy
            x1, y1 = max(0, x1 - 6), max(0, y1 - 6)
            x2, y2 = min(OUT_W, x2 + 6), min(out_h, y2 + 6)
            for i, w in enumerate((6, 2)):
                draw.rectangle([x1 - i * 2, y1 - i * 2, x2 + i * 2, y2 + i * 2], outline=(255, 255, 255) if i else ACCENT, width=w)
            r = 22
            cx, cy = x1, y1
            draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=ACCENT, outline=(255, 255, 255), width=3)
            num = str(c["n"])
            tw = draw.textlength(num, font=f_num)
            draw.text((cx - tw / 2, cy - 15), num, font=f_num, fill=(255, 255, 255))

        # 右侧图例（超过 20 项时分两栏）
        callouts = shot.get("callouts", [])
        two_col = len(callouts) > 20
        if two_col:
            canvas = Image.new("RGB", (OUT_W + PANEL_W * 2, out_h), BG)
            canvas.paste(img, (0, 0))
            draw = ImageDraw.Draw(canvas)
        px = OUT_W + 32
        y = 36
        draw.text((px, y), shot["file"], font=f_foot, fill=MUTED)
        y += 34
        title = shot.get("title", "")
        title_width = (PANEL_W * 2 - 64) if two_col else (PANEL_W - 64)
        for line in wrap(draw, title, f_title, title_width):
            draw.text((px, y), line, font=f_title, fill=FG)
            y += 40
        y += 18
        draw.line([px, y, OUT_W + title_width + 32, y], fill=(70, 70, 80), width=2)
        y += 22
        legend_top = y
        col_items = (len(callouts) + 1) // 2 if two_col else len(callouts)
        col_x = [px]
        if two_col:
            col_x.append(px + PANEL_W)
        col = 0
        count_in_col = 0
        y_cols = [y, y]
        y = y_cols[0]
        for c in callouts:
            if two_col and count_in_col >= col_items and col == 0:
                col = 1
                count_in_col = 0
            cx = col_x[col]
            y = y_cols[col]
            draw.ellipse([cx, y + 2, cx + 32, y + 34], fill=ACCENT)
            num = str(c["n"])
            tw = draw.textlength(num, font=f_num)
            draw.text((cx + 16 - tw / 2, y + 5), num, font=f_num, fill=(255, 255, 255))
            lines = wrap(draw, c["label"], f_label, PANEL_W - 64 - 48)
            ly = y
            for k, line in enumerate(lines):
                draw.text((cx + 46, ly), line, font=f_label, fill=FG)
                ly += 27
            y_cols[col] = max(ly, y + 36) + 6
            count_in_col += 1
        y = max(y_cols)
        if y > out_h - 60:
            draw.text((col_x[-1], y), "...", font=f_label, fill=MUTED)
        foot = f"{spec['app']} · {shot['file']} · 标注基于 {src_w}x{src_h} 原始截图"
        draw.text((px, out_h - 40), foot, font=f_foot, fill=MUTED)

        out = app_dir / "annotated" / shot["file"]
        canvas.save(out, optimize=True)
        print(f"annotated: {out.name} ({len(callouts)} callouts{', 2-col' if two_col else ''})")


if __name__ == "__main__":
    annotate(Path(sys.argv[1]))
