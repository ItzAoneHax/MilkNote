# 书写工具图标素材

书写页的钢笔、圆珠笔、荧光笔和橡皮使用用户已连接平板中小米笔记（`com.miui.notes`）安装包内的原始 `quick_menu_*` 分层资源，不再使用截图裁切或第三方近似图标。

小米笔记将笔杆与笔头分开绘制：应用保留笔杆原始光影，并用当前墨色对独立笔头着色，与原版实现方式一致。

| 工具 | 笔杆 | 笔头 |
| --- | --- | --- |
| 钢笔 | `ic_pen_fountain_body.png` | `ic_pen_fountain_head.png` |
| 圆珠笔 | `ic_pen_ballpoint_body.png` | `ic_pen_ballpoint_head.png` |
| 荧光笔 | `ic_pen_highlighter_body.png` | `ic_pen_highlighter_head.png` |
| 橡皮 | `ic_eraser_body.png` | — |

资源位于 `app/src/main/res/drawable-nodpi/`。原始资源名分别为 `quick_menu_brush_ink_*`、`quick_menu_brush_ballpoint_*`、`quick_menu_brush_markpen_*`、`quick_menu_tool_eraser_body`。
