"""Draw this project's own house/clock mark for the Apple app icon (Pillow)."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "apple/Resources/Assets.xcassets"


def build():
    (OUT / "AppIcon.appiconset").mkdir(parents=True, exist_ok=True)
    (OUT / "Contents.json").write_text(json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n")
    image = Image.new("RGB", (2048, 2048), "#101e30")
    draw = ImageDraw.Draw(image)
    # Solid square artwork; iOS supplies its own icon mask. No game assets used.
    draw.ellipse((180, 180, 1868, 1868), fill="#19334a")
    draw.rounded_rectangle((470, 820, 1470, 1580), radius=70, fill="#f4e6c6")
    draw.polygon([(310, 900), (970, 350), (1630, 900), (1520, 1040), (970, 580), (420, 1040)], fill="#f6b83d")
    draw.rounded_rectangle((720, 1040, 950, 1580), radius=24, fill="#25465b")
    draw.ellipse((1000, 1050, 1750, 1800), fill="#101e30")
    draw.ellipse((1050, 1100, 1700, 1750), fill="#69bc83")
    draw.ellipse((1100, 1150, 1650, 1700), fill="#132d3c")
    draw.line([(1375, 1220), (1375, 1425), (1510, 1510)], fill="#ffffff", width=55, joint="curve")
    draw.ellipse((1338, 1388, 1412, 1462), fill="#ffffff")
    image.resize((1024, 1024), Image.Resampling.LANCZOS).save(OUT / "AppIcon.appiconset/AppIcon.png")
    content = {"images": [{"filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
               "info": {"author": "xcode", "version": 1}}
    (OUT / "AppIcon.appiconset/Contents.json").write_text(json.dumps(content, indent=2) + "\n")
    print("Apple app icon prepared (original geometry, no third-party artwork).")


if __name__ == "__main__":
    build()
