#!/usr/bin/env python3
"""Normalize card fronts to 1500x1080 JPEG and backup originals."""

from __future__ import annotations

import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
JPEG_QUALITY = 15
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg"}
TARGET_SIZE = (1500, 1080)
BACK_SIZE = (720, 1087)


def flatten_for_jpeg(image: Image.Image) -> Image.Image:
    if image.mode in ("RGBA", "LA") or (image.mode == "P" and "transparency" in image.info):
        rgba = image.convert("RGBA")
        background = Image.new("RGB", rgba.size, (255, 255, 255))
        background.paste(rgba, mask=rgba.split()[-1])
        return background
    if image.mode != "RGB":
        return image.convert("RGB")
    return image


def resize_short_side_then_center_crop(image: Image.Image) -> Image.Image:
    width, height = image.size
    short_side = min(width, height)
    if short_side <= 0:
        raise RuntimeError(f"invalid image size: {image.size}")
    scale = TARGET_SIZE[1] / short_side
    resized_size = (round(width * scale), round(height * scale))
    resized = image.resize(resized_size, Image.Resampling.LANCZOS)
    left = max(0, (resized.width - TARGET_SIZE[0]) // 2)
    top = max(0, (resized.height - TARGET_SIZE[1]) // 2)
    return resized.crop((left, top, left + TARGET_SIZE[0], top + TARGET_SIZE[1]))


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
            rgb = flatten_for_jpeg(image)
            normalized = resize_short_side_then_center_crop(rgb)
            target = cards_dir / f"{source.stem}.jpg"
            normalized.save(target, format="JPEG", quality=JPEG_QUALITY, optimize=True)
            if target.stat().st_size == 0:
                raise RuntimeError(f"empty output: {target}")

            with Image.open(target) as saved:
                if saved.size != TARGET_SIZE:
                    raise RuntimeError(
                        f"bad output size for {source.name}: {saved.size}, expected {TARGET_SIZE}"
                    )

        if source != target:
            source.unlink()
        converted += 1
        print(f"[ok] {pack_dir.name}/{source.name} -> {target.name}")

    print(f"{pack_dir.name}: converted {converted} images")


def assert_back_size(pack_dir: Path) -> None:
    back_path = pack_dir / "back.png"
    with Image.open(back_path) as image:
        if image.size != BACK_SIZE:
            raise RuntimeError(f"bad back size for {back_path}: {image.size}, expected {BACK_SIZE}")


def main() -> None:
    pack_dirs = sorted(path for path in (ROOT / "cards").iterdir() if (path / "cards").is_dir())
    for pack_dir in pack_dirs:
        if not (pack_dir / "cards").is_dir():
            raise SystemExit(f"missing cards dir: {pack_dir / 'cards'}")
        assert_back_size(pack_dir)
        convert_pack(pack_dir)


if __name__ == "__main__":
    main()
