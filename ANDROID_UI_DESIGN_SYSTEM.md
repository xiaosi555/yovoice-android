# Yovoice Android UI Design System

This document is the implementation reference for the Android app. It adapts official Android mobile design guidance and Material Design principles to Yovoice's native Views implementation.

## Reference documents

- Android mobile design and UI kit: https://developer.android.com/design/ui/mobile
- Material Design on Android Views: https://developer.android.com/develop/ui/views/theming/look-and-feel
- Android adaptive icons: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- Android core app quality: https://developer.android.com/docs/quality-guidelines/core-app-quality
- Android motion guidance: https://developer.android.com/design/ui/mobile/guides/foundations/translate-designs

## Visual principles

1. Minimal, quiet hierarchy. Use typography, spacing and alignment before adding containers.
2. One surface per semantic group. Avoid cards nested inside cards and avoid decorative borders around every control.
3. Use a consistent 4 dp spacing base; common gaps are 8, 12, 16, 20 and 24 dp.
4. Interactive targets are at least 48 dp in both dimensions. Icons are vector/line artwork, optically centered, normally 20–24 dp.
5. Keep labels short. Use supporting text only when it explains model capability, state, or a required input.
6. Use the same layout and hierarchy in light and dark themes. Derive foreground, surface and stroke colors from the active palette.
7. Use responsive grids: two columns on narrow compact windows and three columns when width permits.
8. Model rows place primary model information on the left and icon-only download / use actions on the right. Every icon has an accessibility description.

## Type and layout tokens

| Token | Value |
| --- | --- |
| Page horizontal inset | 20 dp |
| Compact row inset | 12–16 dp |
| Small control gap | 8 dp |
| Section gap | 20–24 dp |
| Main title | 26–28 sp, medium/semibold |
| Body | 14–16 sp |
| Supporting text | 11–13 sp |
| Icon drawing area | 20–24 dp |
| Minimum touch target | 48 dp |
| Standard corner radius | 12–16 dp |

## Model lifecycle and affordances

A model must expose distinct states: not downloaded, queued, downloading, paused, downloaded and hash-verified, runtime family supported, and runtime unavailable. Downloading, verification, and runtime availability are not interchangeable.

- Download action: download icon; while active it becomes pause; failed or paused becomes retry/download; verified becomes check.
- Use action: play icon. It is enabled only after the package is verified and the family is present in the native runtime registry.
- Selecting Use stores the model for every mapped feature and opens the corresponding creation page.
- A compiled family is not a claim that a particular device has passed real inference. Keep that distinction visible until a real task succeeds.

## Motion and interaction

- Use Android ripple feedback for standard clickable surfaces.
- Use a brief press scale (0.97–0.93) and restore over roughly 100 ms.
- Use a short 180 ms fade for page content transitions.
- Avoid decorative continuous motion, bouncing, or animations that delay task completion.
- Respect system animation scale and accessibility settings where possible.

## Launcher icon

Use an adaptive icon with foreground, background and monochrome layers. The foreground mark is a simple five-bar audio waveform. All layers use a 108 dp viewport; keep the visible mark inside the central 66 dp safe area. The monochrome layer supports themed launcher icons on compatible Android versions.
