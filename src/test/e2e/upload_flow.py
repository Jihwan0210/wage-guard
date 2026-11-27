"""화면 흐름 시험: 첫 화면에서 서류를 올리면 곧바로 읽기를 시작하고, 읽는 동안 상황을 고른다.
다 읽어도 화면을 넘기지 않고 「다음」을 눌러야 확인 화면으로 간다.

서류 읽기 API 는 이 시험 안에서 가짜 응답으로 바꿔 끼운다(AI 키 없이, 늘 같은 결과로).
사용: python -I -X utf8 src/test/e2e/upload_flow.py http://127.0.0.1:8080 [사진 파일]
준비: pip install playwright && playwright install chromium (Edge 가 있으면 그것을 쓴다)
끝 줄이 FLOW OK 면 통과, 아니면 실패한 시나리오를 적고 1 로 끝난다.
"""
import json
import struct
import sys
import tempfile
import zlib
from pathlib import Path

from playwright.sync_api import sync_playwright

BASE = sys.argv[1].rstrip("/")


def png(path):
    """사진 파일을 주지 않으면 1200x1600 흰 PNG 를 만든다 (화면이 읽기 전에 사진을 다듬을 수 있는 크기)."""
    w, h = 1200, 1600
    raw = b"".join(b"\x00" + b"\xff" * (w * 3) for _ in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    return str(path)


PHOTO = sys.argv[2] if len(sys.argv) > 2 else png(Path(tempfile.gettempdir()) / "wageback_flow_test.png")
READ_OK = {
    "pages": [{"page": 1, "docType": "PAYSLIP", "ok": True, "fields": [], "unreadable": [], "maskedCount": 0}],
    "fields": [
        {"key": "companyName", "value": "시험식당", "confidence": 0.95, "page": 1},
        {"key": "wageType", "value": "MONTHLY", "confidence": 0.95, "page": 1},
        {"key": "baseWage", "value": 2156880, "confidence": 0.95, "page": 1},
        {"key": "payMonth", "value": "2026-10", "confidence": 0.9, "page": 1},
        {"key": "paidAmount", "value": 1800000, "confidence": 0.9, "page": 1},
    ],
    "unreadable": ["overtimeHours"], "conflicts": [], "maskedCount": 0,
}
READ_FAIL = {"code": "OCR_FAILED", "field": "file", "message": "서류를 읽지 못했어요. 직접 입력해 주세요."}
fails, errors = [], []


def check(name, ok, why=""):
    print(("ok   " if ok else "FAIL ") + name + ("" if ok else f"  ({why})"), flush=True)
    if not ok:
        fails.append(name)


def run(p, width, phone):
    b = p.chromium.launch(channel="msedge", headless=True) if edge else p.chromium.launch(headless=True)
    ctx = b.new_context(viewport={"width": width, "height": 844 if phone else 800}, is_mobile=phone, has_touch=phone,
                        locale="ko-KR", timezone_id="Asia/Seoul", service_workers="block")
    page = ctx.new_page()
    page.on("pageerror", lambda e: errors.append(f"{width} {e}"))
    page.on("console", lambda m: errors.append(f"{width} {m.text}") if m.type == "error" and "Failed to load resource" not in m.text else None)
    mode = {"answer": "ok", "delay": 2500}
    # 브라우저 안에서 서류 읽기 요청만 가로채 몇 초 뒤 가짜 답을 준다 (실제 서버처럼 기다리는 동안 화면을 누를 수 있게)
    page.add_init_script("""(() => {
      const real = window.fetch;
      window.fetch = (url, opt) => {
        if (!String(url).includes('/documents/extract-batch')) return real(url, opt);
        const m = window.__read || { ok: true, delay: 2500 };
        return new Promise(r => setTimeout(() => r(new Response(JSON.stringify(m.ok ? %s : %s),
          { status: m.ok ? 200 : 422, headers: { 'Content-Type': 'application/json' } })), m.delay));
      };
    })();""" % (json.dumps(READ_OK, ensure_ascii=False), json.dumps(READ_FAIL, ensure_ascii=False)))
    tag = f"[{width}] "

    def fresh():
        # 화면이 살아 있으면 나갈 때 지운 값을 다시 저장한다. 지우고 나서 저장을 막은 채 나갔다가 새로 연다
        page.goto("about:blank")
        page.goto(BASE + "/#start")
        page.evaluate("() => { localStorage.clear(); sessionStorage.clear(); Storage.prototype.setItem = () => {}; }")
        page.goto("about:blank")
        page.goto(BASE + "/#start")
        page.wait_for_selector(".start .drop")
        page.evaluate("(m) => { window.__read = { ok: m.answer === 'ok', delay: m.delay } }", mode)

    def h():
        return page.evaluate("location.hash")

    def ox():
        return page.evaluate("document.documentElement.scrollWidth - document.documentElement.clientWidth")

    # 1. 서류 있음: 올리면 바로 상황 화면, 읽는 동안 고르고, 다 읽어도 그대로, 「다음」 으로 확인 화면
    fresh()
    check(tag + "첫 화면에 올리기 칸과 서류 없이 링크", page.locator('.drop label[for="file"]').count() == 1 and page.locator('[data-act="pick"][data-v="manual"]').count() == 1)
    check(tag + "첫 화면에 따로 「서류로 시작하기」 단추 없음", page.locator('[data-act="pick"][data-v="doc"]').count() == 0)
    check(tag + "첫 화면 가로 넘침 없음", ox() == 0, ox())
    page.set_input_files("#file", PHOTO)
    page.wait_for_function("() => location.hash === '#sit'", timeout=5000)
    page.wait_for_selector(".read-st .read-ind", timeout=5000)
    check(tag + "올리자마자 상황 화면에서 읽는 중 표시", True)
    page.click('label[for="sit-less"]')
    page.click('label[for="sit-sev"]')
    page.wait_for_selector(".read-st.done", timeout=15000)
    page.wait_for_timeout(400)
    check(tag + "다 읽어도 화면을 넘기지 않음", h() == "#sit", h())
    check(tag + "고른 카드가 그대로 남음", page.is_checked("#sit-less") and page.is_checked("#sit-sev"))
    page.click('label[for="sit-extra"]')
    check(tag + "다 읽은 뒤에도 마저 고를 수 있음", page.is_checked("#sit-extra"))
    check(tag + "상황 화면 가로 넘침 없음", ox() == 0, ox())
    page.click("#sit-next")
    page.wait_for_function("() => location.hash === '#review'", timeout=5000)
    check(tag + "「다음」 을 눌러야 확인 화면", True)
    check(tag + "확인 화면에 읽은 값", "시험식당" in page.inner_text("main"))

    # 2. 읽는 중에 「다음」: 기다렸다가 다 읽으면 넘어감
    fresh()
    page.set_input_files("#file", PHOTO)
    page.wait_for_selector(".read-st .read-ind", timeout=5000)
    page.click('label[for="sit-late"]')
    page.click("#sit-next")
    page.wait_for_selector('#sit-next[aria-busy="true"]', timeout=3000)
    check(tag + "읽는 중 「다음」 이면 이 화면에서 기다림", h() == "#sit", h())
    page.wait_for_function("() => location.hash === '#review'", timeout=15000)
    check(tag + "다 읽으면 확인 화면으로 넘어감", True)

    # 3. 읽기 실패: 그 자리에서 다시 올리기, 또는 「다음」이 「서류 없이 계속하기」로 바뀌어 고른 상황 그대로 질문으로
    mode["answer"], mode["delay"] = "fail", 800
    fresh()
    page.set_input_files("#file", PHOTO)
    page.wait_for_selector("#read-err", timeout=15000)
    check(tag + "못 읽으면 같은 화면에 다시 올리기", page.locator('#read-err label[for="file"]').count() == 1 and h() == "#sit", h())
    check(tag + "못 읽으면 「다음」은 서류 없이 계속하기", "서류 없이" in page.inner_text("#sit-next"), page.inner_text("#sit-next"))
    page.click('label[for="sit-less"]')
    page.click("#sit-next")
    page.wait_for_function("() => location.hash === '#q1'", timeout=5000)
    check(tag + "서류 없이 계속하면 질문으로", True)
    page.go_back()
    page.wait_for_function("() => location.hash === '#sit'", timeout=5000)
    check(tag + "서류 없이 계속해도 고른 카드는 그대로", page.is_checked("#sit-less") and page.locator("#read-err").count() == 0)
    mode["answer"], mode["delay"] = "ok", 2500

    # 4. 서류 없음: 첫 화면 링크 → 상황 → 질문. 아무것도 안 고르면 한 번 묻고 넘어갈 수 있음
    fresh()
    page.click('[data-act="pick"][data-v="manual"]')
    page.wait_for_function("() => location.hash === '#sit'", timeout=5000)
    check(tag + "서류 없이 오면 읽기 표시 없음", page.locator(".read-st, #read-err").count() == 0)
    page.click("#sit-next")
    page.wait_for_selector("dialog[open]", timeout=3000)
    page.click('dialog [data-act="sit-next"][data-skip]')
    page.wait_for_function("() => location.hash === '#q1'", timeout=5000)
    check(tag + "고르지 않고 넘어가기로 질문", True)
    b.close()


with sync_playwright() as p:
    try:
        b = p.chromium.launch(channel="msedge", headless=True)
        b.close()
        edge = True
    except Exception:
        edge = False
    for w, phone in ((1280, False), (390, True)):
        try:
            run(p, w, phone)
        except Exception as e:
            fails.append(f"[{w}] 멈춤: {str(e)[:300]}")
            print("FAIL", w, str(e)[:300], flush=True)

check("콘솔 오류 없음", not errors, "; ".join(errors)[:400])
print("FLOW OK" if not fails else "FLOW FAIL: " + ", ".join(fails))
sys.exit(1 if fails else 0)
