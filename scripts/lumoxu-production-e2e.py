#!/usr/bin/env python3
"""生产环境：lumoxu test 牌组图鉴 + 对局 E2E，并输出 Playwright 录像。"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

from playwright.sync_api import sync_playwright, expect

BASE_URL = "http://120.53.245.110:9001"
API_BASE = "http://120.53.245.110:9002/api/v1"
LIB_ID = "u16fb3502_lumoxu-test"
LIB_LABEL = "lumoxu test"
ARTIFACT_DIR = Path("/opt/cursor/artifacts")
VIDEO_RAW_DIR = ARTIFACT_DIR / "video_raw"
OUTPUT_MP4 = ARTIFACT_DIR / "lumoxu-test-production-e2e.mp4"
LOG_PATH = ARTIFACT_DIR / "lumoxu-e2e-log.txt"


def log(msg: str) -> None:
    line = f"[{time.strftime('%H:%M:%S')}] {msg}"
    print(line, flush=True)
    with LOG_PATH.open("a", encoding="utf-8") as f:
        f.write(line + "\n")


def api(method: str, path: str, token: str | None = None, body: dict | None = None) -> dict:
    url = f"{API_BASE}{path}"
    data = None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code} {path}: {raw}") from e
    out = json.loads(raw)
    if out.get("code") != 0:
        raise RuntimeError(f"API {path}: {out}")
    return out["data"]


def register_user() -> tuple[str, str, str]:
    user = f"e2e{int(time.time()) % 100000000}"
    password = "TestPass123!"
    data = api("POST", "/auth/register", body={"username": user, "password": password})
    token = data["token"]
    client_id = data["player"]["clientId"]
    log(f"registered user={user} clientId={client_id}")
    return token, user, password


def browser_register(page, user: str, password: str) -> str:
    page.goto(f"{BASE_URL}/auth", wait_until="networkidle")
    reg_form = page.locator("form").nth(1)
    reg_form.locator("input").nth(0).fill(user)
    passwords = reg_form.locator('input[type="password"]')
    passwords.nth(0).fill(password)
    passwords.nth(1).fill(password)
    reg_form.get_by_role("button", name="注册并进入").click()
    page.wait_for_url(f"{BASE_URL}/", timeout=30000)
    expect(page.get_by_role("heading", name="主菜单")).to_be_visible(timeout=30000)
    token = page.evaluate("() => localStorage.getItem('cof.token') || ''")
    if not token:
        raise RuntimeError("no token after browser register")
    log(f"registered via UI user={user}")
    return token


def create_room_ui(page, token: str) -> str:
    page.get_by_role("link", name="创建房间").click()
    page.wait_for_url("**/rooms/create**", timeout=30000)
    expect(page.get_by_role("heading", name="创建房间")).to_be_visible(timeout=20000)
    for label in page.locator("label.library-row").all():
        text = label.inner_text()
        box = label.locator('input[type="checkbox"]')
        want = LIB_LABEL in text or LIB_ID in text
        if want:
            box.check(force=True)
        elif box.is_checked():
            box.uncheck(force=True)
    page.get_by_role("button", name="创建房间").click()
    page.wait_for_url("**/waiting**", timeout=30000)
    m = re.search(r"/room/([^/]+)/waiting", page.url)
    if not m:
        raise RuntimeError(f"cannot parse room id from {page.url}")
    room_id = m.group(1)
    log(f"room created via UI id={room_id}")
    return room_id


def create_room_api(token: str) -> str:
    payload = {
        "settings": {
            "minPlayers": 2,
            "maxPlayers": 4,
            "isPublic": True,
            "libraryIds": [LIB_ID],
            "libraryCopies": {LIB_ID: 1},
        },
        "computerIds": ["computer_god", "computer_master"],
    }
    data = api("POST", "/rooms", token=token, body=payload)
    room_id = data["room"]["id"]
    log(f"room created via API id={room_id} players={len(data['room'].get('players') or [])}")
    return room_id


def convert_webm_to_mp4(webm_path: Path, mp4_path: Path) -> None:
    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        str(webm_path),
        "-c:v",
        "libx264",
        "-preset",
        "fast",
        "-crf",
        "23",
        "-pix_fmt",
        "yuv420p",
        "-movflags",
        "+faststart",
        str(mp4_path),
    ]
    subprocess.run(cmd, check=True, capture_output=True)


def goto_home_cards(page) -> None:
    """整页 reload 会丢失 Pinia 会话，必须用站内 RouterLink 导航。"""
    if "/cards" not in page.url:
        page.get_by_role("link", name="查看卡牌").click()
    page.wait_for_url("**/cards", timeout=30000)
    expect(page.get_by_role("heading", name="卡组选择")).to_be_visible(timeout=20000)


def select_only_lumoxu(page) -> None:
    goto_home_cards(page)
    page.wait_for_selector("label.library-row", timeout=20000)
    for label in page.locator("label.library-row").all():
        text = label.inner_text()
        box = label.locator('input[type="checkbox"]')
        if LIB_LABEL in text or LIB_ID in text:
            box.check(force=True)
            log("checked lumoxu test")
        else:
            if box.is_checked():
                box.uncheck(force=True)
    page.get_by_role("button", name="查看").click()
    page.wait_for_url("**/cards/info**", timeout=120000)
    log("card gallery loaded")


def verify_gallery_images(page) -> int:
    page.wait_for_selector(".deck-info img, .card-grid img, img", timeout=30000)
    imgs = page.locator("img").all()
    ok = 0
    broken = 0
    for img in imgs[:40]:
        try:
            nw = img.evaluate(
                """el => {
                  if (!el.src || el.src.includes('logo') || el.src.includes('bell')) return -1;
                  return el.complete && el.naturalWidth > 0 ? el.naturalWidth : 0;
                }"""
            )
        except Exception:
            continue
        if nw == -1:
            continue
        src = img.get_attribute("src") or ""
        if nw > 0:
            ok += 1
            if "/cards/" in src:
                log(f"image ok {src[:80]} ({nw}px)")
        elif src and "/cards/" in src:
            broken += 1
            log(f"image BROKEN {src}")
    if ok == 0:
        page.screenshot(path=str(ARTIFACT_DIR / "gallery-fail.png"), full_page=True)
        raise RuntimeError(f"gallery: no loaded card images (broken={broken})")
    return ok


def run() -> int:
    ARTIFACT_DIR.mkdir(parents=True, exist_ok=True)
    VIDEO_RAW_DIR.mkdir(parents=True, exist_ok=True)
    if LOG_PATH.exists():
        LOG_PATH.unlink()

    user = f"e2e{int(time.time()) % 100000000}"
    password = "TestPass123!"

    video_path: Path | None = None
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--no-sandbox", "--disable-dev-shm-usage"])
        context = browser.new_context(
            viewport={"width": 1280, "height": 720},
            record_video_dir=str(VIDEO_RAW_DIR),
            record_video_size={"width": 1280, "height": 720},
            locale="zh-CN",
        )
        page = context.new_page()
        page.set_default_timeout(60000)

        try:
            live_token = browser_register(page, user, password)
            log(f"browser token ok (len={len(live_token)})")

            select_only_lumoxu(page)
            loaded = verify_gallery_images(page)
            log(f"gallery verified: {loaded} card-related images loaded")
            page.wait_for_timeout(2500)
            page.get_by_role("button", name="返回").click()
            page.wait_for_url(f"{BASE_URL}/", timeout=15000)

            room_id = create_room_ui(page, live_token)
            for n in range(2):
                page.get_by_role("button", name="邀请人机").click()
                page.wait_for_selector(".computer-invite-modal", timeout=10000)
                invites = page.locator(".computer-invite-modal button", has_text="邀请")
                if invites.count() == 0:
                    break
                invites.first.click()
                page.wait_for_timeout(1200)
                page.locator(".computer-invite-modal button.modal-close").click()
            expect(page.get_by_role("heading", name="等待室")).to_be_visible(timeout=30000)
            expect(page.locator(".waiting-player-row", has_text="人机").first).to_be_visible(timeout=20000)
            log("waiting room with computers")

            start_btn = page.get_by_role("button", name="开始游戏")
            expect(start_btn).to_be_enabled(timeout=15000)
            start_btn.click()
            log("clicked start game")

            page.wait_for_url(f"**/room/{room_id}/game**", timeout=120000)
            log("entered game route")
            page.wait_for_selector("button.bell", timeout=120000)
            page.wait_for_timeout(3000)

            for i in range(12):
                play_btn = page.locator("button.draw-stack:not([disabled])").first
                if play_btn.count() and play_btn.is_visible():
                    play_btn.click()
                    log(f"play click #{i + 1}")
                page.wait_for_timeout(2000)
                bell = page.locator("button.bell:not([disabled])")
                if bell.count():
                    try:
                        bell.click(timeout=500)
                        log("bell click")
                    except Exception:
                        pass
                page.wait_for_timeout(1500)
                status = page.locator(".game-hud, .table-area").first
                if status.count():
                    pass

            page.wait_for_timeout(5000)
            page.screenshot(path=str(ARTIFACT_DIR / "game-final.png"), full_page=True)
            log("game session captured")

        finally:
            video_path = Path(page.video.path()) if page.video else None
            context.close()
            browser.close()

    if not video_path or not video_path.exists():
        webms = sorted(VIDEO_RAW_DIR.glob("*.webm"), key=lambda p: p.stat().st_mtime, reverse=True)
        if webms:
            video_path = webms[0]
    if not video_path or not video_path.exists():
        log("ERROR: no video file produced")
        return 1

    log(f"raw video: {video_path} ({video_path.stat().st_size} bytes)")
    convert_webm_to_mp4(video_path, OUTPUT_MP4)
    log(f"mp4 written: {OUTPUT_MP4} ({OUTPUT_MP4.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(run())
