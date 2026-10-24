# Beep — charte Atelier

Direction du 24 octobre 2026, inspirée du projet local Vendeer et adaptée au produit Beep. Cette charte remplace la palette violet/vert signal, les boutons en pilule et l’ancienne contrainte « zéro image ».

Source exécutable : [tokens.json](../packages/design-tokens/tokens.json). Inventaire généré : [TOKENS.md](../packages/design-tokens/TOKENS.md). Composants partagés : [packages/ui](../packages/ui). Décisions produit et recette : [refonte-atelier.md](refonte-atelier.md).

## Intention et argumentaire

« Un petit lien. De grandes possibilités. » Beep rapproche les idées, contenus et produits des personnes qui les découvrent.

Le discours commence par les situations de partage : conversation WhatsApp, bio de réseau social, emballage. Il explique ensuite trois bénéfices : une adresse reconnaissable et facile à transmettre ; moins d’étapes vers la bonne destination ; des données pour comprendre les canaux qui fonctionnent. Les noms techniques viennent après leur utilité.

La voix est chaleureuse, concrète et honnête sur l’avancement. Aucun taux de conversion, témoignage, nombre de clients ou résultat commercial fictif. Les photos et aperçus mettent en scène un atelier fictif, pas une cliente de Beep.

**Disponibilité :** en phase 0, compte, vérification d’e-mail, connexion, récupération de mot de passe et TOTP. Liens, deeplinks, Onelink, QR codes, pages bio et analytics restent en préparation. Les visuels portent une légende d’aperçu ; les cartes affichent « Bientôt » ; la FAQ précise le périmètre. Ne pas transformer une maquette en commande qui semble fonctionnelle.

## Palette et thèmes

| Usage | Clair | Sombre | Token |
| --- | --- | --- | --- |
| Fond de page | Ivoire `#F8F7F2` | Encre `#18231C` | `--color-bg-page` |
| Surface | Blanc `#FFFFFF` | Encre `#222B25` | `--color-bg-surface` |
| Texte principal | Encre `#222B25` | Ivoire `#F8F7F2` | `--color-text-primary` |
| Texte secondaire | Olive `#58634F` | Sauge `#B7C2B4` | `--color-text-secondary` |
| Action principale | Terre cuite `#BD3516` | Pêche `#FFAD86` | `--color-action-primary-bg` |
| Action au survol | `#9F2D12` | `#F9B99B` | `--color-action-primary-bg-hover` |
| Signature typographique | `#BD3516` | `#FFAD86` | `--color-brand-signature` |
| Action secondaire forte | Forêt `#243D30` | Sauge `#BCD0BA` | `--color-action-accent-bg` |
| Surface sauge | `#E8EDE3` | `#1C3026` | `--color-surface-sage` |
| Surface terre claire | `#F5E8DD` | `#4B271E` | `--color-surface-warm` |
| Bordure courante | `#DCDED5` | `#3E5143` | `--color-border-default` |

L’orange vif `#E94B23` reste réservé aux détails illustratifs. L’action et les titres orange emploient une terre cuite plus sombre, pour leur lisibilité. Ne pas confondre palette décorative et couleurs de retour d’état : erreurs, avertissements et succès gardent leurs tokens et un libellé explicite.

Les scènes illustrées, le pied de page et la navigation latérale staff conservent un fond forêt avec texte ivoire. Les tokens `color-scene-*` sont stables entre les thèmes pour que photos, papier et textes gardent leur contraste. Les surfaces du produit suivent `prefers-color-scheme`, ou `data-theme="light|dark"` sur `html`.

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

Le signe Beep est un **b ouvert avec une impulsion**, accompagné du mot « beep. ». Il reprend l’idée de connexion du produit et conserve une identité distincte du V de Vendeer. Le composant [Logo.tsx](../packages/ui/src/Logo.tsx) hérite des couleurs sémantiques ; les favicons des deux applications reprennent le même signe.

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

`Button` : primary, accent forêt, secondary et ghost, état de chargement. `TextField` : label, hint et erreur reliée. `Alert`, `Badge`, `Icon`, `Logo`. Utilitaires : `bp-container`, `bp-stack`, `bp-row`, `bp-card`, `bp-sr-only`, `bp-skip-link`, `bp-tabular`.

`SiteChrome` porte la navigation et le pied de page. `ProductPreview` porte la scène illustrative. `AuthShell` sert tous les écrans d’authentification. L’espace client possède sa propre composition : compte, sécurité et outils à venir. Le back office utilise `Shell` avec la même identité.

Les textes d’authentification restent dans `lib/dictionaries/fr.ts` et `en.ts`. Les nouveaux textes éditoriaux sont regroupés dans `lib/experience.ts`, avec parité FR/EN. Toute nouvelle promesse doit être alignée avec la phase réellement livrée.

```bash
npm run tokens       # régénère tokens.css et TOKENS.md
npm run contrast     # 34 paires vérifiées, clair et sombre
npm run lint         # TypeScript des deux applications
npm run build:web
npm run build:bo
```

Ne jamais éditer les exports générés à la main. Les contrôles de contraste couvrent textes, boutons, bordures, focus et principales surfaces éditoriales. Conserver aussi la vérification du rendu et du clavier.

