# Ressources visuelles Beep Signal

Les ressources utilisées par Beep sont copiées dans ce dépôt. Le projet Vendeer n’est pas nécessaire pour construire ou servir les applications.

## Photographies

| Fichier livré | Dimensions | Poids | Provenance / rôle |
| --- | --- | --- | --- |
| `beep/web/public/assets/atelier.webp` | 540 × 700 | 34 784 octets | Copie optimisée de `Vendeer/front/public/images/auth-atelier-v1.webp`. Scène fictive générée pour Vendeer, identifiée comme telle dans sa documentation `docs/DESIGN_AUTHENTIFICATION.md`. Photo de la maquette « Atelier Naya ». |
| `beep/web/public/assets/workspace.webp` | 900 × 1 000 | 51 300 octets | Copie optimisée de `Vendeer/front/public/images/workspace.jpg`, attribuée à [cette photographie Unsplash](https://images.unsplash.com/photo-1497215728101-856f4ea42174) dans la charte de Vendeer. Illustration du contexte de travail. |

Les noms, la page bio et les liens montrés dans les maquettes sont fictifs. Aucune photographie ne constitue un témoignage ou une preuve d’utilisation de Beep.

Les images passent par `next/image`. La route `/assets/*` est dirigée vers le front par Caddy en développement et en production ; la route `/_next/*` sert notamment les versions optimisées. Le Dockerfile du site copie `public` dans l’image finale.

## Polices

`packages/design-tokens/assets/fonts/manrope-latin.woff2` et `dm-sans-latin.woff2` proviennent des ressources locales de Vendeer. Leurs licences SIL Open Font License sont conservées dans `assets/fonts/licenses/`.

Les applications les chargent via `next/font/local`. Elles sont compilées dans les assets Next.js, sans route publique de police supplémentaire et sans fournisseur externe.

## Graphismes

Le signe Beep et les favicons sont des SVG. Les scènes et miniatures sont dessinées en HTML/CSS/SVG : soleil, orbites, téléphone, routage, QR illustratif, page bio et histogramme. La maquette QR n’est pas destinée à être scannée. Les icônes sont centralisées dans `packages/ui/src/Icon.tsx`.

