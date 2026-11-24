"""
点阵字库生成工具：从「缝合像素字体 Fusion Pixel」BDF 文件生成 pixel-font.json。

字体：TakWolf/fusion-pixel-font（SIL OFL 1.1 开源协议）
下载：https://github.com/TakWolf/fusion-pixel-font/releases
      fusion-pixel-font-12px-proportional-bdf-*.zip 中的
      fusion-pixel-12px-proportional-zh_hans.bdf

用法：
    python tools/fontgen_bdf.py <bdf路径> [输出json路径]

输出 JSON：{ "size": 16, "glyphs": { "室": ["....", ...16 行], "A": [...7 宽 16 行] } }
- 字形直接取自点阵字体原厂位图（基线对齐放进 16 行网格），栅格天然干净，放大缩小不糊；
- 同时写入后端 resources/font 与前端 src/assets 两份（内容相同，前后端栅格一致）
- 字库缺失的字符不写入，前端回退浏览器栅格。
"""

import json
import re
import sys
from pathlib import Path

CELL_H = 16

ROOT = Path(__file__).resolve().parent.parent
FONT_JAVA = ROOT / "tools" / "FontGen.java"


def load_charset() -> str:
    text = FONT_JAVA.read_text(encoding="utf-8")
    match = re.search(r'String charset = """(.*?)"""', text, re.S)
    extra = match.group(1) if match else ""
    # ASCII 可见字符
    ascii_chars = [chr(c) for c in range(0x20, 0x7F)]
    # GB2312 全部汉字（一级 + 二级）
    gb_chars = []
    for hi in range(0xB0, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                ch = bytes([hi, lo]).decode("gb2312")
            except UnicodeDecodeError:
                continue
            gb_chars.append(ch)
    return "".join(dict.fromkeys(ascii_chars + list(extra) + gb_chars))


def parse_bdf(path: Path):
    ascent = 13
    glyphs = {}
    char = None
    enc = dwidth = bw = bh = bx = by = 0
    rows: list[int] = []
    in_bitmap = False

    def flush():
        nonlocal char, rows
        if char is None:
            return
        top = ascent - (by + bh)
        grid = ["."] * CELL_H
        width = max(bw, 1)
        total_bits = ((bw + 7) // 8) * 8  # BDF 位图按字节对齐，从高位取
        for i, bits in enumerate(rows):
            y = top + i
            if 0 <= y < CELL_H:
                grid[y] = "".join(
                    "#" if bits & (1 << (total_bits - 1 - x)) else "." for x in range(bw)
                )
        for y in range(CELL_H):
            if len(grid[y]) < width:
                grid[y] = grid[y].ljust(width, ".")
        glyphs[char] = grid
        char = None
        rows = []

    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith("FONT_ASCENT"):
            ascent = int(line.split()[1])
        elif line.startswith("STARTCHAR"):
            flush()
            char = None
        elif line.startswith("ENCODING"):
            enc = int(line.split()[1])
            if enc >= 0:
                char = chr(enc)
        elif line.startswith("DWIDTH"):
            dwidth = int(line.split()[1])
        elif line.startswith("BBX"):
            parts = line.split()
            bw, bh, bx, by = (int(p) for p in parts[1:5])
        elif line == "BITMAP":
            in_bitmap = True
            rows = []
        elif line == "ENDCHAR":
            if char is not None and bw == 0 and dwidth > 0:
                bw = dwidth  # 空白字符（空格等）：按字距输出空行
            flush()
            in_bitmap = False
        elif in_bitmap and line:
            rows.append(int(line, 16))
    flush()
    return glyphs


def main() -> None:
    bdf = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else (
        ROOT / "module" / "device" / "src" / "main" / "resources" / "font" / "pixel-font.json"
    )
    if bdf is None:
        raise SystemExit("用法: python tools/fontgen_bdf.py <bdf路径> [输出json路径]")

    glyphs = parse_bdf(bdf)
    charset = load_charset()
    picked, missing = {}, []
    for ch in dict.fromkeys(charset):
        if ch in glyphs:
            picked[ch] = glyphs[ch]
        else:
            missing.append(ch)

    payload = json.dumps({"size": CELL_H, "glyphs": picked}, ensure_ascii=False, separators=(",", ":"))
    # 后端（服务端渲染动态内容）与前端（画布绘制）各一份，保持同源一致
    targets = [out, ROOT / "gazzette-desk" / "src" / "assets" / "pixel-font.json"]
    for target in targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(payload, encoding="utf-8")
        print(f"字库已生成: {target} ({len(picked)} 字符, {target.stat().st_size // 1024} KB)")
    if missing:
        print(f"字库缺失({len(missing)}, 前端回退栅格): {''.join(missing[:60])}")


if __name__ == "__main__":
    main()
