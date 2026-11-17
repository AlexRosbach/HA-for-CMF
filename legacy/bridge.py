"""Connect one WP2 app slot to one user-configured Home Assistant entity."""

import argparse
import asyncio
import json
import os
import re
from urllib.error import URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class HomeAssistant:
    def __init__(self, url, entity, token):
        parts = urlsplit(url)
        if parts.scheme not in {"http", "https"} or not parts.hostname or (
            parts.username or parts.password or parts.query or parts.fragment
        ):
            raise ValueError("Use the Home Assistant base URL without credentials, query, or fragment")
        if not re.fullmatch(r"(light|switch)\.[a-z0-9_]+", entity):
            raise ValueError("Use a light.* or switch.* entity ID")
        self.base = url.rstrip("/")
        self.entity = entity
        self.domain = entity.split(".", 1)[0]
        self.token = token
        self.opener = build_opener(NoRedirect())

    def request(self, endpoint, data=None):
        body = json.dumps(data).encode() if data is not None else None
        request = Request(self.base + endpoint, data=body, headers={
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json",
        })
        with self.opener.open(request, timeout=4) as response:
            return json.load(response)

    def handle(self, command):
        action = {b"ha1U": "turn_on", b"ha1D": "turn_off"}.get(command)
        if action:
            self.request(f"/api/services/{self.domain}/{action}", {"entity_id": self.entity})
        state = self.request(f"/api/states/{self.entity}").get("state")
        return {"on": b"ha1ON", "off": b"ha1OFF"}.get(state, b"ha1ERR")


async def run(args, home):
    from wp2apps.channel import Channel, NoApp

    async with Channel(address=args.address, hci_port=args.hci_port, slot=args.slot) as radio:
        print("Watch connected. Open HA for CMF on the watch.", flush=True)
        while True:
            message = await radio.recv()
            if message.data not in (b"ha1R", b"ha1U", b"ha1D"):
                continue
            try:
                reply = await asyncio.to_thread(home.handle, message.data)
            except (URLError, TimeoutError, ValueError, OSError):
                print("Home Assistant request failed. Check URL, token, and entity.", flush=True)
                reply = b"ha1ERR"
            reply_kind = b"R" if message.data == b"ha1R" else b"A"
            try:
                await radio.send(reply[:3] + reply_kind + reply[3:], timeout=5)
            except (NoApp, TimeoutError):
                continue


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    link = parser.add_mutually_exclusive_group(required=True)
    link.add_argument("--address", help="Watch address or macOS Bluetooth UUID")
    link.add_argument("--hci-port", type=int, help="Virtual Bluetooth port for emulator testing")
    parser.add_argument("--slot", required=True, type=int, choices=range(8), help="App slot from installation")
    parser.add_argument("--url", required=True)
    parser.add_argument("--entity", required=True)
    args = parser.parse_args()
    token = os.environ.get("HA_TOKEN")
    if not token:
        parser.error("Set HA_TOKEN in the Mac environment")
    try:
        home = HomeAssistant(args.url, args.entity, token)
    except ValueError as error:
        parser.error(str(error))
    try:
        asyncio.run(run(args, home))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
