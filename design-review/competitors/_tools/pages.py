"""从 annotations.json 生成 PAGES.md（逐页说明，嵌入标注图与图例）。

用法: python pages.py <app_dir>
"""
import json
import sys
from pathlib import Path


def main(app_dir: Path):
    spec = json.loads((app_dir / "annotations.json").read_text(encoding="utf-8"))
    lines = [f"# {spec['app']} · 逐页截图与功能标注", ""]
    lines.append("每页先放带编号的标注图（`annotated/`），再列出图例。原始截图在 `screenshots/`，"
                 "UI Automator 层级在 `ui/`，控件坐标表在 `controls.csv`。坐标均为 2560×1600 原始像素。")
    lines.append("")
    for i, shot in enumerate(spec["shots"], 1):
        stem = shot["file"].rsplit(".", 1)[0]
        lines.append(f"## {i}. {shot['title']}")
        lines.append("")
        lines.append(f"![{stem}](annotated/{shot['file']})")
        lines.append("")
        lines.append(f"- 原始截图：`screenshots/{shot['file']}` · UI 层级：`ui/{stem}.xml`")
        lines.append("")
        for c in shot.get("callouts", []):
            x1, y1, x2, y2 = c["box"]
            lines.append(f"{c['n']}. {c['label']}  `[x1,y1][x2,y2] = [{x1},{y1}][{x2},{y2}]`")
        lines.append("")
    (app_dir / "PAGES.md").write_text("\n".join(lines), encoding="utf-8")
    print(f"{app_dir / 'PAGES.md'}: {len(spec['shots'])} pages")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
