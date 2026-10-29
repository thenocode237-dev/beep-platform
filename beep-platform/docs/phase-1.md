# Phase 1 — MVP (bêta privée)

Livraison des trois cycles de la [feuille de route](../../beep-roadmap.md#phase-1--mvp-s3s8), côté `beep` et côté `backoffice`.
Contrat admin : **0.2.0** (additif ; le back office exige un core ≥ 0.2 au démarrage).

## Ce qui est livré, par cycle

### Cycle 1 — liens courts et redirection

| `beep` | `backoffice` |
|---|---|
| Entité `Link`, création en un champ, édition, suppression ; slugs générés (base62, 7 caractères) ou personnalisés (3–64 caractères) ; slugs réservés | Recherche de liens (slug, destination, e-mail du propriétaire, filtre workspace) |
| Registre `slugs` : un chemin public désigne un lien **ou** une page bio, unicité garantie par la base | **Désactivation / réactivation** d'un lien, motif obligatoire, journalisée |
| Moteur de redirection `/{slug}` : cache Caffeine (dont cache négatif 30 s), invalidation `LISTEN/NOTIFY` au commit, threads virtuels, paramètres entrants transmis, 302 `no-store` | Liste des slugs réservés |
| File bornée en mémoire + écrivain par lots ; file pleine = événement abandonné et compté, jamais de redirection bloquée | |
| Pages HTML 404 et « lien indisponible » (410), FR/EN selon `Accept-Language`, sans script | |
| k6 (`make test-load`) : budget p95 < 50 ms, p99 < 150 ms, en CI à débit réduit | |

### Cycle 2 — analytics de base et QR codes

| `beep` | `backoffice` |
|---|---|
| Événements : clic, scan, vue de page bio, clic de bloc ; appareil, OS, navigateur, pays, référent (hôte seul), langue ; robots et aperçus de liens exclus des agrégats | **Métriques plateforme v1** : comptes, inscriptions/jour, activation (premier lien en 10 min), clics/jour, ressources, tailles des deux bases, retard du worker, file d'événements |
| `click_events` partitionnée par mois (UTC), partitions créées 2 mois à l'avance et à la volée si besoin, `DROP` au-delà de 13 mois | **Vue des jobs** : dernière exécution, issue, détail, volume et échecs sur 24 h |
| Agrégats horaires et journaliers recalculés par le worker (3 dernières heures, 2 derniers jours ; idempotents) | |
| Rétention : purge par lots selon le plan et ses overrides (Starter : 30 jours), sels de plus de 2 jours, historique des jobs de plus de 7 jours | |
| Tableau de bord par lien / QR / page : période (aujourd'hui heure par heure, 7, 30, 90 jours dans la limite du plan), totaux, série, répartitions ; **export CSV** | |
| **QR codes** dynamiques (`/q/{code}`, destination modifiable sans réimpression, scans mesurés) et statiques (URL, texte, Wi-Fi, e-mail, SMS, téléphone, vCard) ; export **SVG, PNG, PDF** vectoriel | |

### Cycle 3 — link in bio v1 et quotas

| `beep` | `backoffice` |
|---|---|
| Pages bio : blocs lien, bouton, titre, texte, image, réseaux sociaux ; thèmes Ivoire, Forêt, Nuit, Soleil ; SEO (titre, description, image OG, `noindex`) ; rendu serveur sans script, mis en cache en mémoire ; clics de blocs mesurés | **Plans & droits** : plans publiés et droits ; **overrides par workspace** (ré-authentification TOTP, motif, avant/après journalisés) |
| Connexion par **lien magique** et **OAuth Google/GitHub** (code + PKCE, état lié au navigateur) ; le TOTP reste exigé s'il est actif | **Fiche utilisateur / workspace** : usage face aux quotas, droits effectifs, overrides |
| Domaine `Plan` + `Entitlements` ; Starter gratuit (5 liens, 5 QR dynamiques, 1 page, clics et scans illimités, 30 jours d'analytics) ; Pro et Elite publiés comme « bientôt » | **Suspension / réactivation** de compte (droit dédié, TOTP, motif) : sessions révoquées, liens et pages du workspace en page d'information |
| **Bêta sur invitation** : seules les adresses invitées créent un compte, quel que soit le moyen ; réponses identiques pour ne rien révéler | **Invitations** à la bêta (envoi, liste, statut) |
| | **Journal d'audit** consultable (filtres action/auteur, pagination, état d'intégrité de la chaîne) ; sa consultation est elle-même journalisée |

## Décisions et écarts

- **Un workspace personnel par utilisateur**, créé à la première utilisation (comptes de la phase 0 compris). Les équipes et rôles de workspace arrivent en phase 4 ; l'index unique `workspaces_owner_key` sera alors relâché.
- **Quotas mensuels sur le mois civil UTC** : les cycles de facturation n'existent qu'avec les abonnements (phase 2).
- **Export CSV** : la roadmap le livre en phase 1, la grille le réserve à Pro et Elite. Il est porté par le droit `features.export_csv` (Starter : 0) et s'active pour un workspace de la bêta par override depuis le back office.
- **Badge « Propulsé par BeepClick »** toujours affiché : il n'est retirable qu'en Elite, qui n'est pas commercialisé avant la phase 3.
- **Suspension** = compte **et** workspace : en plus de la connexion bloquée, les liens et pages du workspace affichent une page d'information (410). C'est une décision de modération, distincte des quotas, qui n'arrêtent jamais une redirection.
- **Désactivation des pages bio** (modération) non incluse : elle arrive avec la file de modération (phase 2).
- **Pays** : base MaxMind Country locale (`GEOIP_DB_PATH`, licence GeoLite2 à obtenir). Sans base, les pays sont « inconnus » (`ZZ`) et rien d'autre ne change.
- **Visiteurs uniques** : empreinte SHA-256(sel du jour ‖ IP ‖ User-Agent) tronquée à 128 bits ; le sel aléatoire est partagé entre instances en base puis détruit à J+2. Les uniques sont exacts par jour et **cumulés** sur une période (libellé explicite dans l'interface).
- **Jours et heures en UTC** dans les agrégats et l'interface.
- **Images des pages bio par URL https** : l'envoi de fichiers demande un stockage objet, prévu plus tard.
- **API publique `/v1`** : les routes de l'espace client existent, mais le contrat public (`beep-public.openapi.yaml`), les clés d'API et les limites par plan relèvent de la phase 3.
- **Jetons à usage unique** : la migration `V5__contract_widen_token_purposes.sql` recrée une contrainte `CHECK` pour l'élargir ; elle est nommée « contract » car `MigrationPolicyTest` signale tout `DROP CONSTRAINT`, mais ne supprime aucune donnée.
- **Un seul worker** : les tâches planifiées ne prennent pas de verrou distribué ; plusieurs workers exigeront la table de jobs `SKIP LOCKED` prévue (specs §4.6).
- **`.gitignore`** : le motif `out/` (export Next.js) ignorait aussi les paquets Java `adapter/out/` ; le commit « Phase 0 » ne contenait donc aucun adapter sortant. Le motif est désormais limité aux deux fronts.

## Exploitation

| Réglage | Rôle |
|---|---|
| `SIGNUP_MODE` | `invite` (défaut, bêta privée) ou `open` |
| `GEOIP_DB_PATH` | Chemin du fichier `.mmdb` monté dans le conteneur `core` |
| `OAUTH_GOOGLE_CLIENT_ID/SECRET`, `OAUTH_GITHUB_CLIENT_ID/SECRET` | Active le fournisseur ; URL de retour : `{BEEP_PUBLIC_URL}/v1/auth/oauth/{google,github}/callback` |
| `BEEP_ROLLUP_INTERVAL` | Fréquence des agrégats (défaut `PT1M`, `PT10S` en dev) |
| `BEEP_REDIRECT_CACHE_SIZE`, `BEEP_EVENTS_QUEUE` | Taille du cache de résolution, capacité de la file d'événements |

Métriques Prometheus (port de gestion) : `http_server_requests_seconds` (histogramme, `uri="/{slug}"`, SLO 50/150 ms), `beep_events_queued|dropped|written`, `cache_gets_total{cache="redirect.slugs"}` et voisins.

## Vérifications (24 octobre 2026, poste de développement)

| Commande | Résultat |
|---|---|
| `make test-unit` | core 85 tests (domaine, cas d'usage, rendu QR, ArchUnit), back office 34 tests — verts |
| `make test-int` | 17 tests core (dont registre des slugs, partitions à la volée, agrégats, rétention, invalidation LISTEN/NOTIFY) + 5 back office, PostgreSQL 16 réel — verts |
| `make migrate-validate` | migrations V1→V5 rejouées de zéro, politique expand/contract — vert |
| `make test-contract` | 30 contrôles du contrat 0.2.0 contre le core réel — verts |
| `make test-e2e` | parcours complets phase 0 + phase 1 sur la stack Compose — tous les contrôles passent |
| `make test-load` | 200 req/s : p95 1,8 ms ; 1 000 req/s pendant 30 s : p95 1,65 ms, p99 2,3 ms, 0 % d'erreur. Mesure locale (même hôte que le générateur), **pas** la mesure de préproduction exigée |
| `make lint`, `make test-web` | typage strict, contrastes, slugs réservés = routes Caddy ; build des deux fronts — verts |

Non fait : revue visuelle dans un navigateur (captures mobile/bureau des nouveaux écrans), essais sur appareils réels, Lighthouse.

## Reste à faire

- **Critères de sortie** : bêta de 100 personnes invitées avec taux d'activation mesuré (outillé : métrique « activation » du back office) ; p95 < 50 ms **à 1 000 req/s sur l'hôte de préproduction** (`make test-load RATE=1000 DURATION=60s`) ; sauvegarde/restauration vérifiées (PITR de la phase 0 toujours à démontrer en staging).
- Licence et fichier GeoLite2, identifiants OAuth de production.
- Essais sur appareils réels (pages bio : LCP < 2 s en 4G) et audit Lighthouse mobile.
