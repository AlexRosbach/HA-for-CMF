#![no_std]
#![no_main]

use cmf_wp2_app::{Api, BUTTON, CROWN, MSG_MAX, PAUSE, RESUME, TOUCH_DOWN, TOUCH_UP};
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

const TILE_W: i32 = 146;
const TILE_H: i32 = 99;

fn tile_origin(index: usize) -> (i32, i32) {
    (if index % 2 == 0 { 75 } else { 245 },
     if index < 2 { 152 } else { 265 })
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Mode { Home, Categories, Results }

fn big_centre(mut api: &Api, cx: i32, y: i32, value: &str,
        scale: i32, max_chars: usize, color: Rgb) {
    let (part, _) = text::clip(value, max_chars);
    text::big(&mut api, cx - text::big_width(part, scale) / 2,
            y, part, scale, color.rgb565());
}

fn tile_button(mut api: &Api, index: usize, title: &str, value: &str,
        active: bool, editable: bool) {
    let (x,y)=tile_origin(index);
    let bg=if active { ORANGE } else { CARD };
    let ink=if active { BG } else { INK };
    draw::rrect(&mut api,x,y,TILE_W,TILE_H,18,bg,BG);
    let (first, truncated)=text::clip(title,8);
    let rest=if truncated { &title[first.len()..] } else { "" };
    big_centre(api,x+TILE_W/2,if truncated {y+11}else{y+23},
            first,2,8,ink);
    if truncated {
        big_centre(api,x+TILE_W/2,y+32,rest,2,8,ink);
    }
    big_centre(api,x+TILE_W/2,y+72,if editable { value } else { "--" },2,8,ink);
}

fn page_footer(mut api: &Api, index: usize, total: usize, is_search: bool) {
    draw::rrect(&mut api, 96, 381, 93, 41, 14, CARD, BG);
    big_centre(api, 143, 392, if is_search { "ZURUECK" } else { "SUCHE" },
            2,7,ORANGE);
    big_centre(api, 211, 391, "<", 3, 1, INK);
    big_centre(api, 255, 391, ">", 3, 1, INK);
    let mut numbering=[b'0';5];
    let current=core::cmp::min(index+1,9);
    let max=core::cmp::min(total,9);
    numbering[0]=b'0'+current as u8;
    numbering[1]=b'/';
    numbering[2]=b'0'+max as u8;
    let label=core::str::from_utf8(&numbering[..3]).unwrap_or("1/1");
    big_centre(api, 328, 393, label, 2, 5, MUTED);
}

fn render(mut api: &Api, home: &Screen, results: &Screen, mode: Mode,
        selected: u8, waiting: Option<usize>, confirming: Option<usize>, offline: bool) {
    api.rect(0,0,466,466,BG.rgb565());
    let snapshot=home.pages>0;
    let caption=if offline { "OFFLINE" } else if snapshot { "VERBUNDEN" }
            else { "WARTE..." };
    draw::rrect(&mut api, 122, 44, 222, 42, 18, CARD, BG);
    big_centre(api,233,54,caption,2,13,
            if offline { ORANGE } else if snapshot { GREEN } else { MUTED });

    if mode==Mode::Categories {
        big_centre(api,233,109,"SUCHE",3,9,INK);
        let names=["LICHTER","SCHALTER","SZENEN","SENSOREN"];
        for (i,name) in names.iter().enumerate() {
            tile_button(api,i,name,"ANZEIGEN",false,true);
        }
        draw::rrect(&mut api,145,388,176,39,13,CARD,BG);
        big_centre(api,233,398,"ZURUECK",2,8,ORANGE);
    } else {
        let is_search=mode==Mode::Results;
        let screen=if is_search { results } else { home };
        let title=core::str::from_utf8(&screen.room[..screen.room_len]).unwrap_or("");
        big_centre(api,233,109,
                if title.is_empty() { if is_search { "SUCHE" } else { "FAVORITEN" } }
                    else { title },3,12,INK);
        let empty=screen.tiles[..screen.count].iter()
                .all(|t|t.title_len==0);
        if empty {
            big_centre(api,233,215,if !snapshot { "LADE DATEN" }
                    else if is_search { "KEINE TREFFER" } else { "LEERE SEITE" },3,13,INK);
            big_centre(api,233,264,
                    if is_search { "ANDEREN TYP WAEHLEN" } else { "AM HANDY BELEGEN" },
                    2,20,MUTED);
        }else{
            for(i,tile) in screen.tiles[..screen.count].iter().enumerate(){
                if tile.title_len==0 {continue;}
                let active=!offline && tile.flags&2!=0;
                let title=if confirming==Some(i) && tile.flags&4!=0 {
                    if active { "SCHLIESSEN?" } else { "OEFFNEN?" }
                }else{tile.title()};
                let value=if confirming==Some(i) { "NOCHMAL" }
                    else if waiting==Some(i) { "SENDEN" }
                    else if offline { "OFFLINE" } else { tile.value() };
                tile_button(api,i,title,value,active,true);
            }
        }
        page_footer(api,screen.page as usize,
                if screen.pages==0 {1}else{screen.pages as usize},is_search);
    }
    api.show();
}

fn send_page(api: &Api, next: bool) {
    let _=api.send(if next { b"ha2N" } else { b"ha2P" });
}

fn handle_tap(api: &Api, x: i32, y: i32, mode: &mut Mode,
        home: &Screen, results: &Screen, selected: &mut u8,
        waiting: &mut Option<usize>, confirming: &mut Option<usize>,
        confirmed_at: &mut u32, now: u32) -> bool {
    if *mode==Mode::Categories {
        if y>=375 {
            *mode=Mode::Home;
            let _=api.send(b"ha2H");
            return true;
        }
        for i in 0..4 {
            let (tx,ty)=tile_origin(i);
            if (tx..tx+TILE_W).contains(&x) && (ty..ty+TILE_H).contains(&y) {
                *selected=match i {0=>b'L',1=>b'S',2=>b'C',_=>b'T'};
                *mode=Mode::Results;
                let _=api.send(&[b'h',b'a',b'2',b'Q',*selected]);
                return true;
            }
        }
        return false;
    }

    if y>=376 {
        if x>=94 && x<=191 {
            if *mode==Mode::Results {
                *mode=Mode::Categories;
                *confirming=None;
                return true;
            }
            *mode=Mode::Categories;
            return true;
        }
        if x>=195 && x<=235 {send_page(api,false);return false;}
        if x>=235 && x<=279 {send_page(api,true);return false;}
        return false;
    }
    let screen=if *mode==Mode::Results {results} else {home};
    for i in 0..screen.count {
        let (tx,ty)=tile_origin(i);
        if !(tx..tx+TILE_W).contains(&x) || !(ty..ty+TILE_H).contains(&y){
            continue;
        }
        let tile=screen.tiles[i];
        if tile.flags&1==0 {return false;}
        if tile.flags&4!=0 && (*confirming!=Some(i) ||
                now.wrapping_sub(*confirmed_at)>5000) {
            *confirming=Some(i);
            *confirmed_at=now;
            return true;
        }
        *confirming=None;
        let action=if tile.flags&2==0 {b'U'} else {b'D'};
        if api.send(&[b'h',b'a',b'2',action,i as u8])==0 {
            *waiting=Some(i);
            return true;
        }
        return false;
    }
    false
}

fn main(api: &'static Api) -> i32 {
    let mut home=Screen::default();
    let mut results=Screen::default();
    let mut mode=Mode::Home;
    let mut selected=b'L';
    let mut waiting=None;
    let mut confirming=None;
    let mut confirmed_at=api.now();
    let mut last_data=api.now();
    let mut next_poll=api.now();
    let mut last_turn=api.now().wrapping_sub(350);
    let mut last_render=api.now();
    let mut down:Option<(i32,i32)>=None;
    let mut offline=false;
    let mut paused=false;
    let mut inbox=[0u8;MSG_MAX];
    render(api,&home,&results,mode,selected,waiting,confirming,offline);

    loop {
        let event=api.event(if paused {u32::MAX}else{200});
        let now=api.now();
        let mut redraw=false;
        if let Some(event)=event {
            match event.kind {
                BUTTON=>return 0,
                PAUSE=>paused=true,
                RESUME=>{
                    paused=false;offline=false;
                    waiting=None;confirming=None;
                    last_data=now;next_poll=now;
                    redraw=true;
                },
                CROWN if !paused && mode!=Mode::Categories => {
                    if now.wrapping_sub(last_turn)>300 {
                        send_page(api,event.delta>0);
                        last_turn=now;
                    }
                },
                TOUCH_DOWN if !paused=>{
                    down=Some((event.x as i32,event.y as i32));
                },
                TOUCH_UP if !paused=>{
                    let end_x=event.x as i32;
                    let end_y=event.y as i32;
                    if let Some((start_x,start_y))=down.take(){
                        let dx=end_x-start_x;
                        let dy=end_y-start_y;
                        if dx.abs()>=60 && dy.abs()<105 &&
                                (mode==Mode::Home||mode==Mode::Results) {
                            send_page(api,dx<0);
                        }else if dx.abs()<35 && dy.abs()<35 {
                            redraw=handle_tap(api,end_x,end_y,&mut mode,
                                    &home,&results,&mut selected,
                                    &mut waiting,&mut confirming,&mut confirmed_at,now);
                        }
                    }
                },
                _=>{}
            }
        }
        if paused {continue;}
        while let Some(message)=api.recv(&mut inbox){
            let payload=message.strip_prefix(b"ha2S")
                    .or_else(||message.strip_prefix(b"ha2A"));
            if let Some(data)=payload {
                if let Some(next)=Screen::parse(data){
                    if mode==Mode::Results {
                        if next!=results { results=next;redraw=true; }
                    }else{
                        if next!=home {
                            if next.page!=home.page {confirming=None;}
                            home=next;redraw=true;
                        }
                    }
                    if waiting.is_some() {waiting=None;redraw=true;}
                    if offline {offline=false;redraw=true;}
                    last_data=now;
                }
            }
        }
        if confirming.is_some() && now.wrapping_sub(confirmed_at)>=5000 {
            confirming=None;redraw=true;
        }
        if now.wrapping_sub(last_data)>=16000 && !offline {
            offline=true;waiting=None;confirming=None;redraw=true;
        }
        if now.wrapping_sub(next_poll)>=5000 || now==next_poll {
            if mode==Mode::Home {
                let _=api.send(b"ha2R");
            }
            next_poll=now;
        }
        if redraw {
            // Avoid erasing and presenting an identical frame for periodic snapshots.
            render(api,&home,&results,mode,selected,waiting,confirming,offline);
            last_render=now;
        }
    }
}
