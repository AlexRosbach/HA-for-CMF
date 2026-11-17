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
    room: [u8; 18],
    room_len: usize,
    page: u8,
    pages: u8,
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
            if flags > 7 || t > FIELD || v > FIELD || message.len() < 3 + t + v {
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
        screen.pages = 1;
        if !message.is_empty() {
            let [page, total, room_len] = *message.first_chunk::<3>()?;
            let length = room_len as usize;
            if total == 0 || page >= total || length > screen.room.len()
                    || message.len() != 3 + length {
                return None;
            }
            let name = &message[3..];
            if !name.iter().all(|&b| (b' '..=b'~').contains(&b)) { return None; }
            screen.page = page;
            screen.pages = total;
            screen.room_len = length;
            screen.room[..length].copy_from_slice(name);
        }
        Some(screen)
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

fn render(mut api: &Api, screen: &Screen,
        waiting: Option<usize>, confirmation: Option<usize>, offline: bool) {
    api.rect(0, 0, 466, 466, BG.rgb565());
    // Clear room navigation and high-contrast one-tap controls.
    text::centre(&mut api, 233, 52, "HA FOR CMF", MUTED.rgb565());
    let status = if offline { "PHONE OFFLINE" }
        else if screen.count == 0 { "CONNECTING..." }
        else { "CONNECTED" };
    text::centre(&mut api, 233, 83, status, (if offline { ORANGE } else { GREEN }).rgb565());
    draw::rrect(&mut api, 77, 107, 312, 35, 17, CARD, BG);
    let room = core::str::from_utf8(&screen.room[..screen.room_len]).unwrap_or("");
    text::centre(&mut api, 233, 117, if room.is_empty() { "FAVORITES" } else { room },
        INK.rgb565());
    if screen.count == 0 {
        text::centre(&mut api, 233, 239, if offline { "Wake phone & retry" } else { "Waiting for bridge" }, INK.rgb565());
    }
    for (index, tile) in screen.tiles[..screen.count].iter().enumerate() {
        let (x, y) = tile_origin(index, screen.count);
        let lit = !offline && tile.flags & 2 != 0;

        let fill = if confirmation == Some(index) { CARD } else if lit { ORANGE } else { CARD };
        let foreground = if confirmation == Some(index) { INK } else if lit { BG } else { INK };
        draw::rrect(&mut api, x, y, TILE_W, TILE_H, 21, fill, BG);
        let title = if confirmation == Some(index) && tile.flags & 4 != 0 {
            if tile.flags & 2 == 0 { "UNLOCK?" } else { "LOCK?" }
        } else { tile.title() };
        text::centre(&mut api, x + TILE_W / 2, y + 20, title, foreground.rgb565());
        let value = if offline { "Offline" }
            else if confirmation == Some(index) { "TAP AGAIN" }
            else if waiting == Some(index) { "Updating..." }
            else { tile.value() };
        text::centre(&mut api, x + TILE_W / 2, y + 65, value,
            if confirmation == Some(index) { ORANGE.rgb565() }
            else if lit { BG.rgb565() }
            else if offline { ORANGE.rgb565() }
            else { INK.rgb565() });
    }
    if screen.pages > 1 {
        text::centre(&mut api, 101, 416, "< PREV", INK.rgb565());
        text::centre(&mut api, 365, 416, "NEXT >", INK.rgb565());
    } else {
        text::centre(&mut api, 233, 422, "HOME ASSISTANT", MUTED.rgb565());
    }
    api.show();
}

fn main(api: &'static Api) -> i32 {
    let mut screen = Screen::default();
    let mut pending = None;
    let mut confirmation: Option<usize> = None;
    let mut confirmed_at = api.now();
    let mut received = api.now();
    let mut next_poll = api.now();
    let mut offline = false;
    let mut paused = false;
    let mut message = [0; MSG_MAX];
    render(api, &screen, pending, confirmation, offline);
    loop {
        let event = api.event(if paused { u32::MAX } else { 250 });
        let now = api.now();
        let old = (screen, pending, confirmation, offline);
        let mut redraw = false;
        if let Some(event) = event {
            match event.kind {
                BUTTON => return 0,
                PAUSE => paused = true,
                RESUME => {
                    paused = false;
                    pending = None;
                    confirmation = None;
                    offline = false;
                    screen = Screen::default();
                    received = now;
                    next_poll = now;
                    redraw = true;
                }
                TOUCH_DOWN if !paused && !offline && pending.is_none() => {
                    let x = event.x as i32;
                    let y = event.y as i32;
                    if y >= 399 && screen.pages > 1 && (x < 195 || x > 271) {
                        confirmation = None;
                        let command: &[u8] = if x < 195 { b"ha2P" } else { b"ha2N" };
                        if api.send(command) != 0 { offline = true; }
                        received = now;
                    } else {
                        for (i, tile) in screen.tiles[..screen.count].iter().enumerate() {
                            let (tx, ty) = tile_origin(i, screen.count);
                            if tile.flags & 1 == 0 || !(tx..tx + TILE_W).contains(&x)
                                    || !(ty..ty + TILE_H).contains(&y) {
                                continue;
                            }
                            // Locks always require two distinct taps in a five-second window.
                            if tile.flags & 4 != 0 && (confirmation != Some(i)
                                    || now.wrapping_sub(confirmed_at) > 5000) {
                                confirmation = Some(i);
                                confirmed_at = now;
                                redraw = true;
                                break;
                            }
                            confirmation = None;
                            let action = if tile.flags & 2 == 0 { b'U' } else { b'D' };
                            if api.send(&[b'h', b'a', b'2', action, i as u8]) == 0 {
                                pending = Some(i);
                                received = now;
                            } else { offline = true; }
                            break;
                        }
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
                    let same_page = screen.page == snapshot.page && screen.pages == snapshot.pages
                        && screen.room == snapshot.room;
                    screen = snapshot;
                    pending = None;
                    if !same_page { confirmation = None; }
                    offline = false;
                    received = now;
                }
            }
        }
        if confirmation.is_some() && now.wrapping_sub(confirmed_at) >= 5_000 {
            confirmation = None;
        }
        if now.wrapping_sub(received) >= 15_000 {
            offline = true;
            pending = None;
            confirmation = None;
        }
        if (now.wrapping_sub(next_poll) as i32) >= 0 {
            if pending.is_none() { api.send(b"ha2R"); }
            next_poll = now.wrapping_add(4000);
        }
        if redraw || (screen, pending, confirmation, offline) != old {
            render(api, &screen, pending, confirmation, offline);
        }
    }
}
