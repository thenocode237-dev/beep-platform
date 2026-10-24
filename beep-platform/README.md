# Beep — plateforme

Monorepo de `beep` (site, espace client, API, moteur de redirection) et `backoffice` (outil interne staff).
Spécifications : `../beep-specs.md` · Feuille de route : `../beep-roadmap.md` · **État actuel : Phase 0 — Fondations**.

## Démarrer

Prérequis : **Docker** (Compose v2) et **Node ≥ 20**. Aucun JDK local n'est nécessaire (Maven tourne en conteneur).

```bash
npm install          # une fois : dépendances des fronts et des outils
make up              # secrets générés, build, migrations, démarrage de toute la stack
make seed            # (optionnel) comptes de démo ; le secret TOTP du staff s'affiche dans les logs
```

| URL | Quoi |
|---|---|
| http://beep.localhost | Site, inscription, connexion, espace client (FR, et EN sous `/en`) |
| http://dashboard.beep.localhost | Back office (staff, TOTP obligatoire) |
| http://mail.beep.localhost | Mailpit : e-mails de confirmation et de réinitialisation |

`make help` liste toutes les commandes.

## Ce que livre la phase 0

| Domaine | Livré |
|---|---|
| **Architecture** | `beep/core` et `backoffice/api` : Java 21 / Spring Boot 3.4 en modules Maven (`domain`, `application`, `adapter-in-*`, `adapter-out-*`, `bootstrap`), profils `prod` / `worker` / `migrate` / `seed` / `dev`, règles **ArchUnit** qui font échouer la CI |
| **Données** | PostgreSQL 16 dans Compose, 2 bases + 2 rôles sans droits croisés, jOOQ + HikariCP, **Flyway** via services one-shot `core-migrate` / `backoffice-migrate`, politique expand/contract testée |
| **Auth (site)** | Inscription (anti-énumération), confirmation d'e-mail, connexion, sessions (cookie `HttpOnly`, `SameSite=Lax`, `__Host-` en HTTPS), **TOTP** (RFC 6238, secret chiffré AES-GCM), réinitialisation de mot de passe, limitation de débit, e-mails transactionnels (Mailpit en dev) |
| **Auth (staff)** | TOTP **obligatoire** à chaque connexion, session courte (8 h max, 30 min d'inactivité), rôles de base, **journal d'audit append-only à chaînage de hachage** (UPDATE/DELETE/TRUNCATE refusés par la base) |
| **Contrat** | `contracts/beep-admin.openapi.yaml` v0.1 ; API admin `GET /users`, `GET /meta`, jeton de service à rotation (deux jetons acceptés) ; test de compatibilité de version au démarrage du back office ; test de contrat contre le core réel |
| **Fronts** | Next.js 15 : site (accueil, connexion, inscription, vérification, mot de passe oublié, espace client), back office (connexion, coque adaptative, utilisateurs) ; **i18n FR/EN** |
| **Charte** | Direction **Atelier**, inspirée de Vendeer : ivoire, terre cuite, forêt, Manrope/DM Sans locales, illustrations et photos WebP. Tokens primitive → sémantique → composant, clair/sombre, 34 paires de contrastes vérifiées — voir [`docs/design-system.md`](docs/design-system.md) |
| **Infra** | Caddy (routage par hôte, en-têtes de sécurité, `/internal` et `/actuator` jamais exposés), réseaux `edge` / `backend` (interne) / `db` (interne), images non-root, Makefile, CI |
| **Observabilité** | `/healthz`, logs JSON en prod, métriques Prometheus sur le port de gestion (8090 / 8091, jamais routé) |

## Direction graphique et expérience mobile

La [refonte Atelier du 24 octobre 2026](docs/refonte-atelier.md) couvre le site FR/EN, les parcours de compte et le back office. Le discours explique les bénéfices de Beep pour le partage sur WhatsApp, les réseaux et les supports physiques. L’interface est conçue dès 320 px, avec formulaires à 16 px, actions de 44 à 52 px, CTA mobile fixe et tableaux remplacés par des cartes sur téléphone.

Les outils de liens, QR codes, pages bio et analytics restent annoncés comme **à venir**. Les aperçus sont illustratifs ; la phase 0 livre la gestion de compte et la sécurité. Voir les [captures et la recette](docs/refonte-atelier.md#recette-du-rendu) et la [provenance des médias](docs/assets.md).

## Qualité

```bash
make test-unit       # domaine + cas d'usage + règles d'architecture
make test-int        # adapters sur PostgreSQL réel, migrations rejouées de zéro
make migrate-validate
make test-contract   # contrat OpenAPI valide + core réel conforme (stack démarrée)
make test-e2e        # parcours complet sur la stack (stack démarrée)
make test-web        # typage + build des deux fronts
make lint            # TypeScript strict + contrastes des tokens
```

## Structure

```
beep/core        Java — API, (redirection en phase 1), worker
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
- **Client `coregateway` écrit à la main** (un seul endpoint). Il sera remplacé par un client généré (openapi-generator) quand le contrat grossira ; le test de contrat protège d'ici là.
- **Sessions staff en base (jeton opaque)** plutôt que `BO_SESSION_SECRET` : révocation immédiate, rien à signer. Le secret de session de la spécification n'existe donc pas ; `BO_ENCRYPTION_KEY` chiffre les secrets TOTP.
- **`en` est un slug réservé** : le français est servi sans préfixe, l'anglais sous `/en`, et Caddy route `/en*` vers le site.
- **Limiteur de débit en mémoire** (une instance d'API en phase 0). À remplacer par un adapter partagé à la phase 5.
- **Hot reload** (DevTools, `next dev`) non fourni dans le Compose de dev : reconstruire un service avec `make up`.

## Reste à faire pour clore la phase 0

- **PITR démontré en staging** (critère de sortie) : outillage livré, démonstration à faire et à consigner dans [`docs/runbook-backup.md`](docs/runbook-backup.md).
- **Réserver le nom `Beep`** (marque, domaine) et valider les hypothèses H1–H7 des spécifications.
- Revue visuelle sur appareils réels (iOS Safari, Android Chrome entrée de gamme) et audit Lighthouse mobile.
