# Refonte Beep — direction Atelier

24 octobre 2026. Périmètre : site public FR/EN, inscription et connexion, vérification d’adresse, récupération de mot de passe, espace client, connexion équipe et liste des utilisateurs du back office.

## Pourquoi cette direction

La référence demandée est le projet Vendeer : ivoire, encre végétale, orange terre cuite, vert forêt, Manrope/DM Sans, photos de matières et de travail, surfaces sobres et composition éditoriale. Beep reprend ce vocabulaire visuel avec son propre signe et un argumentaire lié au partage de liens.

L’ancienne interface exposait surtout un catalogue de fonctionnalités. La nouvelle commence par le problème rencontré : une adresse trop longue à partager, des détours avant de trouver le bon contenu, un manque de visibilité sur les clics. Elle relie ensuite chaque capacité future de Beep à son bénéfice.

| Décision | Effet recherché |
| --- | --- |
| Accroche « Un petit lien. De grandes possibilités. » | Exprimer le produit et ouvrir sur les usages |
| WhatsApp, bio sociale, emballage | Rendre le partage concret pour une audience mobile |
| Trois bénéfices avant les six outils | Expliquer l’intérêt avant le vocabulaire technique |
| Maquette de page bio avec photo d’atelier | Montrer un résultat possible sans simuler de fonction disponible |
| Palette chaleureuse, rayons discrets | Donner une personnalité commune au site et aux interfaces |
| Formulaire seul sur téléphone | Faciliter l’accès au compte sans détour décoratif |
| Compte et sécurité au premier plan dans l’espace client | Mettre en avant ce qui fonctionne aujourd’hui |
| Cartes de comptes sur mobile | Lire les e-mails et statuts sans déplacer un tableau horizontalement |
| FAQ sur l’avancement et la sécurité | Répondre aux objections avant l’inscription |

## Ce que cette refonte livre

- Nouvelle identité partagée : palette clair/sombre, logo, favicons, polices locales, boutons, champs et cartes.
- Accueil recomposé : accroche, bénéfices Beep, audience, six aperçus de fonctions, scénario en trois gestes, FAQ et invitation à créer un compte.
- Authentification à deux panneaux sur bureau, formulaire prioritaire sur mobile.
- Espace client séparé du gabarit de connexion : état du compte, TOTP et catalogue des outils à venir.
- Back office cohérent : connexion équipe illustrée, navigation forêt, liste adaptative et état vide.
- Photographies locales optimisées, légendes d’aperçu et illustrations statiques.
- Routage des médias dans les deux configurations Caddy et copie des médias dans l’image Docker.
- [Charte](design-system.md), [provenance des ressources](assets.md), tokens et documentation de projet actualisés.

Aucun ajout de fonctionnalité de raccourcissement, redirection intelligente, QR, bio ou statistiques : leur réalisation reste dans les phases existantes de la feuille de route.

## Recette du rendu

Les contrôles navigateur utilisent les builds de production Next.js, Google Chrome en mode headless et les largeurs **320, 375, 390, 768, 1024 et 1440 px**. Le [rapport JSON](previews/verification.json) contient les résultats.

Contrôles : statut HTTP, titre principal unique, absence de débordement, accès inscription via le CTA mobile, ouverture de FAQ, affichage du mot de passe, retour de confirmation, apparition du champ TOTP, redirection sans session, rendu serveur de l’espace client et des utilisateurs, e-mails longs, thème sombre, mouvement réduit, lien d’évitement et erreurs JavaScript.

Les sessions et réponses de formulaires de cette recette utilisent un serveur de données fictives et des interceptions réseau locales. Elles vérifient les interfaces et leurs états ; elles ne constituent pas une nouvelle recette de sécurité des API Java ou des e-mails réels. Les tests existants `make test-e2e` restent la référence pour la stack complète.

Les builds des deux fronts, le typage et les **34 paires de contrastes** passent. La revue visuelle a conduit à donner un fond opaque à la légende de la maquette pour préserver sa lisibilité. Les essais sur téléphones physiques et les mesures Lighthouse/4G restent à effectuer.

## Aperçus

- [Accueil bureau](previews/accueil-desktop.png)
- [Accueil mobile](previews/accueil-mobile.png)
- [Premier écran mobile](previews/accueil-mobile-premier-ecran.png)
- [Inscription mobile](previews/inscription-mobile.png)
- [Connexion bureau](previews/connexion-desktop.png)
- [Espace client](previews/espace-client-desktop.png)
- [Connexion équipe](previews/equipe-connexion-desktop.png)
- [Utilisateurs sur mobile](previews/equipe-utilisateurs-390.png)
- [Utilisateurs sur bureau](previews/equipe-utilisateurs-1440.png)
- [Thème sombre mobile](previews/accueil-sombre-mobile.png)

## Méthode

Le skill demandé **ui-ux-pro-max** a servi de grille de décision : contraste, cibles tactiles, priorité de contenu, labels de formulaires, clavier, dimensions de médias et mouvement réduit. Son moteur de recherche Python n’a pas été exécuté, Python n’étant pas disponible ; aucune recommandation issue de ce moteur n’est revendiquée. La référence Vendeer et les contraintes réelles de Beep ont guidé la palette et la composition.

Les valeurs communes se maintiennent dans les tokens, les compositions dans les feuilles CSS de chaque application et les textes éditoriaux FR/EN dans `beep/web/lib/experience.ts`. Les sources de Vendeer n’ont pas été modifiées.

