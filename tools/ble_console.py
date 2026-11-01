#!/usr/bin/env python3
"""Konsola BLE do testowania "ciała" robota z komputera – bez telefonu.

    pip install bleak
    python tools/ble_console.py

Wpisuj komendy z docs/protokol-ble.md, np.:  L 30 10 500   G nod   H   S
Specjalne:  demo  – krótki pokaz wszystkich gestów,  track – symulacja śledzenia (ruch po okręgu).
"""

import asyncio
import math
import sys

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "4d7d0001-8a3e-4b8f-9c1a-2f6d5e7a9b10"
CMD_UUID = "4d7d0002-8a3e-4b8f-9c1a-2f6d5e7a9b10"
STATUS_UUID = "4d7d0003-8a3e-4b8f-9c1a-2f6d5e7a9b10"


async def send(client: BleakClient, cmd: str) -> None:
    await client.write_gatt_char(CMD_UUID, (cmd + "\n").encode(), response=True)


async def demo(client: BleakClient) -> None:
    for g in ["wake", "nod", "shake", "happy", "curious", "sad"]:
        print(f"> G {g}")
        await send(client, f"G {g}")
        await asyncio.sleep(2.2)
    await send(client, "H")


async def track(client: BleakClient, seconds: float = 8.0) -> None:
    steps = int(seconds * 10)
    for i in range(steps):
        a = i / steps * 2 * math.pi
        await send(client, f"L {40 * math.cos(a):.1f} {15 * math.sin(a):.1f} 150")
        await asyncio.sleep(0.1)
    await send(client, "H")


async def main() -> None:
    print("Szukam robota…")
    device = await BleakScanner.find_device_by_filter(
        lambda d, adv: SERVICE_UUID in [u.lower() for u in adv.service_uuids], timeout=10
    )
    if device is None:
        sys.exit("Nie znaleziono urządzenia DeskMate-DIY. Czy ESP32 jest włączone?")

    async with BleakClient(device) as client:
        print(f"Połączono z {device.name} ({device.address}). Wpisz komendę albo 'q'.")
        await client.start_notify(STATUS_UUID, lambda _, data: print(f"< {data.decode(errors='replace')}"))

        loop = asyncio.get_running_loop()
        while True:
            line = (await loop.run_in_executor(None, sys.stdin.readline)).strip()
            if line in ("q", "quit", "exit"):
                break
            if line == "demo":
                await demo(client)
            elif line == "track":
                await track(client)
            elif line:
                await send(client, line)


if __name__ == "__main__":
    asyncio.run(main())
