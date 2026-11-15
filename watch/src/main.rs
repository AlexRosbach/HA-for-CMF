#![no_std]
#![no_main]

use cmf_wp2_app::{Api, BUTTON, MSG_MAX, PAUSE, RESUME, TOUCH_DOWN};

use cmf_wp2_ui::{Rgb, draw, text};

cmf_wp2_app::entry!(main);

#[derive(Clone, Copy, PartialEq, Eq)]
enum State {
    Connecting,
    On,
    Off,
    Unavailable,
    Pending,
}

const BUTTON_X: i16 = 113;
const BUTTON_Y: i16 = 279;
const BUTTON_W: i16 = 240;
const BUTTON_H: i16 = 76;

fn draw_screen(mut api: &Api, state: State) {
    let background = Rgb(2, 6, 9);
    let icon_bg = Rgb(18, 30, 39);
    let cyan = Rgb(85, 217, 254);
    let muted = Rgb(164, 177, 190);
    let disabled = Rgb(23, 33, 42);
    let (status, action, button_text, button, outline, icon) = match state {
        State::Connecting => ("Connecting", "Please wait", muted, disabled, Rgb(41, 55, 70), cyan),
        State::On => ("On", "Turn off", Rgb(17, 32, 42), cyan, cyan, cyan),
        State::Off => ("Off", "Turn on", cyan, Rgb(17, 27, 36), Rgb(62, 82, 98), Rgb(62, 82, 98)),
        State::Pending => ("Updating", "Please wait", muted, disabled, Rgb(41, 55, 70), cyan),
        State::Unavailable => ("Offline", "Retrying...", muted, disabled, Rgb(41, 55, 70), Rgb(133, 149, 165)),
    };
    api.rect(0, 0, 466, 466, background.rgb565());
    text::centre(&mut api, 233, 86, "HOME ASSISTANT", muted.rgb565());
    draw::disc(&mut api, 233, 162, 41, icon_bg, background);
    draw::arc(&mut api, 233, 164, 18, 22, 57_000, 48_000, icon, None, icon_bg);
    api.rect(231, 135, 4, 24, icon.rgb565());
    let x = 233 - text::big_width(status, 4) / 2;
    text::big(&mut api, x, 218, status, 4, Rgb(242, 247, 250).rgb565());

    let (x, y, w, h) = (BUTTON_X as i32, BUTTON_Y as i32, BUTTON_W as i32, BUTTON_H as i32);
    draw::rrect(&mut api, x, y, w, h, 36, outline, background);
    draw::rrect(&mut api, x + 2, y + 2, w - 4, h - 4, 34, button, outline);
    text::centre(&mut api, 233, 309, action, button_text.rgb565());
    api.show();
}

fn main(api: &'static Api) -> i32 {
    let mut state = State::Connecting;
    let mut last_reply = api.now();
    let mut next_query = api.now();
    let mut paused = false;
    let mut buffer = [0; MSG_MAX];
    draw_screen(api, state);
    loop {
        let event = api.event(if paused { u32::MAX } else { 250 });
        let now = api.now();
        let previous = state;
        if let Some(event) = event {
            match event.kind {
                BUTTON => return 0,
                PAUSE => paused = true,
                RESUME => {
                    paused = false;
                    state = State::Connecting;
                    last_reply = now;
                    next_query = now;
                }
                TOUCH_DOWN if !paused && (BUTTON_X..BUTTON_X + BUTTON_W).contains(&event.x)
                    && (BUTTON_Y..BUTTON_Y + BUTTON_H).contains(&event.y) => {
                    let command = match state {
                        State::On => Some(b"ha1D"),
                        State::Off => Some(b"ha1U"),
                        _ => None,
                    };
                    if let Some(command) = command {
                        state = if api.send(command) == 0 { State::Pending } else { State::Unavailable };
                        last_reply = now;
                        next_query = now.wrapping_add(3000);
                    }
                }
                _ => {}
            }
        }
        if paused {
            continue;
        }
        while let Some(message) = api.recv(&mut buffer) {
            state = match message {
                b"ha1RON" | b"ha1ROFF" | b"ha1RERR" if state == State::Pending => continue,
                b"ha1RON" | b"ha1AON" => State::On,
                b"ha1ROFF" | b"ha1AOFF" => State::Off,
                b"ha1RERR" | b"ha1AERR" => State::Unavailable,
                _ => continue,
            };
            last_reply = now;
        }
        if now.wrapping_sub(last_reply) >= 12_000 {
            state = State::Unavailable;
        }
        if (now.wrapping_sub(next_query) as i32) >= 0 {
            api.send(b"ha1R");
            next_query = now.wrapping_add(3000);
        }
        if state != previous || event.is_some_and(|e| e.kind == RESUME) {
            draw_screen(api, state);
        }
    }
}
