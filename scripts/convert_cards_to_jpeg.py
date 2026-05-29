#!/usr/bin/env python3
"""Convert card pack images to low-quality JPEG and backup originals."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PACKS = [
    ROOT / "cards" / "基础包@ThePMVPanel'25",
    ROOT / "cards" / "拓展包@ThePMVPanel'25",
]
JPEG_QUALITY = 15
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}


def flatten_for_jpeg(image: Image.Image) -> Image.Image:
    if image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info):
        rgba = image.convert("RGBA")
        background = Image.new("RGB", rgba.size, (255, 255, 255))
        background.paste(rgba, mask=rgba.split()[-1])
        return background
    if image.mode != "RGB":
        return image.convert("RGB")
    return image


def convert_pack(pack_dir: Path) -> None:
    cards_dir = pack_dir / "cards"
    backup_dir = pack_dir / "_backup_original" / "cards"
    backup_dir.mkdir(parents=True, exist_ok=True)

    converted = 0
    for source in sorted(cards_dir.iterdir()):
        if not source.is_file() or source.suffix.lower() not in IMAGE_SUFFIXES:
            continue

        backup_path = backup_dir / source.name
        if not backup_path.exists():
            shutil.copy2(source, backup_path)

        with Image.open(source) as image:
            original_size = image.size
            rgb = flatten_for_jpeg(image)
            target = cards_dir / f"{source.stem}.jpg"
            rgb.save(target, format="JPEG", quality=JPEG_QUALITY, optimize=True)
            if target.stat().st_size == 0:
                raise RuntimeError(f"empty output: {target}")

            with Image.open(target) as saved:
                if saved.size != original_size:
                    raise RuntimeError(
                        f"resolution changed for {source.name}: {original_size} -> {saved.size}"
                    )

        if source != target:
            source.unlink()
        converted += 1
        print(f"[ok] {pack_dir.name}/{source.name} -> {target.name}")

    print(f"{pack_dir.name}: converted {converted} images")


def main() -> None:
    for pack_dir in PACKS:
        if not (pack_dir / "cards").is_dir():
            raise SystemExit(f"missing cards dir: {pack_dir / 'cards'}")
        convert_pack(pack_dir)


if __name__ == "__main__":
    main()
