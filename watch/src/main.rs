#![no_std]
#![no_main]

use cmf_wp2_app::{Api, BUTTON, MSG_MAX, PAUSE, RESUME, TOUCH_DOWN};
use cmf_wp2_ui::{Rgb, draw, text};

cmf_wp2_app::entry!(main);

const MAX: usize = 4;
const FIELD: usize = 10;
const BG: Rgb = Rgb(8, 10, 13);
const INK: Rgb = Rgb(250, 250, 250);
const MUTED: Rgb = Rgb(156, 158, 166);
const ORANGE: Rgb = Rgb(255, 106, 24);
const GREEN: Rgb = Rgb(90, 220, 146);
const CARD: Rgb = Rgb(37, 39, 44);

#[derive(Clone, Copy, Default, PartialEq, Eq)]
struct Tile {
    title: [u8; FIELD],
    value: [u8; FIELD],
    title_len: u8,
    value_len: u8,
    flags: u8,
}

impl Tile {
    fn title(&self) -> &str {
        core::str::from_utf8(&self.title[..self.title_len as usize]).unwrap_or("")
    }

    fn value(&self) -> &str {
        core::str::from_utf8(&self.value[..self.value_len as usize]).unwrap_or("")
    }
}

#[derive(Clone, Copy, Default, PartialEq, Eq)]
struct Screen {
    tiles: [Tile; MAX],
    count: usize,
}

impl Screen {
    fn parse(mut message: &[u8]) -> Option<Self> {
        let (&count, rest) = message.split_first()?;
        if !(1..=MAX as u8).contains(&count) {
            return None;
        }
        message = rest;
        let mut screen = Self { count: count as usize, ..Self::default() };
        for tile in &mut screen.tiles[..screen.count] {
            let [flags, title_len, value_len] = *message.first_chunk::<3>()?;
            let (t, v) = (title_len as usize, value_len as usize);
            if flags > 3 || t > FIELD || v > FIELD || message.len() < 3 + t + v {
                return None;
            }
            let fields = &message[3..3 + t + v];
            if !fields.iter().all(|&b| (b' '..=b'~').contains(&b)) {
                return None;
            }
            tile.flags = flags;
            tile.title_len = title_len;
            tile.value_len = value_len;
            tile.title[..t].copy_from_slice(&fields[..t]);
            tile.value[..v].copy_from_slice(&fields[t..]);
            message = &message[3 + t + v..];
        }
        message.is_empty().then_some(screen)
    }
}

const TILE_W: i32 = 174;
const TILE_H: i32 = 107;

fn tile_origin(index: usize, count: usize) -> (i32, i32) {
    let row = index / 2;
    let centered = count == 1 || (count == 3 && index == 2);
    let x = if centered { 146 } else { 51 + (index % 2) as i32 * 190 };
    let y = if count < 3 { 198 } else { 153 + row as i32 * 117 };
    (x, y)
}

fn render(mut api: &Api, screen: &Screen, waiting: Option<usize>, offline: bool) {
    api.rect(0, 0, 466, 466, BG.rgb565());
    // CMF-inspired high-contrast design; keep the BLE tile protocol unchanged.
    text::centre(&mut api, 233, 52, "HA FOR CMF", MUTED.rgb565());
    let status = if offline { "PHONE OFFLINE" }
        else if screen.count == 0 { "CONNECTING..." }
        else { "CONNECTED" };
    text::centre(&mut api, 233, 83, status, (if offline { ORANGE } else { GREEN }).rgb565());
    draw::rrect(&mut api, 77, 107, 312, 35, 17, CARD, BG);
    text::centre(&mut api, 233, 117, "HOME ASSISTANT", INK.rgb565());
    if screen.count == 0 {
        text::centre(&mut api, 233, 239, if offline { "Wake phone & retry" } else { "Waiting for bridge" }, INK.rgb565());
    }
    for (index, tile) in screen.tiles[..screen.count].iter().enumerate() {
        let (x, y) = tile_origin(index, screen.count);
        let lit = !offline && tile.flags == 3;
        let fill = if lit { ORANGE } else { CARD };
        let foreground = if lit { BG } else { INK };
        draw::rrect(&mut api, x, y, TILE_W, TILE_H, 21, fill, BG);
        text::centre(&mut api, x + TILE_W / 2, y + 20, tile.title(), foreground.rgb565());
        let value = if offline { "Offline" }
            else if waiting == Some(index) { "Updating..." }
            else { tile.value() };
        text::centre(&mut api, x + TILE_W / 2, y + 65, value,
            if lit { BG } else if offline { ORANGE } else { INK }.rgb565());
    }
    text::centre(&mut api, 233, 422, "SMART HOME", MUTED.rgb565());
    api.show();
}

fn main(api: &'static Api) -> i32 {
    let mut screen = Screen::default();
    let mut pending = None;
    let mut received = api.now();
    let mut next_poll = api.now();
    let mut offline = false;
    let mut paused = false;
    let mut message = [0; MSG_MAX];
    render(api, &screen, pending, offline);
    loop {
        let event = api.event(if paused { u32::MAX } else { 250 });
        let now = api.now();
        let old = (screen, pending, offline);
        let mut redraw = false;
        if let Some(event) = event {
            match event.kind {
                BUTTON => return 0,
                PAUSE => paused = true,
                RESUME => {
                    paused = false;
                    pending = None;
                    offline = false;
                    screen = Screen::default();
                    received = now;
                    next_poll = now;
                    redraw = true;
                }
                TOUCH_DOWN if !paused && !offline && pending.is_none() => {
                    for (i, tile) in screen.tiles[..screen.count].iter().enumerate() {
                        let (x, y) = tile_origin(i, screen.count);
                        if tile.flags & 1 == 0 || !(x..x + TILE_W).contains(&(event.x as i32))
                            || !(y..y + TILE_H).contains(&(event.y as i32)) {
                            continue;
                        }
                        let action = if tile.flags & 2 == 0 { b'U' } else { b'D' };
                        if api.send(&[b'h', b'a', b'2', action, i as u8]) == 0 {
                            pending = Some(i);
                            received = now;
                        } else {
                            offline = true;
                        }
                        break;
                    }
                }
                _ => {}
            }
        }
        if paused { continue; }
        while let Some(data) = api.recv(&mut message) {
            if let Some(values) = data.strip_prefix(b"ha2A").or_else(|| {
                if pending.is_none() { data.strip_prefix(b"ha2S") } else { None }
            }) {
                if let Some(snapshot) = Screen::parse(values) {
                    screen = snapshot;
                    pending = None;
                    offline = false;
                    received = now;
                }
            }
        }
        if now.wrapping_sub(received) >= 15_000 {
            offline = true;
            pending = None;
        }
        if (now.wrapping_sub(next_poll) as i32) >= 0 {
            if pending.is_none() { api.send(b"ha2R"); }
            next_poll = now.wrapping_add(4000);
        }
        if redraw || (screen, pending, offline) != old {
            render(api, &screen, pending, offline);
        }
    }
}
