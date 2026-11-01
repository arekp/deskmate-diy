#!/usr/bin/env python3
"""Pomocnik ekranu – pozwala robotowi "spojrzeć" na ekran komputera, gdy o to poprosisz.

Uruchom na komputerze, przy którym stoi robot:

    pip install mss pillow
    python desktop/screen_helper.py --token zmien-mnie

Telefon pobiera  GET http://<ip-komputera>:8765/screenshot  z nagłówkiem  X-Token: <token>.
Zrzut jest pomniejszany i kompresowany do JPEG; nic nie jest zapisywane na dysku.

Prywatność: serwer odpowiada tylko z poprawnym tokenem. Uruchamiaj go wyłącznie w zaufanej
sieci domowej i wyłączaj (Ctrl+C), gdy nie chcesz, żeby robot mógł widzieć ekran.
"""

import argparse
import hmac
import io
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import mss
from PIL import Image

MAX_SIDE = 1568  # wystarczająco dla modelu, a szybciej się przesyła


def grab_jpeg(monitor_index: int, quality: int = 70) -> bytes:
    with mss.mss() as sct:
        shot = sct.grab(sct.monitors[monitor_index])
        img = Image.frombytes("RGB", shot.size, shot.bgra, "raw", "BGRX")
    img.thumbnail((MAX_SIDE, MAX_SIDE))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=quality)
    return buf.getvalue()


def make_handler(token: str, monitor: int):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):  # noqa: N802
            if self.path.split("?")[0] != "/screenshot":
                self.send_error(404)
                return
            if not hmac.compare_digest(self.headers.get("X-Token", ""), token):
                self.send_error(401)
                return
            data = grab_jpeg(monitor)
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            print(f"Wysłano zrzut ekranu do {self.client_address[0]} ({len(data) // 1024} KB)")

        def log_message(self, *args):
            pass

    return Handler


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--token", required=True, help="wspólny sekret, ten sam co screenHelper.token w aplikacji")
    p.add_argument("--port", type=int, default=8765)
    p.add_argument("--monitor", type=int, default=1, help="1 = główny monitor, 0 = wszystkie razem")
    args = p.parse_args()

    server = ThreadingHTTPServer(("0.0.0.0", args.port), make_handler(args.token, args.monitor))
    print(f"Pomocnik ekranu działa na porcie {args.port}. Ctrl+C kończy.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
