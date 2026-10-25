# Design tokens Beep

> Généré par `packages/design-tokens/build.mjs`. Source : `tokens.json`. Utilisation : `@import "@beep/design-tokens/tokens.css"`.

## Règles
1. Les couleurs passent par des tokens **sémantiques** (`--color-bg-surface`). Les composants partagés consomment leurs tokens (`--button-height`) ; les dimensions propres aux illustrations restent dans leur CSS.
2. Les tokens **primitifs** ne servent qu'à définir la couche sémantique.
3. Clair et sombre sont conçus ensemble : le thème suit `prefers-color-scheme`, ou `data-theme="light|dark"` sur `<html>`.
4. Mobile-first : cibles tactiles ≥ `--touch-target-min` (44 px), corps de texte 16 px, échelle de titres fluide (`clamp`).
5. Breakpoints : 375 / 768 / 1024 / 1440 px (`min-width`, mobile d'abord).
6. Mouvement : `--duration-*` + `--easing-*`, neutralisé par `prefers-reduced-motion`.

## Primitifs
| Token | Valeur |
|---|---|
| `--color-ink-50` | `#F5F8FF` |
| `--color-ink-100` | `#EDF2FA` |
| `--color-ink-200` | `#D9E2F0` |
| `--color-ink-300` | `#B9C8DE` |
| `--color-ink-400` | `#8195B3` |
| `--color-ink-500` | `#5D708D` |
| `--color-ink-600` | `#4C607D` |
| `--color-ink-700` | `#354B6C` |
| `--color-ink-800` | `#253752` |
| `--color-ink-900` | `#192B46` |
| `--color-ink-950` | `#101D32` |
| `--color-green-100` | `#DCFCE7` |
| `--color-green-400` | `#4ADE80` |
| `--color-green-700` | `#15803D` |
| `--color-amber-100` | `#FEF3C7` |
| `--color-amber-400` | `#FBBF24` |
| `--color-amber-700` | `#B45309` |
| `--color-red-100` | `#FEE2E2` |
| `--color-red-400` | `#F87171` |
| `--color-red-600` | `#DC2626` |
| `--color-red-700` | `#B91C1C` |
| `--color-sky-100` | `#E0F2FE` |
| `--color-sky-400` | `#38BDF8` |
| `--color-sky-700` | `#0369A1` |
| `--color-white` | `#FFFFFF` |
| `--color-black` | `#000000` |
| `--color-cobalt-50` | `#EEF3FF` |
| `--color-cobalt-100` | `#E1EAFF` |
| `--color-cobalt-200` | `#C2D6FF` |
| `--color-cobalt-300` | `#A8CAFF` |
| `--color-cobalt-400` | `#7CABFF` |
| `--color-cobalt-500` | `#457BFF` |
| `--color-cobalt-600` | `#2457D6` |
| `--color-cobalt-700` | `#1C45AF` |
| `--color-cobalt-800` | `#183A8F` |
| `--color-cobalt-900` | `#192F5B` |
| `--color-navy-100` | `#E7F0FF` |
| `--color-navy-300` | `#B8D4FF` |
| `--color-navy-500` | `#4F6EA3` |
| `--color-navy-700` | `#233C69` |
| `--color-navy-800` | `#1B2F53` |
| `--color-navy-900` | `#12213B` |
| `--font-family-display` | `var(--font-display), 'Manrope', ui-sans-serif, system-ui, sans-serif` |
| `--font-family-body` | `var(--font-body), 'DM Sans', ui-sans-serif, system-ui, sans-serif` |
| `--font-family-mono` | `ui-monospace, 'SF Mono', Menlo, Consolas, monospace` |
| `--font-weight-regular` | `400` |
| `--font-weight-medium` | `500` |
| `--font-weight-semibold` | `600` |
| `--font-weight-bold` | `700` |
| `--size-12` | `0.75rem` |
| `--size-14` | `0.875rem` |
| `--size-16` | `1rem` |
| `--size-18` | `1.125rem` |
| `--size-20` | `1.25rem` |
| `--size-24` | `1.5rem` |
| `--size-30` | `1.875rem` |
| `--size-36` | `2.25rem` |
| `--size-48` | `3rem` |
| `--size-60` | `3.75rem` |
| `--space-0` | `0` |
| `--space-1` | `0.25rem` |
| `--space-2` | `0.5rem` |
| `--space-3` | `0.75rem` |
| `--space-4` | `1rem` |
| `--space-5` | `1.25rem` |
| `--space-6` | `1.5rem` |
| `--space-8` | `2rem` |
| `--space-10` | `2.5rem` |
| `--space-12` | `3rem` |
| `--space-16` | `4rem` |
| `--space-20` | `5rem` |
| `--space-24` | `6rem` |
| `--radius-sm` | `0.25rem` |
| `--radius-md` | `0.375rem` |
| `--radius-lg` | `0.5rem` |
| `--radius-xl` | `0.75rem` |
| `--radius-pill` | `999px` |
| `--duration-instant` | `80ms` |
| `--duration-fast` | `140ms` |
| `--duration-base` | `220ms` |
| `--duration-slow` | `360ms` |
| `--easing-standard` | `cubic-bezier(0.2, 0, 0, 1)` |
| `--easing-enter` | `cubic-bezier(0, 0, 0, 1)` |
| `--easing-exit` | `cubic-bezier(0.3, 0, 1, 1)` |
| `--z-base` | `0` |
| `--z-raised` | `10` |
| `--z-sticky` | `20` |
| `--z-drawer` | `40` |
| `--z-modal` | `100` |
| `--z-toast` | `1000` |

## Sémantique — clair
| Token | Valeur |
|---|---|
| `--color-bg-page` | `{color.white}` |
| `--color-bg-surface` | `{color.white}` |
| `--color-bg-subtle` | `{color.ink.100}` |
| `--color-bg-inverse` | `{color.navy.700}` |
| `--color-text-primary` | `{color.ink.900}` |
| `--color-text-secondary` | `{color.ink.600}` |
| `--color-text-muted` | `{color.ink.500}` |
| `--color-text-inverse` | `{color.ink.50}` |
| `--color-text-link` | `{color.cobalt.700}` |
| `--color-border-default` | `{color.ink.200}` |
| `--color-border-strong` | `{color.ink.400}` |
| `--color-action-primary-bg` | `{color.cobalt.600}` |
| `--color-action-primary-bg-hover` | `{color.cobalt.700}` |
| `--color-action-primary-bg-active` | `{color.cobalt.800}` |
| `--color-action-primary-text` | `{color.white}` |
| `--color-action-accent-bg` | `{color.navy.700}` |
| `--color-action-accent-bg-hover` | `{color.navy.800}` |
| `--color-action-accent-text` | `{color.white}` |
| `--color-brand-tint` | `{color.cobalt.50}` |
| `--color-brand-tint-strong` | `{color.cobalt.100}` |
| `--color-focus-ring` | `{color.cobalt.600}` |
| `--color-feedback-success-bg` | `{color.green.100}` |
| `--color-feedback-success-text` | `{color.green.700}` |
| `--color-feedback-warning-bg` | `{color.amber.100}` |
| `--color-feedback-warning-text` | `{color.amber.700}` |
| `--color-feedback-danger-bg` | `{color.red.100}` |
| `--color-feedback-danger-text` | `{color.red.700}` |
| `--color-feedback-info-bg` | `{color.sky.100}` |
| `--color-feedback-info-text` | `{color.sky.700}` |
| `--color-overlay-scrim` | `rgb(10 11 30 / 0.55)` |
| `--shadow-1` | `0 1px 2px rgb(10 11 30 / 0.06), 0 1px 1px rgb(10 11 30 / 0.04)` |
| `--shadow-2` | `0 4px 12px rgb(10 11 30 / 0.08), 0 1px 3px rgb(10 11 30 / 0.06)` |
| `--shadow-3` | `0 16px 40px rgb(10 11 30 / 0.14), 0 4px 10px rgb(10 11 30 / 0.06)` |
| `--color-surface-blue` | `{color.navy.100}` |
| `--color-surface-mist` | `{color.cobalt.100}` |
| `--color-brand-signature` | `{color.cobalt.600}` |
| `--color-on-blue` | `{color.navy.700}` |
| `--color-scene-navy` | `{color.navy.700}` |
| `--color-scene-deep` | `{color.navy.900}` |
| `--color-scene-paper` | `{color.ink.50}` |
| `--color-scene-mist` | `{color.navy.100}` |
| `--color-scene-ink` | `{color.ink.900}` |
| `--color-scene-muted` | `{color.ink.600}` |
| `--color-scene-electric` | `{color.cobalt.500}` |
| `--color-scene-sky` | `{color.cobalt.200}` |

## Sémantique — sombre
| Token | Valeur |
|---|---|
| `--color-bg-page` | `{color.ink.950}` |
| `--color-bg-surface` | `{color.ink.900}` |
| `--color-bg-subtle` | `{color.ink.800}` |
| `--color-bg-inverse` | `{color.ink.50}` |
| `--color-text-primary` | `{color.ink.50}` |
| `--color-text-secondary` | `{color.ink.300}` |
| `--color-text-muted` | `{color.ink.400}` |
| `--color-text-inverse` | `{color.ink.950}` |
| `--color-text-link` | `{color.cobalt.300}` |
| `--color-border-default` | `{color.ink.700}` |
| `--color-border-strong` | `{color.ink.400}` |
| `--color-action-primary-bg` | `{color.cobalt.300}` |
| `--color-action-primary-bg-hover` | `{color.cobalt.200}` |
| `--color-action-primary-bg-active` | `{color.cobalt.100}` |
| `--color-action-primary-text` | `{color.ink.950}` |
| `--color-action-accent-bg` | `{color.navy.300}` |
| `--color-action-accent-bg-hover` | `{color.navy.100}` |
| `--color-action-accent-text` | `{color.navy.900}` |
| `--color-brand-tint` | `{color.cobalt.900}` |
| `--color-brand-tint-strong` | `{color.cobalt.800}` |
| `--color-focus-ring` | `{color.cobalt.300}` |
| `--color-feedback-success-bg` | `rgb(22 163 74 / 0.18)` |
| `--color-feedback-success-text` | `{color.green.400}` |
| `--color-feedback-warning-bg` | `rgb(245 158 11 / 0.18)` |
| `--color-feedback-warning-text` | `{color.amber.400}` |
| `--color-feedback-danger-bg` | `rgb(220 38 38 / 0.2)` |
| `--color-feedback-danger-text` | `{color.red.400}` |
| `--color-feedback-info-bg` | `rgb(56 189 248 / 0.16)` |
| `--color-feedback-info-text` | `{color.sky.400}` |
| `--color-overlay-scrim` | `rgb(0 0 0 / 0.65)` |
| `--shadow-1` | `0 1px 2px rgb(0 0 0 / 0.5)` |
| `--shadow-2` | `0 4px 12px rgb(0 0 0 / 0.5)` |
| `--shadow-3` | `0 16px 40px rgb(0 0 0 / 0.6)` |
| `--color-surface-blue` | `{color.navy.800}` |
| `--color-surface-mist` | `{color.cobalt.900}` |
| `--color-brand-signature` | `{color.cobalt.300}` |
| `--color-on-blue` | `{color.navy.300}` |
| `--color-scene-navy` | `{color.navy.700}` |
| `--color-scene-deep` | `{color.navy.900}` |
| `--color-scene-paper` | `{color.ink.50}` |
| `--color-scene-mist` | `{color.navy.100}` |
| `--color-scene-ink` | `{color.ink.900}` |
| `--color-scene-muted` | `{color.ink.600}` |
| `--color-scene-electric` | `{color.cobalt.500}` |
| `--color-scene-sky` | `{color.cobalt.200}` |

## Sémantique — statique (typo, gabarits, tactile)
| Token | Valeur |
|---|---|
| `--text-display` | `clamp(2.75rem, 5.1vw, 4.5rem)` |
| `--text-h1` | `clamp(1.875rem, 6vw, 3rem)` |
| `--text-h2` | `clamp(2rem, 3.4vw, 3rem)` |
| `--text-h3` | `{size.20}` |
| `--text-body` | `{size.16}` |
| `--text-body-lg` | `{size.18}` |
| `--text-small` | `{size.14}` |
| `--text-caption` | `{size.12}` |
| `--leading-tight` | `1.1` |
| `--leading-snug` | `1.3` |
| `--leading-body` | `1.6` |
| `--tracking-display` | `-0.055em` |
| `--touch-target-min` | `2.75rem` |
| `--touch-target-comfortable` | `3rem` |
| `--gutter-page` | `1.375rem` |
| `--gutter-page-md` | `2.5rem` |
| `--container-narrow` | `32rem` |
| `--container-content` | `45rem` |
| `--container-wide` | `82.5rem` |
| `--focus-ring-width` | `3px` |
| `--focus-ring-offset` | `2px` |
| `--ease-standard` | `{easing.standard}` |
| `--ease-enter` | `{easing.enter}` |
| `--ease-exit` | `{easing.exit}` |

## Composants
| Token | Valeur |
|---|---|
| `--button-height` | `3.25rem` |
| `--button-height-sm` | `{semantic.touch-target-min}` |
| `--button-padding-x` | `{space.5}` |
| `--button-radius` | `{radius.sm}` |
| `--button-font-weight` | `{font.weight-semibold}` |
| `--button-press-scale` | `0.98` |
| `--input-height` | `3.25rem` |
| `--input-padding-x` | `{space.4}` |
| `--input-radius` | `{radius.md}` |
| `--input-font-size` | `{size.16}` |
| `--card-padding` | `{space.5}` |
| `--card-padding-lg` | `{space.8}` |
| `--card-radius` | `{radius.lg}` |
| `--nav-height` | `5.5rem` |
| `--nav-bottom-height` | `4rem` |
| `--badge-radius` | `{radius.pill}` |
| `--badge-padding-x` | `{space.3}` |
| `--badge-padding-y` | `{space.1}` |

## Breakpoints
| Token | Valeur |
|---|---|
| `--bp-sm` | `375px` |
| `--bp-md` | `768px` |
| `--bp-lg` | `1024px` |
| `--bp-xl` | `1440px` |
