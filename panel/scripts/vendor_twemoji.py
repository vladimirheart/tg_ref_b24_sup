import json, os, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))           # panel/
PROJ = os.path.dirname(ROOT)                                                 # repo root
NODE = os.path.join(PROJ, "node_modules", "twemoji")
SRC_JS = os.path.join(NODE, "dist", "twemoji.min.js")
SRC_SVG = os.path.join(NODE, "assets", "svg")  # в новых версиях папка именно assets/svg
DST_DIR = os.path.join(ROOT, "static", "vendor", "twemoji")
DST_JS  = os.path.join(DST_DIR, "twemoji.min.js")
DST_SVG = os.path.join(DST_DIR, "svg")
VER_OUT = os.path.join(DST_DIR, "VERSION")

def main():
    if not os.path.exists(NODE):
        print("twemoji is not installed. Run: npm i twemoji", file=sys.stderr)
        sys.exit(1)

    os.makedirs(DST_DIR, exist_ok=True)

    # JS
    shutil.copy2(SRC_JS, DST_JS)

    # SVG (перекладываем целиком)
    if os.path.exists(DST_SVG):
        shutil.rmtree(DST_SVG)
    shutil.copytree(SRC_SVG, DST_SVG)

    # Версию — в файл
    with open(os.path.join(NODE, "package.json"), "r", encoding="utf-8") as f:
        ver = json.load(f).get("version", "unknown")
    with open(VER_OUT, "w", encoding="utf-8") as f:
        f.write(ver + "\n")

    print(f"Twemoji {ver} vendored to {DST_DIR}")

if __name__ == "__main__":
    main()
