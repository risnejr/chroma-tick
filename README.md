# ChromaTick

Cycles your true tile color each game tick for preattentive tick tracking in OSRS.

Each tick the tile changes color, letting you track your weapon's attack cycle without counting. The color difference triggers preattentive processing — you feel the beat rather than consciously counting it.

## Features

- **Preattentive color palettes** — perceptually-distinct palettes for 2–10 tick cycles, optimized so each color pops out instantly
- **Custom colors** — disable the built-in palette and define up to 10 colors of your own
- **Static mode** — toggle to a single solid color (with its own configurable fill) via hotkey; tile stays visible
- **Draw below player** — GPU rendering mode option that punches the player model out of the overlay so the tile appears behind them
- **In-session cycle hotkeys** — bind keys to instantly switch between 2–10 tick cycles mid-fight without touching the settings panel

## Usage

1. Enable the plugin. Your true tile will start cycling colors every tick.
2. Set **Tick Cycle Length** to match your weapon (e.g. 4 for most slash/stab, 6 for halberd/chinchompa).
3. Optionally bind cycle hotkeys (e.g. `4-Tick Cycle`, `6-Tick Cycle`) so you can swap mid-session.
4. Use the **Toggle Static / Cycle** hotkey to freeze the tile on a solid color when you don't need the cycle.

## Credits

- Inspired by [MarlGames' OSRS brainhack video](https://www.youtube.com/watch?v=4fc4eIUmj6U)
- Adapted from [vincent0955's Visual Metronome](https://github.com/vincent0955/Visual-metronome)
- Draw-below-player technique from [LeikvollE's Improved Tile Indicators](https://github.com/LeikvollE/tileindicators)
