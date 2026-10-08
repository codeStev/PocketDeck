# PocketDesign

A small Compose design system: tokens, a theme and a library of components with a clean, friendly, art-first look
(rounded type, one warm accent, categorical tints, a floating pill navigation). It knows nothing about any app; an app
brings only a brand.

```kotlin
PocketDesignTheme(brand = SignalBrand.brand, darkTheme = isSystemInDarkTheme()) {
    PdButton("Import code", onClick = ::import, modifier = Modifier.fillMaxWidth())
}
```

## What is in it
- **Tokens** (`theme/`): `PdColors` (semantic colours plus brand tints), `PdTypography` (Fredoka for titles, Nunito for
  text), `PdShapes`, `PdSpacing`, `PdMotion`. Read them through `PocketDesign.colors`, `PocketDesign.typography`, ...
- **Brand** (`brand/`): `SignalBrand` is the reference brand. Another project supplies its own `PdBrand`.
- **Components** (`components/`): `PdButton`, `PdIconButton`, `PdStepper`, `PdChip`, `PdTopBar`, `PdFloatingNav`,
  `PdSegmentedTabs`, `PdSectionHeader`, `PdCard`, `PdTintedCard`, `PdArtTile`, `PdDialog`, `PdFullScreenPanel`,
  `PdBanner`, `PdEmptyState`, `PdProgressMeter`, `PdMeterTable`, `PdTextField`, `PdSwitch`, `PdRowGroup`, `PdInfoCard`.
- **Icons** (`icons/PdIcons`): a curated set of rounded outline glyphs, tinted by the component that shows them.
- **Catalog** (debug builds): `CatalogActivity` shows everything in light and dark.

## Rules
- Components take text and content descriptions as parameters; there are no strings in the library.
- No literal colours: only theme tokens. Text pairs hold 4.5:1 and graphics 3:1 in both themes (a unit test enforces it).
- Touch targets are 48dp; every interactive component has a pressed scale and a visible focus ring.
- Keep it free of any app code: the project's architecture test fails if this module imports `dev.pocketdeck`.

## Licences
Fredoka and Nunito are bundled under the SIL Open Font License 1.1 (`licenses/`).
