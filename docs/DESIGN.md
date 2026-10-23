# Design notes — iOS 27 on Android

What Duo Glass takes from iOS 27 (shipped Sept 2026) and how each piece is
built in Compose. iOS 27 refines iOS 26's Liquid Glass rather than replacing it;
Apple published the direction but few numbers, so values below are from the
HIG where available and from Liquid Glass emulation kits otherwise.

## iOS 27 vs iOS 26, and what we did

| iOS 27 change | Here |
|---|---|
| Glass is calmer: more frosted by default, stronger contrast | `GlassStyle.resolve` defaults to the Regular midpoint (blur 10 dp, dark tint #4D4D52 @ 45%) |
| New Liquid Glass slider, Ultra Clear ↔ Fully Tinted | **Look → Appearance → Liquid Glass**; `LocalGlassAppearance` 0…1 interpolates clear → regular → tinted |
| Defined borders instead of drop shadows | 1 dp hairline on every glass shape (`edgeShade`) |
| No tilt-driven specular shimmer; light on top/bottom edges only | Static vertical-gradient rim stroke; the fold shader's edge light is static too |
| Selected tab darker than the bar in dark mode | Tab pill is black @ 28% in dark, black @ 6% in light |
| "Hard" scroll edge under bars | Top bar: frosted glass + a crisp 0.5 dp separator, fading in with scroll |
| Grouped toolbars, capsule controls, concentric corners | Capsule buttons/segments; sections 26 dp; hero 34 dp; sheet 38 dp |
| iPhone Duo: two-screen layouts, vertical tab bars | ≥ 600 dp: vertical glass tab bar, content split exactly at the crease (`DuoPanes`) |

## Material: Liquid Glass (`ui/glass/LiquidGlass.kt`)

1. `Modifier.backdropSource(b)` records content into a `GraphicsLayer`.
2. `Modifier.liquidGlass(b, shape, style)` (a `Modifier.Node`) re-records that
   layer offset to its own position, padded so blur and refraction can sample
   beyond its edge, then applies `RenderEffect` blur → AGSL lens:
   - signed distance to the rounded rect; inside a 14 dp rim band the sample
     point is pushed outward along the edge normal with a circular profile
     (thick-glass lensing), R and B offset slightly (dispersion);
   - saturation ×1.45 (vibrancy); press "energizes" it (brightness).
3. Tint fill, then the hairline and the top/bottom rim light.

Glass never samples a backdrop it is inside. Floating chrome samples the whole
screen; glass inside content (the hero's chips and button) samples the hero.
On a sheet, buttons are solid (tinted glass over a plain surface resolves to
that anyway).

| Appearance | Blur | Tint (dark) | Tint (light) | Refraction |
|---|---|---|---|---|
| Ultra Clear (0) | 4 dp | white 8% | white 6% | 0.8 |
| Default (0.5) | 10 dp | #4D4D52 45% | white 32% | 0.7 |
| Tinted (1) | 20 dp | #38383D 86% | #F7F7FA 86% | 0.6 |

## Type (`ui/theme/Theme.kt`)

SF Pro can't be bundled, so Inter (variable, OFL) stands in: optical size 14
for text, 32 for display, tracking tightened. Dynamic Type ramp at the default
size, pt → sp: Large Title 34/41 bold, Title 1 28/34, Title 2 22/28,
Title 3 20/25, Headline 17/22 semibold, Body 17/22, Callout 16/21,
Subhead 15/20, Footnote 13/18, Caption 1 12/16, Caption 2 11/13.

## Colour

HIG system colours (dark / light): blue #0091FF / #0088FF, green #30D158 /
#34C759, orange #FF9230 / #FF8D28, red #FF4245 / #FF383C, indigo #6D7CFF /
#6155F5, purple #DB34F2 / #CB30E0, teal #00D2E0 / #00C3D0. Labels at the UIKit
opacities (secondary 60%, tertiary 30%), grouped background #000 / #F2F2F7,
cells #1C1C1E / #FFF. Behind everything, a slow AGSL aurora (~30 fps) gives the
glass something to refract.

## Motion

Springs in the spirit of SwiftUI: smooth (ζ 1.0), snappy (ζ 0.86), bouncy
(ζ 0.68). Glass swells under a finger (buttons +5%, icon buttons +8%);
switch knob and slider thumb grow and turn to glass while dragged; tab and
segment pills spring between positions; the tab bar shrinks to the selected
tab while you scroll down. Haptics: segment ticks on selections and slider
detents, toggle on/off, confirm/reject.

## Symbols

`ui/components/Symbols.kt`: a small set drawn on a 24-unit grid with round
caps and joins at regular weight, outline + fill variants for tabs, in the
manner of SF Symbols.
