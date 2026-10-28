293 is civic brutalism in fog. The structure comes from public concrete buildings: terraces that step out floor by floor and one long curved facade. The colour comes from a memorial seen through fog: grey ground, a black silhouette for everything you can act on, and one faded red for what is live right now.

## Principles

- **Say it once, say it big.** No kicker lines above titles, no subtitles under rows, no footnotes. If a fact matters (a ticket number, a window), it gets a Window plate or a `figure`. If it does not change what the person does next, it is cut.
- **Terraces, not shadows.** Cards and primary buttons sit on a `ledge` that steps out `ledge-step` (8) below them. Pressing a terrace drops it 4px onto its ledge. No blur anywhere.
- **The silhouette acts.** `action` (near-black in the light themes, near-white in Dark) is the only action colour: primary button, selected chip or time, current step, active tab, switch-on. If it is black and filled, it can be tapped.
- **One red.** `banner` marks the single live thing on a screen: "now serving", an unread count, an expiry, an error.
- **One sweep per screen.** `radius-sweep` (120) on the bottom-right of the key card. Everything else is square, except small discs (step markers, status dots, the switch knob, the FAB).
- **Information lives where it is used.** Window numbers and wait times appear on the queue and booking screens for that window, not in the list that leads there.

## Content

- Counter voice: precise and short. Give the next step and how long it takes. "6 ahead of you, about 20 minutes."
- Titles, labels, buttons and nav are uppercase Jost with wide tracking. Body is sentence case.
- Rows are one line: a title and, at most, one trailing value (DONE, ~20 MIN, NOV 10).
- No emoji, no exclamation marks.

## Themes

- **Light** (default): fog grey. **Dark**: night. **Light yellow**: aged ochre plaster. **Light pink**: dusty pink panel.
- Every theme keeps the same roles and the same rules: one action colour (near-black, near-white in Dark), one faded red, grey-tinted terraces. Only the ground hue changes. In Light pink the red still differs from the ground by lightness, and it is always paired with a dot and a word.

## Colour

- `fog` is the ground; `fog-raised` is every terrace face (cards, inputs, secondary buttons, the bar); `ledge` is the underside, pressed and full states.
- Text is `ink` on `fog`, `fog-raised` or `ledge`; `ink-muted` is for labels above figures, availability and unselected tabs.
- `action` with `on-action`. The key card gets a `stroke-trim` (4) `action` edge along its top; the active tab gets the same edge.
- `banner` with `on-banner`; `banner` also works as text and as a 2px outline for destructive buttons.
- `rib` draws row rules, control outlines and the divider inside a Window plate.
- Focus: 3px solid `action`, offset 2px.

## Type

- Jost only. `display` for the screen title, `figure` for the number the screen exists for, `time` for times and dates, `headline` for sections, `title` for rows, `body` for one sentence of explanation, `label` and `label-small` for everything uppercase.

## Signature parts

- **Window plate:** the ticket `figure` on the left, a `plate-width` (88) plate on the right with a 2px `rib` divider, each under a `label`. Use it wherever a destination matters: queue, booking, the key card.
- **Timetable:** pick a time from a list of `row-height` (56) rows, time in `time` style on the left and availability on the right. The selected row fills `action`; full rows fill `ledge` with the time struck through. Never a grid of time buttons.
- **Inline control:** a switch that affects one block sits inside that block, above a 1px `rib` rule. Only app-wide settings go on the settings screen.
- **Chips:** for 2 to 4 short options (days, categories). Five or more options become a list or a Timetable.

## Layout

- Primary actions follow the content directly. Pin a button to the bottom only when content scrolls under it; never leave a gap between the last field and the button.
- The bottom bar is `bar-height` (64) plus the gesture inset: icon and one `label-small` line.

## States

- Pressed: the terrace drops 4px onto its ledge. Disabled and full: `ledge` fill, `ink-muted` text, no ledge.
- Error: `banner` outline, a red disc `!`, one sentence with the fix.
- Live status: a 10px `banner` dot followed by an uppercase label.

## Motion

- 120ms ease-out. Terraces press down; nothing slides sideways or bounces.

## Iconography

- Stroke icons, 24 grid, 2 stroke, square caps, `currentColor`. Icon-only buttons for universal actions only.

## Mobile rules

- Every tappable thing has a hit area of at least `target-min` (48).
- Side margin `space-4` (16); content inside safe areas, ground colour edge to edge.
- One primary action per screen. Destructive actions never take the primary style.
- Body 16; smallest text `label-small` (11), labels only. Text follows dynamic type; rows wrap at the largest sizes.
- 3 to 5 destinations in the bottom bar.

## Platform mapping

- **Compose:** `fog` to `background`, `fog-raised` to `surface`, `action` to `primary`, `on-action` to `onPrimary`, `banner` to `error` and badges. Ledge: a `ledge` rectangle 8.dp below the card in a `Box`. Sweep: `RoundedCornerShape(bottomEnd = 120.dp)`. `NavigationBar` height 64.dp.
- **SwiftUI:** ledge as `.background(Rectangle().fill(ledge).offset(y: 8))`; sweep as `UnevenRoundedRectangle(bottomTrailingRadius: 120)`.
