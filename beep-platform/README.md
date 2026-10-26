# Beep — plateforme

Monorepo de `beep` (site, espace client, API, moteur de redirection) et `backoffice` (outil interne staff).

Domaine de production : **`beep.click`** ; back office : **`dashboard.beep.click`**. Le modèle [`.env.production.example`](.env.production.example) configure ces adresses. Les illustrations et exemples de liens utilisent `beep.click`.
Spécifications : `../beep-specs.md` · Feuille de route : `../beep-roadmap.md` · **État actuel : phase 5 implémentée et recette locale validée ; validations externes documentées**. Détail : [`docs/phase-5.md`](docs/phase-5.md).

La phase 4 ajoute les workspaces d’équipe (Pro : 3 membres, Elite : 15), rôles et invitations, dossiers/tags, import CSV, intégrations, Radar, rapports, insights, blocs bio avancés, extension Chrome et affiliation. Le back-office supervise ces fonctions, les vues en lecture seule, le contenu publié et les accès staff. Parcours : `/app/team`, `/app/integrations`, `/app/radar`, et `dashboard.beep.localhost/ecosystem`. Installation de l’extension : [`extensions/chrome/README.md`](extensions/chrome/README.md).

La phase 3 ajoute conversions et attribution, `beep.js`, analytics de site et campagnes, API publique avec clés et idempotence, webhooks signés, pixels et postbacks, ainsi que leurs outils staff. Elite est disponible dans le catalogue local. Parcours : `/app/developer`, `/docs`, `/en/docs` et `dashboard.beep.localhost/developer`. La stack reste sur invitation ; les paiements de recette sont simulés.

La phase 2 ajoute facturation, domaines personnalisés, règles et simulateur, QR avancés, modération, feature flags et gestion des données du compte. Pages publiques : `/features`, `/pricing`, `/blog`, `/legal`, `/report`, `/status`. La stack locale reste sur invitation et les paiements de recette sont simulés.

## Démarrer

Prérequis : **Docker** (Compose v2) et **Node ≥ 20**. Aucun JDK local n'est nécessaire (Maven tourne en conteneur).

```bash
npm install          # une fois : dépendances des fronts et des outils
make up              # secrets générés, build, migrations, démarrage de toute la stack
make seed            # (optionnel) démo : demo@beep.localhost, lien /demo, page /atelier-demo ; le TOTP du staff s'affiche dans les logs
```

| URL | Quoi |
|---|---|
| http://beep.localhost | Site, inscription, connexion, espace client (FR, et EN sous `/en`) |
| http://dashboard.beep.localhost | Back office (staff, TOTP obligatoire) |
| http://mail.beep.localhost | Mailpit : e-mails de confirmation et de réinitialisation |

`make help` liste toutes les commandes.

## SEO et production

Les pages publiques disposent de métadonnées FR/EN, de canoniques, de liens de langue et d’un sitemap dynamique. Pour préparer le serveur, les DNS et les variables de production, suivre le [guide de déploiement](docs/production.md).

```bash
make prod-check ENV_FILE=.env.production
make deploy-prod ENV_FILE=.env.production
```

Le déploiement vérifie la configuration, construit les images, exécute les migrations puis attend les services sains. Le projet Compose `beep-prod` sépare les volumes de production du développement.

## Ce que livre la phase 1

| Domaine | Livré |
|---|---|
| **Liens courts** | Création en un champ, slugs générés (base62, 7 car.) ou personnalisés, slugs réservés (= routes du site, contrôlé en CI), registre commun aux liens et pages bio, modification de destination, suppression (refusée si un QR dynamique suit le lien) |
| **Redirection** | `/{slug}`, `/q/{code}`, `/_b/c/…` : cache Caffeine invalidé par `LISTEN/NOTIFY` (cache négatif inclus), threads virtuels, paramètres entrants transmis, 302 `no-store`, page 404 et page « lien indisponible » (410), file d'événements bornée écrite par lots ; k6 en CI (budget p95 < 50 ms) |
| **Analytics** | Clics, scans, vues ; appareil, OS, navigateur, pays (base MaxMind locale facultative), référent (hôte seul), langue ; robots et aperçus exclus ; IP jamais stockée (empreinte salée, sel quotidien détruit à J+2) ; partitions mensuelles, agrégats horaires/journaliers par le worker, rétention 13 mois / plan (Starter : 30 j) ; export CSV (droit `features.export_csv`) |
| **QR codes** | Dynamiques (suivent un lien, scans mesurés) et statiques (URL, texte, Wi-Fi, e-mail, SMS, téléphone, vCard) ; couleurs (contraste ≥ 3:1 vérifié), correction d'erreur ; export SVG, PNG, PDF vectoriel |
| **Pages bio** | Blocs lien, bouton, titre, texte, image, réseaux sociaux ; 4 thèmes ; SEO (titre, description, image OG, `noindex`) ; rendu serveur sans script, CSS en ligne, quelques Ko ; clics de blocs mesurés |
| **Plans & quotas** | Domaine `Plan` + `Entitlements` ; Starter (5 liens, 5 QR dynamiques, 1 page, 30 j d'analytics), Pro/Elite publiés « bientôt » ; quotas de stock/mensuels sous verrou, avertissement 80 %, blocage 100 % ; les redirections ne dépendent jamais d'un quota |
| **Comptes** | Bêta sur invitation (`SIGNUP_MODE`), lien magique, OAuth Google/GitHub (PKCE), TOTP toujours exigé s'il est actif, workspace personnel |
| **Back office** | Recherche/désactivation de liens, slugs réservés, fiche utilisateur (usage vs quotas, droits effectifs), overrides et suspension avec ré-authentification TOTP, plans, métriques (inscriptions, activation, clics/jour, tailles des bases, retard du worker, file d'événements), jobs, journal d'audit (filtres, intégrité de la chaîne), invitations ; contrat admin **0.9.0** |

Ce qui reste ouvert pour clore la phase 1 (bêta de 100 personnes, mesure à 1 000 req/s en préproduction) : voir [`docs/phase-1.md`](docs/phase-1.md#reste-à-faire).

## Ce que livre la phase 0

| Domaine | Livré |
|---|---|
| **Architecture** | `beep/core` et `backoffice/api` : Java 21 / Spring Boot 3.4 en modules Maven (`domain`, `application`, `adapter-in-*`, `adapter-out-*`, `bootstrap`), profils `prod` / `worker` / `migrate` / `seed` / `dev`, règles **ArchUnit** qui font échouer la CI |
| **Données** | PostgreSQL 16 dans Compose, 2 bases + 2 rôles sans droits croisés, jOOQ + HikariCP, **Flyway** via services one-shot `core-migrate` / `backoffice-migrate`, politique expand/contract testée |
| **Auth (site)** | Inscription (anti-énumération), confirmation d'e-mail, connexion, sessions (cookie `HttpOnly`, `SameSite=Lax`, `__Host-` en HTTPS), **TOTP** (RFC 6238, secret chiffré AES-GCM), réinitialisation de mot de passe, limitation de débit, e-mails transactionnels (Mailpit en dev) |
| **Auth (staff)** | TOTP **obligatoire** à chaque connexion, session courte (8 h max, 30 min d'inactivité), rôles de base, **journal d'audit append-only à chaînage de hachage** (UPDATE/DELETE/TRUNCATE refusés par la base) |
| **Contrat** | `contracts/beep-admin.openapi.yaml` v0.1 ; API admin `GET /users`, `GET /meta`, jeton de service à rotation (deux jetons acceptés) ; test de compatibilité de version au démarrage du back office ; test de contrat contre le core réel |
| **Fronts** | Next.js 15 : site (accueil, connexion, inscription, vérification, mot de passe oublié, espace client), back office (connexion, coque adaptative, utilisateurs) ; **i18n FR/EN** |
| **Charte** | Direction **Signal**, inspirée de Vendeer : fond blanc, cobalt, bleu encre, Manrope/DM Sans locales, illustrations et photos WebP. Tokens primitive → sémantique → composant, clair/sombre, 34 paires de contrastes vérifiées — voir [`docs/design-system.md`](docs/design-system.md) |
| **Infra** | Caddy (routage par hôte, en-têtes de sécurité, `/internal` et `/actuator` jamais exposés), réseaux `edge` / `backend` (interne) / `db` (interne), images non-root, Makefile, CI |
| **Observabilité** | `/healthz`, logs JSON en prod, métriques Prometheus sur le port de gestion (8090 / 8091, jamais routé) |

## Direction graphique et expérience mobile

La [refonte Signal du 24 octobre 2026](docs/refonte-atelier.md) couvre le site FR/EN, les parcours de compte et le back office. Le discours explique les bénéfices de Beep pour le partage sur WhatsApp, les réseaux et les supports physiques. L’interface est conçue dès 320 px, avec formulaires à 16 px, actions de 44 à 52 px, CTA mobile fixe et tableaux remplacés par des cartes sur téléphone.

Les aperçus marketing sont illustratifs. L’état de livraison des liens, QR codes, pages bio, statistiques et évolutions suivantes est suivi dans les documents de phase. Voir les [captures et la recette](docs/refonte-atelier.md#recette-du-rendu) et la [provenance des médias](docs/assets.md).

## Qualité

```bash
make test-unit       # domaine + cas d'usage + règles d'architecture
make test-int        # adapters sur PostgreSQL réel, migrations rejouées de zéro
make migrate-validate
make test-contract   # contrat OpenAPI valide + core réel conforme (stack démarrée)
make test-e2e        # parcours complet phase 0 + phase 1 sur la stack (stack démarrée)
make seed && make test-load   # k6 sur la redirection (RATE=1000 DURATION=60s pour le palier de phase 1)
make test-web        # typage + build des deux fronts
make lint            # TypeScript strict + contrastes des tokens + slugs réservés = routes Caddy
```

## Structure

```
beep/core        Java — API, redirector et worker (rôles de processus séparés)
beep/web         Next.js — site et espace client
backoffice/api   Java — API du back office, passerelle vers le core
backoffice/web   Next.js — interface staff
packages/design-tokens   tokens.json -> tokens.css, TOKENS.md, contrôle des contrastes
packages/ui              composants et styles partagés
contracts        OpenAPI (source de vérité core <-> back office)
infra            Caddy, PostgreSQL (init, image WAL-G)
scripts          init-env, tests d'intégration, contrat, e2e, sauvegarde/restauration
docs             charte graphique, runbook de sauvegarde
```

## Décisions et écarts par rapport à la spécification

- **Maven en conteneur** (`MVN_IMAGE`) plutôt que `./mvnw` : seul Docker est exigé sur le poste. Avec un JDK 21 local : `make test-unit MVN=mvn`.
- **Client `coregateway` écrit à la main** (contrat 0.2, quinze opérations). DTO identiques aux schémas ; le test de contrat vérifie le core réel. Passage à un client généré (openapi-generator) dès que le contrat se stabilise (phase 2).
- **Sessions staff en base (jeton opaque)** plutôt que `BO_SESSION_SECRET` : révocation immédiate, rien à signer. Le secret de session de la spécification n'existe donc pas ; `BO_ENCRYPTION_KEY` chiffre les secrets TOTP.
- **`en` est un slug réservé** : le français est servi sans préfixe, l'anglais sous `/en`, et Caddy route `/en*` vers le site.
- **Limiteur de débit partagé PostgreSQL** depuis la phase 5 : les instances comptent les mêmes tentatives.
- **Hot reload** (DevTools, `next dev`) non fourni dans le Compose de dev : reconstruire un service avec `make up`.

## Reste à faire pour clore la phase 0

- **PITR démontré en staging** (critère de sortie) : outillage livré, démonstration à faire et à consigner dans [`docs/runbook-backup.md`](docs/runbook-backup.md).
- **Réserver le nom `Beep`** (marque, domaine) et valider les hypothèses H1–H7 des spécifications.
- Revue visuelle sur appareils réels (iOS Safari, Android Chrome entrée de gamme) et audit Lighthouse mobile.

## Phase 5 — Industrialisation

Les services API, redirector et worker sont séparés. La console `/enterprise` gère les contrats, preuves et versions SDK ; `/app/enterprise` présente le contrat et les applications mobiles. Voir [la livraison phase 5](docs/phase-5.md), [le runbook](docs/runbook-industrialisation.md) et [les SDK](sdk/mobile/README.md). PgBouncer est fourni par `docker-compose.scale.yml` ; réplica et TimescaleDB sont optionnels et explicitement configurés.
