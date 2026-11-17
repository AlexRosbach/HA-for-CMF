#![no_std]
#![no_main]

use cmf_wp2_app::{Api, BUTTON, CROWN, MSG_MAX, PAUSE, RESUME, TOUCH_DOWN};
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
        if count > MAX as u8 {
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
    let y = if count < 3 { 193 } else { 149 + row as i32 * 114 };
    (x, y)
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Mode { Home, Search, Assistant }

const LETTERS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";
const INPUT_MAX: usize = 16;

fn send_word(api: &Api, command: u8, query: &[u8; INPUT_MAX], len: usize) -> i32 {
    let mut data = [0u8; 4 + INPUT_MAX];
    data[..3].copy_from_slice(b"ha2");
    data[3] = command;
    data[4..4 + len].copy_from_slice(&query[..len]);
    api.send(&data[..4 + len])
}

fn display_word<'a>(buffer: &'a [u8; INPUT_MAX], len: usize) -> &'a str {
    core::str::from_utf8(&buffer[..len]).unwrap_or("")
}

// The stock WP2 16-pixel font is too small on the round display. Use the
// SDK's scaled 8x8 font and never place buttons in the screen's clipped corners.
fn large_centre(mut api: &Api, cx: i32, y: i32, value: &str,
        scale: i32, max_chars: usize, color: Rgb) {
    let (shown, _) = text::clip(value, max_chars);
    let width = text::big_width(shown, scale);
    text::big(&mut api, cx - width / 2, y, shown, scale, color.rgb565());
}

fn render(mut api: &Api, screen: &Screen, results: &Screen,
        mode: Mode, query: &[u8; INPUT_MAX], query_len: usize,
        letter: usize, answer: &[u8; 96], answer_len: usize,
        pending: Option<usize>, confirmation: Option<usize>, offline: bool) {
    api.rect(0, 0, 466, 466, BG.rgb565());
    let has_snapshot = screen.pages != 0 || results.pages != 0;
    let (state, state_color) = if offline { ("OFFLINE", ORANGE) }
        else if !has_snapshot { ("WARTE AUF HANDY", MUTED) }
        else { ("VERBUNDEN", GREEN) };
    // Top bar is inside the circular safe area.
    draw::rrect(&mut api, 100, 42, 266, 44, 18, CARD, BG);
    large_centre(api, 233, 55, state, 2, 16, state_color);

    if mode == Mode::Home {
        let room = core::str::from_utf8(&screen.room[..screen.room_len]).unwrap_or("");
        let title = if room.is_empty() { "FAVORITEN" } else { room };
        large_centre(api, 233, 107, title, 3, 12, INK);
        if screen.count == 0 {
            let main = if !has_snapshot { "KEINE DATEN" } else { "0 FAVORITEN" };
            large_centre(api, 233, 224, main, 3, 14, INK);
            large_centre(api, 233, 268,
                    if has_snapshot { "AM HANDY AUSWAEHLEN" }
                    else { "BRIDGE STARTEN" }, 2, 22, MUTED);
        }
        for (index, tile) in screen.tiles[..screen.count].iter().enumerate() {
            let (x, y) = tile_origin(index, screen.count);
            let on = !offline && tile.flags & 2 != 0 && confirmation != Some(index);
            let fill = if on { ORANGE } else { CARD };
            let ink = if on { BG } else { INK };
            draw::rrect(&mut api, x, y, TILE_W, TILE_H, 20, fill, BG);
            let title = if confirmation == Some(index) {
                if tile.flags & 2 == 0 { "ENTSPERREN?" } else { "VERRIEGELN?" }
            } else { tile.title() };
            let (line, cut) = text::clip(title, 7);
            large_centre(api, x + TILE_W / 2,
                    if cut { y + 9 } else { y + 25 }, line, 3, 7, ink);
            if cut {
                let rest = &title[line.len()..];
                large_centre(api, x + TILE_W / 2, y + 38, rest, 3, 7, ink);
            }
            let value = if confirmation == Some(index) { "NOCHMAL" }
                else if pending == Some(index) { "SENDEN..." }
                else if offline { "OFFLINE" } else { tile.value() };
            large_centre(api, x + TILE_W / 2, y + 79, value, 2, 10,
                    if confirmation == Some(index) { ORANGE } else { ink });
        }
        // Bottom navigation at y=380, not in the clipped bottom corners.
        draw::rrect(&mut api, 98, 379, 91, 39, 15, CARD, BG);
        draw::rrect(&mut api, 279, 379, 91, 39, 15, CARD, BG);
        large_centre(api, 143, 390, "SUCHE", 2, 5, ORANGE);
        large_centre(api, 216, 389, "<", 3, 1, INK);
        large_centre(api, 255, 389, ">", 3, 1, INK);
        large_centre(api, 325, 390, "ASSIST", 2, 6, ORANGE);
    } else {
        let searching = mode == Mode::Search;
        large_centre(api, 233, 103,
                if searching { "SUCHE" } else { "ASSISTENT" }, 3, 10, INK);
        large_centre(api, 114, 104, "<", 2, 1, ORANGE);
        draw::rrect(&mut api, 62, 144, 342, 46, 16, CARD, BG);
        let query_text = display_word(query, query_len);
        large_centre(api, 233, 155, if query_len == 0 { "TEXT EINGEBEN" }
                else { query_text }, 2, 17, INK);
        large_centre(api, 233, 204, "KRONE DREHEN", 2, 16, MUTED);
        draw::rrect(&mut api, 74, 230, 149, 47, 17, ORANGE, BG);
        draw::rrect(&mut api, 244, 230, 149, 47, 17, CARD, BG);
        let selected = core::str::from_utf8(&LETTERS[letter..letter + 1]).unwrap_or("A");
        large_centre(api, 149, 241, selected, 3, 1, BG);
        large_centre(api, 318, 245, "LOESCHEN", 2, 9, INK);
        if searching {
            let count = core::cmp::min(results.count, 3);
            for i in 0..count {
                let tile = &results.tiles[i];
                let y = 287 + i as i32 * 31;
                draw::rrect(&mut api, 75, y, 316, 29, 10, CARD, BG);
                large_centre(api, 233, y + 6, tile.title(), 2, 10, INK);
            }
            if count == 0 {
                large_centre(api, 233, 305,
                        if !has_snapshot { "WARTEN..." } else { "KEINE TREFFER" },
                        2, 16, MUTED);
            }
        } else {
            let response = core::str::from_utf8(&answer[..answer_len]).unwrap_or("");
            if answer_len == 0 {
                large_centre(api, 233, 315, "TEXT ODER HANDY", 2, 16, MUTED);
            } else {
                let bytes = response.as_bytes();
                for i in 0..3 {
                    let first = i * 20;
                    if first >= bytes.len() { break; }
                    let end = core::cmp::min(first + 20, bytes.len());
                    if let Ok(line) = core::str::from_utf8(&bytes[first..end]) {
                        large_centre(api, 233, 287 + i as i32 * 27, line, 2, 20, INK);
                    }
                }
            }
        }
        draw::rrect(&mut api, 96, 386, 122, 32, 13, CARD, BG);
        draw::rrect(&mut api, 248, 386, 122, 32, 13, ORANGE, BG);
        large_centre(api, 157, 395,
                if searching { "ZURUECK" } else { "HANDY" }, 2, 8, INK);
        large_centre(api, 309, 395,
                if searching { "SUCHE" } else { "FRAGEN" }, 2, 7, BG);
    }
    api.show();
}

fn main(api: &'static Api) -> i32 {
    let mut screen=Screen::default();
    let mut results=Screen::default();
    let mut mode=Mode::Home;
    let mut query=[0u8; INPUT_MAX];
    let mut query_len=0usize;
    let mut letter=0usize;
    let mut answer=[0u8;96];
    let mut answer_len=0usize;
    let mut pending=None;
    let mut confirmation:Option<usize>=None;
    let mut confirmed_at=api.now();
    let mut received=api.now();
    let mut next_poll=api.now();
    let mut offline=false;
    let mut paused=false;
    let mut message=[0u8; MSG_MAX];
    render(api,&screen,&results,mode,&query,query_len,letter,&answer,answer_len,
            pending,confirmation,offline);
    loop {
        let event=api.event(if paused { u32::MAX } else { 250 });
        let now=api.now();
        let mut redraw=false;
        if let Some(event)=event {
            match event.kind {
                BUTTON => return 0,
                PAUSE => paused=true,
                RESUME => {
                    paused=false;
                    pending=None;
                    confirmation=None;
                    offline=false;
                    received=now;
                    next_poll=now;
                    redraw=true;
                }
                CROWN if !paused && mode != Mode::Home => {
                    letter = if event.delta > 0 { (letter+1)%LETTERS.len() }
                        else { (letter+LETTERS.len()-1)%LETTERS.len() };
                    redraw=true;
                }
                TOUCH_DOWN if !paused && pending.is_none() => {
                    let x=event.x as i32;
                    let y=event.y as i32;
                    if mode==Mode::Home {
                        if y >= 375 {
                            if x < 130 {
                                mode=Mode::Search;
                                query_len=0;
                                answer_len=0;
                                results=Screen::default();
                                send_word(api,b'Q',&query,0);
                            } else if x < 230 {
                                api.send(b"ha2P");
                            } else if x < 322 {
                                api.send(b"ha2N");
                            } else {
                                mode=Mode::Assistant;
                                query_len=0;
                                answer_len=0;
                            }
                            redraw=true;
                        } else if !offline {
                            for (i,tile) in screen.tiles[..screen.count].iter().enumerate() {
                                let (tx,ty)=tile_origin(i,screen.count);
                                if tile.flags & 1 == 0 ||
                                    !(tx..tx+TILE_W).contains(&x) ||
                                    !(ty..ty+TILE_H).contains(&y) { continue; }
                                if tile.flags & 4 != 0 &&
                                    (confirmation!=Some(i) || now.wrapping_sub(confirmed_at)>5000) {
                                    confirmation=Some(i);
                                    confirmed_at=now;
                                    redraw=true;
                                    break;
                                }
                                confirmation=None;
                                let action=if tile.flags & 2 == 0 { b'U' } else { b'D' };
                                if api.send(&[b'h',b'a',b'2',action,i as u8])==0 {
                                    pending=Some(i);
                                    received=now;
                                } else { offline=true; }
                                redraw=true;
                                break;
                            }
                        }
                    } else {
                        if y < 139 && x < 146 {
                            mode=Mode::Home;
                            api.send(b"ha2H");
                            redraw=true;
                        } else if (230..280).contains(&y) {
                            if x < 230 {
                                if query_len<INPUT_MAX {
                                    query[query_len]=LETTERS[letter];
                                    query_len+=1;
                                    if mode==Mode::Search {
                                        send_word(api,b'Q',&query,query_len);
                                    }
                                }
                            } else if query_len>0 {
                                query_len-=1;
                                if mode==Mode::Search { send_word(api,b'Q',&query,query_len); }
                            }
                            redraw=true;
                        } else if y>=383 {
                            if mode==Mode::Search {
                                if x<230 {
                                    mode=Mode::Home;
                                    api.send(b"ha2H");
                                } else {
                                    send_word(api,b'Q',&query,query_len);
                                }
                            } else {
                                if x<230 {
                                    api.send(b"ha2V");
                                    answer_len=0;
                                } else if query_len>0 {
                                    send_word(api,b'T',&query,query_len);
                                    answer_len=0;
                                }
                            }
                            redraw=true;
                        } else if mode==Mode::Search && (287..380).contains(&y) {
                            let index=((y-287)/31) as usize;
                            if index<results.count {
                                let tile=results.tiles[index];
                                if tile.flags & 1 != 0 {
                                    // Keep lock actions on phone; watch search only controls non-lock entities.
                                    let action=if tile.flags & 2 == 0 { b'U' } else { b'D' };
                                    if api.send(&[b'h',b'a',b'2',action,index as u8])==0 {
                                        pending=Some(index);
                                        received=now;
                                    }
                                }
                            }
                        }
                    }
                }
                _ => {}
            }
        }
        if paused { continue; }
        while let Some(data)=api.recv(&mut message) {
            if let Some(values)=data.strip_prefix(b"ha2Y") {
                let n=core::cmp::min(values.len(),answer.len());
                answer[..n].copy_from_slice(&values[..n]);
                answer_len=n;
                received=now;
                redraw=true;
                continue;
            }
            if let Some(values)=data.strip_prefix(b"ha2A").or_else(||data.strip_prefix(b"ha2S")) {
                if let Some(snapshot)=Screen::parse(values) {
                    if mode==Mode::Search {
                        results=snapshot;
                    } else {
                        let same_page=screen.page==snapshot.page &&
                                screen.pages==snapshot.pages && screen.room==snapshot.room;
                        screen=snapshot;
                        if !same_page { confirmation=None; }
                    }
                    pending=None;
                    offline=false;
                    received=now;
                    redraw=true;
                }
            }
        }
        if confirmation.is_some()&&now.wrapping_sub(confirmed_at)>5000 {
            confirmation=None;
            redraw=true;
        }
        if now.wrapping_sub(received)>=15000 {
            if !offline { redraw=true; }
            offline=true;
            pending=None;
            confirmation=None;
        }
        if (now.wrapping_sub(next_poll) as i32)>=0 {
            if pending.is_none()&&mode==Mode::Home { api.send(b"ha2R"); }
            next_poll=now.wrapping_add(4000);
        }
        if redraw {
            render(api,&screen,&results,mode,&query,query_len,letter,
                    &answer,answer_len,pending,confirmation,offline);
        }
    }
}
