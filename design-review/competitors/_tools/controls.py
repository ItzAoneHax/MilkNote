"""从 ui/*.xml 生成 controls.csv。

用法: python controls.py <app_dir>
每行一个带 text 或 content-desc 的节点；坐标同时给出原始像素与归一化值。
page_name 取自 annotations.json 的 title（无标注时留空）。
"""
import csv
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def main(app_dir: Path):
    titles = {}
    ann = app_dir / "annotations.json"
    if ann.exists():
        import json
        spec = json.loads(ann.read_text(encoding="utf-8"))
        for shot in spec["shots"]:
            titles[shot["file"].rsplit(".", 1)[0]] = shot.get("title", "")
    rows = []
    for xml in sorted((app_dir / "ui").glob("*.xml")):
        page_id = xml.stem
        try:
            root = ET.parse(xml).getroot()
        except ET.ParseError:
            continue
        w = h = 0
        for node in root.iter("node"):
            b = node.get("bounds", "")
            m = BOUNDS.match(b)
            if not m:
                continue
            x1, y1, x2, y2 = map(int, m.groups())
            w, h = max(w, x2), max(h, y2)
            text = (node.get("text") or "").replace("\n", " ").strip()
            desc = (node.get("content-desc") or "").replace("\n", " ").strip()
            if not (text or desc):
                continue
            if x2 <= x1 and y2 <= y1:
                continue
            norm = f"{x1/2560:.4f},{y1/1600:.4f},{x2/2560:.4f},{y2/1600:.4f}"
            rows.append([
                page_id, titles.get(page_id, ""), node.get("package", ""),
                node.get("resource-id", "").split("/")[-1], node.get("class", "").split(".")[-1],
                text[:80], desc[:80], f"[{x1},{y1}][{x2},{y2}]", norm,
                f"screenshots/{page_id}.png", f"ui/{page_id}.xml",
                "ui_observed" if (app_dir / "screenshots" / f"{page_id}.png").exists() else "ui_only",
            ])
    out = app_dir / "controls.csv"
    with out.open("w", newline="", encoding="utf-8") as f:
        wr = csv.writer(f)
        wr.writerow(["page_id", "page_name", "package", "control_id", "control_type", "text",
                     "content_desc", "bounds_px", "normalized_bounds", "screenshot", "ui_xml", "status"])
        wr.writerows(rows)
    print(f"{out}: {len(rows)} rows")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
