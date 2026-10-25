# Beep — charte Signal

Direction Signal : thème bleu sur fond blanc, actualisé le 25 octobre 2026 à la demande du porteur du projet. Bleu encre, cobalt et brume structurent les accents et les cartes. La composition et la priorité mobile sont conservées.

Source exécutable : [tokens.json](../packages/design-tokens/tokens.json). Inventaire généré : [TOKENS.md](../packages/design-tokens/TOKENS.md). Composants partagés : [packages/ui](../packages/ui). Décisions produit et recette : [refonte-atelier.md](refonte-atelier.md).

## Intention et argumentaire

« Un petit lien. De grandes possibilités. » Beep rapproche les idées, contenus et produits des personnes qui les découvrent.

Le discours commence par les situations de partage : conversation WhatsApp, bio de réseau social, emballage. Il explique ensuite trois bénéfices : une adresse reconnaissable et facile à transmettre ; moins d’étapes vers la bonne destination ; des données pour comprendre les canaux qui fonctionnent. Les noms techniques viennent après leur utilité.

La voix est chaleureuse, concrète et honnête sur l’avancement. Aucun taux de conversion, témoignage, nombre de clients ou résultat commercial fictif. Les photos et aperçus mettent en scène un atelier fictif, pas une cliente de Beep.

**Disponibilité :** l’état fonctionnel suit la [phase 1](phase-1.md) et la [phase 2 en cours](phase-2.md). Liens courts, QR codes, pages bio et statistiques de base font partie de la bêta sur invitation. Deeplinks et Onelink restent des évolutions prévues. Les maquettes marketing restent des illustrations, sans données clients réelles. Les mentions de disponibilité doivent suivre ces documents.

## Palette et thèmes

| Usage | Clair | Sombre | Token |
| --- | --- | --- | --- |
| Fond de page | Blanc #FFFFFF | Encre #101D32 | --color-bg-page |
| Surface | Blanc #FFFFFF | Encre #192B46 | --color-bg-surface |
| Texte principal | Encre #192B46 | Glacier #F5F8FF | --color-text-primary |
| Texte secondaire | Bleu ardoise #4C607D | Bleu brume #B9C8DE | --color-text-secondary |
| Action principale | Cobalt #2457D6 | Bleu ciel #A8CAFF | --color-action-primary-bg |
| Texte sur action | Blanc #FFFFFF | Encre #101D32 | --color-action-primary-text |
| Action au survol | #1C45AF | #C2D6FF | --color-action-primary-bg-hover |
| Signature typographique | #2457D6 | #A8CAFF | --color-brand-signature |
| Action secondaire forte | Bleu nuit #233C69 | Bleu brume #B8D4FF | --color-action-accent-bg |
| Surface bleu brume | #E7F0FF | #1B2F53 | --color-surface-blue |
| Surface bleutée | #E1EAFF | #192F5B | --color-surface-mist |
| Bordure courante | #D9E2F0 | #354B6C | --color-border-default |

Le bleu électrique #457BFF sert aux détails illustratifs. Les actions et titres utilisent le cobalt plus sombre #2457D6 pour leur lisibilité. Les boutons principaux ont un texte blanc en clair et encre en sombre.

Les illustrations, le pied de page et la navigation staff utilisent un fond bleu nuit avec texte glacier. Les tokens color-scene-* conservent leurs paires de contraste entre les thèmes. L’interface suit prefers-color-scheme ou data-theme="light|dark". Les couleurs de retour d’état gardent un texte ou une icône explicite.

## Typographie, formes et rythme

| Rôle | Choix |
| --- | --- |
| Titres et logotype | Manrope variable 200–800, titres généralement 600 |
| Texte et interface | DM Sans variable 100–1000, corps 16 px, interligne 1,6 |
| Secrets et code | Monospace système |
| Accroche d’accueil | `clamp(2.75rem, 5.1vw, 4.5rem)`, interligne 1,06 |
| Titre de section | `clamp(2rem, 3.4vw, 3rem)` |
| Bouton principal / champ | 52 px de hauteur minimale |
| Cible compacte | 44 px minimum |
| Rayons | 4 / 6 / 8 / 12 px ; cercles pour avatars et symboles |
| Grille d’espacement | Base 4 px, respiration accrue entre sections |
| Conteneur large | 82,5 rem, gouttière 22 px mobile et 40 px tablette/bureau |

Les polices sont locales, dans `packages/design-tokens/assets/fonts`, avec licences SIL OFL. Les deux layouts utilisent `next/font/local` avec `display: swap` : aucun appel Google Fonts au build ou à la navigation. Pas d’italique.

Les titres se composent en casse naturelle, avec approche resserrée. Les sous-titres et paragraphes expliquent une action ou un bénéfice, sans sur-titres promotionnels décoratifs. Les surfaces restent mates, les ombres réservées aux éléments de la maquette. Pas de flou ni de bibliothèque d’animation.

## Logo, illustrations et médias

Le signe Beep est un **point émetteur entouré de deux paires d’ondes**, accompagné du mot « beep. ». Il reprend l’idée de connexion du produit et conserve une identité distincte du V de Vendeer. Le composant [Logo.tsx](../packages/ui/src/Logo.tsx) hérite des couleurs sémantiques ; les favicons des deux applications reprennent le même signe.

Une seule famille d’icônes SVG à trait cohérent, dans [Icon.tsx](../packages/ui/src/Icon.tsx). Les icônes décoratives sont masquées aux lecteurs d’écran. Les commandes qui n’affichent qu’une icône ont un nom accessible.

La scène d’accueil assemble une page bio fictive « Atelier Naya », une adresse `beep.example`, des destinations mobile/web, des orbites et un soleil. C’est une illustration HTML/CSS/SVG statique, sans données ni contrôles factices. Le QR est explicitement non scannable. L’histogramme ne porte aucune valeur de performance réelle.

Deux images locales WebP : [provenance et dimensions](assets.md). Ne pas ajouter de dépendance à une URL d’image distante. Utiliser `next/image`, dimensions explicites et `sizes`. Image de l’aperçu prioritaire sur l’accueil ; photo de section et illustration d’authentification chargées de façon différée. Le panneau illustré d’authentification est masqué sous 1024 px.

## Règles mobiles

1. Concevoir dès **320 px** ; vérifier 375 et 390 px. Pas de défilement horizontal.
2. Sous 768 px : accueil en une colonne, cartes fonctionnalités empilées, audience sur deux colonnes et CTA inférieur fixe.
3. Réserver la hauteur du CTA dans le document et appliquer `env(safe-area-inset-bottom)`. Le clavier et les liens d’évitement restent utilisables.
4. Champs et corps de texte à **16 px**, boutons à **52 px**, cibles compactes au moins **44 px**. Le mot de passe a une commande visible pour afficher/masquer.
5. L’authentification mobile montre d’abord le formulaire. Le grand visuel ne doit pas repousser la tâche sous la ligne de flottaison.
6. Back office : cartes sous 768 px, tableau à partir de 768 px ; navigation inférieure avant 1024 px, latérale au-delà. Les adresses e-mail longues reviennent à la ligne.
7. Dès 768 px : grille de fonctionnalités à deux colonnes ; dès 1024 px : trois colonnes et accroche/illustration côte à côte.
8. FAQ native `details/summary` : accessible au clavier et au toucher, sans JavaScript supplémentaire.
9. Les animations et le défilement doux respectent `prefers-reduced-motion`.

L’objectif LCP < 2 s en 4G des futures pages bio reste une exigence à mesurer, pas un résultat annoncé pour cette refonte. Les contrôles locaux de rendu ne remplacent pas Lighthouse ni des essais sur téléphone réel.

## Composants et maintenance

Trois couches : primitives → sémantique clair/sombre/statique → composants. Les couleurs des composants passent par les alias sémantiques. Les tailles partagées (boutons, champs, cartes) viennent des tokens ; les géométries spécifiques à une illustration peuvent rester dans sa feuille CSS.

`Button` : primary cobalt, accent bleu nuit, secondary et ghost, état de chargement. `TextField` : label, hint et erreur reliée. `Alert`, `Badge`, `Icon`, `Logo`. Utilitaires : `bp-container`, `bp-stack`, `bp-row`, `bp-card`, `bp-sr-only`, `bp-skip-link`, `bp-tabular`.

Les thèmes personnalisables des pages bio (Ivoire, Forêt, Nuit, Soleil) sont des choix de contenu indépendants de l’identité de la plateforme. Leur palette et leurs identifiants persistent ; changer la marque Beep ne recolore pas les pages de leurs propriétaires.

`SiteChrome` porte la navigation et le pied de page. `ProductPreview` porte la scène illustrative. `AuthShell` sert les écrans d’authentification. `AppShell` encadre l’espace client et ses écrans de gestion ; les paramètres donnent accès à la sécurité. Le back office utilise `Shell` avec la même identité. Les nouveaux écrans réutilisent les tokens sémantiques Signal.

Les textes d’authentification restent dans `lib/dictionaries/fr.ts` et `en.ts`. Les nouveaux textes éditoriaux sont regroupés dans `lib/experience.ts`, avec parité FR/EN. Toute nouvelle promesse doit être alignée avec la phase réellement livrée.

```bash
npm run tokens       # régénère tokens.css et TOKENS.md
npm run contrast     # 34 paires vérifiées, clair et sombre
npm run lint         # TypeScript des deux applications
npm run build:web
npm run build:bo
```

Ne jamais éditer les exports générés à la main. Les contrôles de contraste couvrent textes, boutons, bordures, focus et principales surfaces éditoriales. Conserver aussi la vérification du rendu et du clavier.

