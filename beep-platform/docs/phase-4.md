# Phase 4 — Équipes et écosystème

Développement livré le 25 octobre 2026. La recette utilise la stack Docker locale, des comptes de test et Mailpit. Elle ne constitue pas un déploiement en production ni une validation des comptes fournisseurs réels.

## Équipes et organisation

- Un utilisateur possède un workspace personnel stable et peut créer jusqu’à dix workspaces au total, puis participer à d’autres équipes. Le choix est propre à la requête (cookie `HttpOnly`, ou `X-Workspace-Id`). Les clés API restent liées au workspace de leur émission, indépendamment du cookie et de cet en-tête.
- Rôles `owner`, `admin`, `editor`, `viewer`. Le propriétaire conserve la facturation et la propriété. Les administrateurs gèrent les invitations, connexions et clés ; les éditeurs modifient les ressources ; les lecteurs consultent. Un administrateur ne peut ni rétrograder le propriétaire ni promouvoir un autre administrateur. Les contrôles couvrent les anciennes routes de liens, QR, domaines et pages bio.
- Invitations envoyées par e-mail, empreinte du jeton en base, durée de sept jours, usage unique et compte vérifié correspondant à l’adresse invitée. Les invitations en attente réservent une place ; créations et acceptations sont sérialisées par workspace.
- Starter : un propriétaire ; Pro : trois places ; Elite : quinze places. Après réduction d’offre, les membres au-delà du quota perdent l’accès dans l’ordre inverse de leur ancienneté. Les lignes sont conservées et le propriétaire peut gérer tous les membres. La suppression d’un membre retire immédiatement son accès et celui des flux de session. Les clés appartenant au workspace doivent être révoquées séparément si elles ont été partagées.
- Dossiers et tags : jusqu’à 200 libellés, vingt tags par lien, aucune association entre workspaces. Import CSV RFC4180 de 500 lignes / 256 Ko, résultat par ligne et quotas normaux. Les lignes réussies sont conservées lorsqu’une autre échoue ; réimporter crée de nouveaux liens. Export complet avec protection contre les formules de tableur.
- Journal de workspace Elite, événements de ressources et d’administration, contrôle d’immutabilité en base. L’anonymisation d’un acteur et l’effacement de son workspace restent possibles.

Parcours : `/app/team`, `/app/organization`, et sélecteur de workspace dans la navigation. L’espace client garde cinq entrées sur mobile, avec les autres rubriques sous « Plus ».

## Intégrations

| Outil | Comportement livré | Configuration |
|---|---|---|
| Stripe | Réception des paiements réussis ; déduplication commune aux événements Checkout et PaymentIntent d’un même paiement | Secret du webhook Stripe ; remplacer son URL de livraison par celle fournie par Beep |
| Shopify | Réception des commandes payées, HMAC du corps brut et contrôle du domaine marchand | Secret de l’application et domaine `*.myshopify.com`, puis abonnement webhook |
| WooCommerce | Réception des commandes `processing` / `completed`, signature HMAC du corps brut | Secret du webhook et URL de livraison Beep |
| Zapier / Make | Événements sortants signés, file durable et journal existant | URL HTTPS de réception ; secret de signature affiché une fois |
| Slack | Notifications de leads, ventes et créations de liens ; alertes Radar | URL Incoming Webhook Slack |
| GA4 | Leads et ventes via Measurement Protocol | Measurement ID et API secret ; référence visiteur consentie nécessaire |

Pro donne accès à Zapier et Make ; Elite donne accès à tous les connecteurs. Les connexions se configurent dans `/app/integrations`. Il s’agit de connecteurs à configuration explicite, sans installation OAuth ou publication sur les marketplaces fournisseurs.

Les ventes reçues exigent `beep_consent=true` dans les métadonnées (`note_attributes` Shopify, `meta_data` WooCommerce). `beep_cid` rattache la vente à un clic ; `beep_visitor` est facultatif pour Beep et doit être un identifiant opaque. GA4 exige cette référence visiteur pour recevoir le postback. Aucun e-mail client, nom, adresse, IP ou panier n’est importé. Les montants Stripe suivent les [unités mineures et cas particuliers du fournisseur](https://docs.stripe.com/currencies), y compris ISK, UGX et MGA ; Shopify et WooCommerce transmettent une valeur décimale.

Les signatures Stripe ont une tolérance de cinq minutes ; Shopify et WooCommerce utilisent HMAC-SHA256 en Base64. La collecte lit au plus 256 Ko et déduplique par connexion et identifiant métier. Le worker effectue huit tentatives avec baux et backoff, conserve les erreurs normalisées et permet le rejeu. Les secrets, y compris les URL natives de webhook, sont chiffrés et absents des vues et exports. Désactiver une connexion annule les travaux encore en attente.

Le visiteur est pseudonymisé avant stockage dans la file. Export et effacement visiteur couvrent aussi les conversions en attente ; un travail réclamé doit encore être actif sous verrou du workspace avant son ingestion. Les journaux gardent un marqueur de déduplication sans les données effacées. Les livraisons déjà reçues par un fournisseur ne peuvent pas être retirées par Beep.

Les erreurs sortantes et les rejeux sont également visibles dans `/app/developer`. Les destinations sortantes reprennent la protection DNS/HTTPS de la phase 3 ; aucune redirection réseau n’est suivie.

## Radar, temps réel et rapports

- Alertes sur pic de clics, taux ventes/clics sous un seuil, destination indisponible et domaine en erreur. Fenêtre de quinze minutes, au plus une alerte par règle et fenêtre ; la chute de conversion exige vingt clics minimum. Cinquante règles maximum par workspace.
- Radar Pro par e-mail au propriétaire ; Elite ajoute Slack et webhooks. Les webhooks Radar utilisent l’outbox signée existante (`radar.alert`), avec un secret affiché une seule fois. Supprimer la règle désactive sa destination et annule les livraisons en attente.
- Vérification des destinations hors du chemin de redirection, toutes les six heures, avec cinq connexions simultanées. Seules les adresses publiques HTTP/80 ou HTTPS/443 sont interrogées, TLS vérifié. Les réponses 3xx sont rapportées sans être suivies ; `0` signifie échec de connexion ou destination interdite au vérificateur.
- Flux SSE Elite de snapshots Radar, renouvelé automatiquement ; la session, le membership et le plan sont revérifiés. Le flux de conversions de la phase 3 respecte aussi le workspace sélectionné.
- Customer insights Elite : tendances quotidiennes sur 90 jours, pays/appareils/référents et campagnes, sans profils individuels. Les visiteurs sont dédupliqués par journée UTC, pas sur toute la période.
- Rapports quotidiens ou hebdomadaires, destinés au propriétaire vérifié, synthèse clics/ventes sur les mêmes journées UTC complètes (une ou sept). Planification et file de notification persistantes, huit tentatives en cas d’échec.

Parcours : `/app/radar` et `/app/insights`. Les jobs `integrations.process`, `radar.health`, `radar.evaluate`, `radar.notify`, `reports.schedule`, `affiliates.accrue` et `ecosystem.retention` sont visibles dans les outils d’exploitation existants.

## Bio et extension Chrome

Elite ajoute les blocs vidéo et audio HTTPS, sans autoplay ni iframe, ainsi que capture d’e-mail et formulaire avec consentement explicite, politique de confidentialité, contrôle d’origine et limitation de débit. Les réponses sont chiffrées, accessibles aux administrateurs dans `/app/bio-submissions`, effaçables et supprimées après 90 jours. Une réduction d’offre masque les blocs avancés publiés et interdit de nouvelles réponses.

L’extension Manifest V3 est dans [`extensions/chrome`](../extensions/chrome/README.md). Elle lit uniquement l’onglet actif à l’ouverture, puis crée le lien après validation. Clé `sk_` de portée `write` dans `chrome.storage.session`, origine mémorisée localement, permissions HTTPS demandées pour l’origine choisie, aucun script injecté ni lecture de l’historique. Les reprises réseau conservent `Idempotency-Key`. Le dossier est chargeable en mode développeur ; aucune publication Chrome Web Store n’a été faite.

## Affiliation et back-office

- Enrôlement explicite, code stable et un parrain par nouveau workspace, avant le premier paiement et dans les trente jours de sa création. Auto-parrainage interdit. Conditions initiales : 20 % des montants encaissés pendant douze mois, validation après trente jours. Les paiements sandbox sont exclus ; un remboursement annule les commissions non versées. Les remboursements après versement nécessitent une revue finance.
- La finance consigne les versements déjà effectués, avec référence, motif et TOTP. Les commissions doivent être approuvées et ne peuvent être versées deux fois. Cette action ne déclenche pas de transfert bancaire ou Mobile Money. Les paramètres commerciaux et conditions doivent être validés avant un lancement réel.
- Rôles staff granulaires : support (assistance, intégrations, vue en lecture seule), finance (commissions), trust/safety (intégrations et confidentialité), content (brouillons/publications), readonly et superadmin. Changer un rôle ou désactiver un membre révoque ses sessions ; auto-modification et suppression du dernier administrateur sont refusées.
- La vue staff en lecture seule dure dix minutes, est liée au membre staff et révocable. Son jeton reste hors des URL ; elle n’émet aucune session client ni capacité d’écriture déléguée.
- Notes de support dans la base du back-office ; historique et supervision passent par l’API core. Une réconciliation horaire de cent workspaces supprime notes et vues associées à un workspace dont le core confirme l’effacement, y compris après suppression en libre-service. Une erreur réseau ne provoque jamais cette purge.
- Blog, feuille de route et annonces : brouillons dans le back-office, copie publiée versionnée dans le core, compteurs d’édition/publication distincts, détection des conflits, retrait public. Le site lit ces copies à la demande ; le HTML saisi reste du texte échappé. L’ancien article du journal est transféré par migration.
- Cohortes des douze derniers mois : activité humaine à 30 jours et rétention entre J90 et J120, avec dénominateur des cohortes matures. Créer plusieurs workspaces ne gonfle plus le compteur d’activation des utilisateurs.

Parcours staff : `/ecosystem`, `/content`, `/affiliates`, `/staff`. Les actions sensibles exigent permission, motif et TOTP, avec audit. Aucun accès croisé entre les deux bases applicatives.

## Migrations et recette

Contrat admin et public **0.8.0** ; le back-office requiert un core ≥ 0.8. Migrations core **V17–V19**, back-office **V3–V4**, validées sur PostgreSQL réel et depuis zéro. V17 est l’expansion ; **V18 remplace l’unicité globale du propriétaire par l’unicité du workspace personnel et élargit les types acceptés**. En déploiement progressif, retirer les anciens writers phase 3 avant V18 et basculer les interfaces après le core compatible. Aucune migration appliquée n’est réécrite et aucun `repair` n’est nécessaire.

La recette couvre les anciens parcours, invitations et rôles, isolation et révocation, import/export, signatures des trois fournisseurs, consentement, traitement durable, données visiteurs en attente, Radar, commissions et remboursements, édition concurrente, publication/retrait, vue staff, anonymisation et suppression multi-workspaces. Les journaux locaux `phase4-*.log` sont ignorés par Git.

Résultats : **311 tests Java distincts** (250 core, 61 back-office, dont 82 intégrations PostgreSQL), **347 contrôles de bout en bout**, **63 contrôles de contrat** et **7 tests Node** réussis. Les deux spécifications OpenAPI, le typage et les builds de production des deux fronts sont valides. Les 34 paires de contraste et les routes réservées sont vérifiées. Le dernier contrôle des rapports confirme une même fenêtre UTC complète pour les clics et les ventes.

Commandes : `make test-unit`, `make test-int`, `make test-contract`, `make test-e2e`, `make test-web`, et `node --test scripts/tracking-script.test.mjs scripts/chrome-extension.test.mjs`.

## Vérifications externes encore ouvertes

- Connexions avec de vrais comptes Stripe, Shopify, WooCommerce, Slack et GA4, permissions marchandes et vérification des rapports fournisseurs. La recette locale utilise des payloads signés synthétiques et des services de test.
- Installation et recette visuelle de l’extension dans Chrome, publication sur le Store, tests interactifs/mobile/lecteurs d’écran. Aucun navigateur pilotable n’était connecté ; builds, typage, contrats et parcours HTTP sont vérifiés.
- Mesures de charge en préproduction, toujours ouvertes depuis les phases précédentes. Aucun résultat de débit ou de latence n’est revendiqué pour la phase 4.
- Critères produit : NPS ≥ 40, rétention observée à 90 jours et cinq clients Elite actifs. Ce sont des observations à recueillir après lancement, pas des données simulées par les tests.
- Les réserves de lancement et de paiement des phases 2 et 3 restent applicables. La stack locale reste sur invitation et les paiements de recette sont simulés.

Références : [signatures Stripe](https://docs.stripe.com/webhooks/signature), [webhooks Shopify](https://shopify.dev/docs/apps/build/webhooks/verify-deliveries), [webhooks WooCommerce](https://developer.woocommerce.com/docs/apis/rest-api/v2/webhooks), [Incoming Webhooks Slack](https://docs.slack.dev/messaging/sending-messages-using-incoming-webhooks/), [GA4 Measurement Protocol](https://developers.google.com/analytics/devguides/collection/protocol/ga4/reference), [Chrome activeTab](https://developer.chrome.com/docs/extensions/develop/concepts/activeTab), [Chrome storage](https://developer.chrome.com/docs/extensions/reference/api/storage).
