# Beepclick — Spécifications complètes

Plateforme tout-en-un de gestion de liens : **liens courts, deeplinks, Onelink, QR codes, link in bio, suivi de conversion, analytics, API/webhooks**, avec un **back office** (staff) exposé en sous-domaine.

> Statut : v1.1 du document (PostgreSQL + Java) · Périmètre : `beep` (plateforme) + `backoffice` (administration)
> Domaine de référence dans ce document : `beepclick.app` (en local : `beep.localhost`).

> Direction d’interface actualisée le **24 octobre 2026** : [charte Signal](beep-platform/docs/design-system.md), inspirée de Vendeer (glacier, cobalt, bleu encre, Manrope/DM Sans, médias locaux). Priorité mobile dès 320 px ; argumentaire fondé sur les usages de partage et les bénéfices de Beepclick. Voir la [refonte et sa recette](beep-platform/docs/refonte-atelier.md). Cette évolution visuelle ne change pas les jalons fonctionnels ; l’état du MVP et des évolutions suivantes est suivi dans les documents de phase.

---

## 0. Hypothèses et décisions à valider

| # | Point | Décision retenue |
|---|---|---|
| H1 | Base de données | **PostgreSQL 16 livré dans le Compose** (un serveur, deux bases `beep` et `backoffice`, deux rôles sans droits croisés). Choix retenu pour la croissance : concurrence d'écriture, réplication, PITR, partitionnement des événements. Les accès restent derrière des *ports* (le domaine ignore la base). |
| H2 | Deux projets | `beep` (site, espace client, API, moteur de redirection) et `backoffice` (outil interne staff). Chacun a **sa propre base** et son propre code en Clean Architecture. |
| H3 | Même domaine | `beepclick.app` = site + espace client + API + redirections. `dashboard.beepclick.app` = back office. |
| H4 | Stack | Backend **Java 21 + Spring Boot 3** (Maven multi-modules, jOOQ, Flyway), front **Next.js** (site, espace client **et** back office), reverse proxy **Caddy**, **Docker Compose**. Le domaine et les cas d'usage restent en Java pur, sans dépendance au framework. |
| H5 | « Radar » | Le module « Radar » de Taap.it n'est pas documenté publiquement. Dans Beepclick, **Radar = flux d'activité temps réel + alertes** (pic de trafic, lien cassé, domaine en erreur). À confirmer. |
| H6 | Tarifs | Offres **Starter (gratuit)**, **Pro 4 999 FCFA/mois** et **Elite 14 999 FCFA/mois**, fixées par le porteur du projet. Quotas inspirés de la grille publique de Taap.it (5 liens/QR/1 page en gratuit, 50 au total en entrée de gamme, 500 créations par mois en haut de gamme, 10 domaines, 15 membres). Prix supposés **TTC** ; à valider par une étude de coûts (infrastructure, frais de l'agrégateur de paiement). |
| H7 | Propriété intellectuelle | Beepclick reprend le **périmètre fonctionnel** de ce type de plateforme, pas son code, ses textes, ses visuels ni sa marque. Vérifier la disponibilité du nom « Beepclick » (marque et domaine) avant lancement. |
| H8 | Marché cible | Offres en **FCFA (XAF)** : on suppose un lancement en **Afrique centrale** (Cameroun, Gabon…). Conséquences retenues : **Mobile Money** (MTN MoMo, Orange Money) comme moyen de paiement principal, abonnements prépayés, support **WhatsApp**, pages légères pour connexions mobiles instables, redirections servies au plus près des utilisateurs (CDN/edge), conformité aux lois locales de protection des données à valider avec un juriste. |

---

## 1. Vision et objectifs

**Promesse** : « Des liens plus intelligents pour plus de conversions » — chaque lien partagé est optimisé (bonne app, bon appareil, bon store) et mesuré du clic jusqu'à la vente.

**Objectifs produit**
1. Remplacer en un seul espace : raccourcisseur d'URL, générateur d'UTM, link in bio, générateur de QR codes, outil de deeplinking.
2. Mesurer le **revenu réel** généré par chaque lien (clic → lead → vente), pas seulement les clics.
3. Rester simple : prise en main en moins de 5 minutes, sans développeur.
4. S'intégrer à l'écosystème client (API ouverte, webhooks, intégrations natives).

**Cibles** : créateurs de contenu, e-commerçants, marketeurs, agences, équipes growth, éditeurs d'apps mobiles.

**Indicateurs de succès (6 mois après lancement)** : taux d'activation (1er lien créé en moins de 10 min) > 60 %, conversion Starter (gratuit) → payant > 4 %, p95 de redirection < 50 ms, disponibilité redirection ≥ 99,9 % (99,95 % après la mise en haute disponibilité de la phase 5).

---

## 2. Périmètre fonctionnel — plateforme `beep`

### 2.1 Comptes, workspaces et équipes
- Inscription : e-mail + mot de passe (argon2id), lien magique, OAuth Google/GitHub.
- Authentification forte optionnelle (TOTP).
- **Workspace** : conteneur des liens, domaines, pages, QR codes, facturation. Un utilisateur peut appartenir à plusieurs workspaces.
- Rôles : `owner`, `admin`, `editor`, `viewer`. Invitations par e-mail.
- Dossiers et tags pour organiser les ressources ; recherche plein texte.
- Journal d'audit du workspace (plan Elite).
- Export CSV/JSON et suppression des données (RGPD).

### 2.2 Liens courts (Short links)
- Création en un champ : URL de destination → lien `beepclick.app/{slug}`.
- Slug personnalisé, génération automatique (base62, 7 caractères), liste de slugs réservés (§8.4).
- Titre, notes, tags, dossier.
- Règles avancées : expiration (date / nombre de clics), mot de passe, rotation A/B de destinations (répartition pondérée), routage par pays, par langue, par OS/appareil.
- Constructeur UTM intégré (`utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`) avec modèles réutilisables.
- Export CSV (Pro et Elite), import et création en masse (Elite).
- Vérification de santé de la destination (alerte si 4xx/5xx) — alimente Radar.
- Redirection 302/307 par défaut, 301 en option. Page intermédiaire optionnelle (marque, avertissement).

### 2.3 Deeplinks
- Un lien vers un contenu d'application (YouTube, Instagram, TikTok, Spotify, Amazon, etc.) **ouvre l'app native** au lieu du navigateur.
- **Contournement des navigateurs intégrés** (webviews Instagram, Facebook, TikTok, LinkedIn…) : page de bascule + schémas d'URL / intents Android adaptés.
- **Catalogue d'applications** géré depuis le back office : motif d'URL reconnu, schéma iOS, intent Android, repli web. Beepclick détecte l'app à partir de l'URL de destination.
- Repli intelligent : app non installée → store ou version web selon la règle.
- Aperçu « comment le lien se comportera » (simulateur iOS/Android/desktop) dans l'espace client.
- Extension Chrome « Ajouter à Beepclick » : création d'un deeplink en un clic depuis la page en cours (phase 4).

### 2.4 Onelink
- **Un seul lien, toutes les plateformes** : destinations distinctes pour iOS, Android, desktop, avec repli.
- Paramètres transmis à la destination (UTM, `beep_cid`).
- Deeplink différé (*deferred deeplink*) via SDK léger : hors périmètre v1, prévu en phase 5.

### 2.5 Domaines personnalisés et liens de marque
- Ajout d'un domaine ou sous-domaine du client (`go.maboite.com`), vérification DNS (CNAME + TXT), **certificat TLS automatique** (Caddy on-demand TLS).
- Domaine par défaut du workspace, par lien, par page bio, par QR code.
- Page de redirection racine configurable (ex. vers le site du client) et page 404 personnalisée.
- Statut en temps réel (DNS ok, TLS ok, erreur) visible côté client et côté back office.

### 2.6 QR codes
- Types : **dynamique** (pointe vers un lien Beepclick, destination modifiable sans réimprimer) et **statique** (URL, texte, Wi-Fi, vCard, e-mail, SMS).
- Personnalisation : couleurs, dégradés, forme des modules et des yeux, logo central, cadre avec appel à l'action, niveau de correction d'erreur.
- Export **SVG, PNG (haute résolution), PDF** prêts à imprimer.
- Statistiques de scan (mêmes dimensions que les liens).
- Génération en masse depuis un CSV (Elite). Modèles de style enregistrables.

### 2.7 Link in bio
- Page publique `beepclick.app/{slug}` (ou domaine perso) : hub entièrement personnalisable.
- Blocs : lien, bouton, titre, texte, image, galerie, vidéo (YouTube, Vimeo, TikTok), audio (Spotify, SoundCloud), réseaux sociaux (icônes), capture d'e-mail, formulaire de contact, carte, compte à rebours, FAQ, séparateur, produit.
- Blocs **programmables** : affichage entre deux dates, par pays, par appareil.
- Thèmes (clair/sombre, polices, couleurs, arrière-plans image/dégradé/vidéo), CSS personnalisé (Elite).
- SEO : titre, description, image Open Graph, `noindex` en option.
- Rendu SSR + cache CDN ; score Lighthouse mobile cible ≥ 95.
- Badge « Propulsé par Beepclick » retirable avec le plan Elite.
- Nombre de pages bio selon le plan.

### 2.8 Suivi de conversion (Conversion tracking)
- **Identifiant de clic** propriétaire (`beep_cid`) ajouté à la destination et conservé dans un cookie first-party par le script Beepclick.
- **Script `beep.js`** (installation par une ligne) : pixel de suivi, capture du `beep_cid`, pages vues, mode sans cookie.
- **Événements** : `lead`, `sale` (montant + devise), `signup`, `subscription`, `custom:*`. Envoi par :
  - navigateur (`beep('track','sale',{value:49,currency:'EUR'})`),
  - serveur à serveur (`POST /v1/events`, clé API),
  - intégrations (Stripe, Shopify, WooCommerce, Zapier/Make).
- **Deux types de clés** : une **clé publique** (`pk_…`, utilisable dans le navigateur, limitée à la collecte d'événements et aux origines autorisées) et une **clé secrète** (API serveur). Le script envoie ses événements à l'endpoint public `/_b/collect`.
- **Attribution** : dernier clic (défaut), premier clic, fenêtre configurable (1 à 90 jours).
- Funnel par lien : clics → leads → ventes, taux de conversion à chaque étape, revenu, valeur par clic.
- **Pixels de retargeting** déclenchés à la redirection (Meta, TikTok, Google Ads, LinkedIn) — Elite.
- Postbacks serveur configurables (Meta CAPI, Google).
- Respect du consentement (Consent Mode) et possibilité de désactiver tout suivi par lien.

### 2.9 Analytics
- **Analytics de liens** : clics, visiteurs uniques, source/référent, appareil, OS, navigateur, langue, pays/ville, heure, bots filtrés.
- **Analytics de site web** : pages vues et visiteurs via `beep.js`, mode sans cookie.
- **Customer insights** : parcours complet d'un visiteur (premier clic → événements), en pseudonymisé (Elite).
- **Temps réel** : flux des événements au fil de l'eau (SSE/WebSocket).
- **Radar** (H5) : alertes configurables (pic de clics, chute de conversion, lien cassé, domaine en erreur) par e-mail, Slack, webhook.
- Filtres, comparaison de périodes, export CSV, rapports e-mail planifiés (hebdo/mensuel).
- Rétention selon le plan.

### 2.10 API, webhooks et intégrations
- **API REST** versionnée `/v1`, authentification par clé API (portée par workspace, scopes `read`/`write`), pagination par curseur, idempotence (`Idempotency-Key`), limites de débit par plan, spécification **OpenAPI 3.1** publiée.
- **Webhooks temps réel** : `click`, `lead`, `sale`, `link.created`, `domain.status_changed`… Signature HMAC-SHA256, rejeu automatique avec backoff exponentiel, journal des livraisons.
- **Intégrations natives** (sans code) : Stripe, Shopify, WooCommerce, Zapier, Make, Slack, Google Analytics 4, Meta Pixel.

### 2.11 Facturation et abonnements
- Offres et quotas (§6) : **Starter (gratuit), Pro, Elite**, prix en **FCFA (XAF)** ; essai gratuit **14 jours** du plan Elite, sans paiement.
- Paiement par **Mobile Money (MTN MoMo, Orange Money)** et **carte** via un agrégateur régional, derrière le port `PaymentGateway` (adapter de référence : Notch Pay, qui gère MTN et Orange en XAF au Cameroun et au Gabon ; autres agrégateurs à comparer). Stripe reste un adapter optionnel pour les cartes internationales.
- Abonnements **prépayés** (mensuel, trimestriel, annuel), rappels d'échéance, renouvellement en un clic, période de grâce (§6.4).
- **Paiements manuels** (virement, espèces) enregistrables par l'équipe finance depuis le back office.
- Upgrade immédiat avec prorata, downgrade en fin de période.
- Quotas appliqués par des **droits (entitlements)** ; les dépassements n'interrompent **jamais** les redirections (§6.4).
- **TVA et factures** : factures PDF générées par Beepclick (numérotation séquentielle, mentions légales), TVA appliquée selon le pays du client (taux à valider avec un expert-comptable), envoi par e-mail.
- Codes promo, programme d'affiliation (§2.12) avec commissions versées par Mobile Money.

### 2.12 Site public et croissance
- Site marketing : accueil, fonctionnalités (une page par module), tarifs, blog, pages légales, documentation API.
- **Programme d'affiliation** : lien de parrainage, commission récurrente (proposition : 30 % pendant 12 mois), tableau de bord affilié.
- Blog et **feuille de route publique** alimentés depuis le back office.
- Communauté (Discord) et formulaire de demande de démo.
- SEO technique : sitemap, balises Open Graph, pages multilingues (FR/EN dès la v1, i18n prévue pour d'autres langues).

---

## 3. Périmètre fonctionnel — back office `backoffice`

Accessible uniquement sur `dashboard.beepclick.app`, réservé au personnel.

| Module | Fonctions |
|---|---|
| **Accès staff** | Comptes staff, **TOTP obligatoire**, sessions courtes, liste d'IP autorisées optionnelle, rôles : `superadmin`, `support`, `finance`, `trust_safety`, `content`, `readonly`. |
| **Utilisateurs & workspaces** | Recherche, fiche détaillée (plan, usage, ressources), suspension / réactivation, changement de plan manuel, **impersonation en lecture seule** (journalisée, durée limitée), export et suppression RGPD. |
| **Plans & droits** | Création et édition des plans, quotas, prix affichés, **overrides par workspace** (ex. +50 liens offerts), versionnement des plans. |
| **Facturation** | Abonnements, paiements (Mobile Money, carte, manuels), échéances à venir, impayés, MRR/ARR, coupons, remboursements, **enregistrement de paiements hors ligne**, factures, commissions d'affiliation et leurs paiements. |
| **Modération & abus** | File de signalements, détection **Google Safe Browsing**, désactivation d'un lien/page/domaine, liste noire de domaines et de motifs, slugs réservés, historique des décisions, réponses types. |
| **Catalogue d'apps (deeplinks)** | CRUD des applications reconnues (motifs d'URL, schémas iOS, intents Android, repli), test en direct, versionnement et publication. |
| **Domaines personnalisés** | Liste de tous les domaines, état DNS/TLS, relance de vérification, blocage. |
| **Métriques plateforme** | Inscriptions, activations, clics/jour, événements/jour, latence de redirection, taille des files, taille des bases, erreurs. |
| **Feature flags** | Activation par workspace, par plan ou en pourcentage. |
| **Contenu** | Gestion des articles de blog, entrées de feuille de route, annonces in-app et changelog. |
| **Support léger** | Notes internes sur un compte, historique des échanges, liens vers l'outil de tickets externe. |
| **Jobs & santé** | État des tâches de fond (rollups, webhooks, vérifications de liens), relance manuelle, santé des services. |
| **Audit** | Journal **immuable** de toute action staff (qui, quoi, quand, avant/après, IP). |

---

## 4. Architecture technique

### 4.1 Vue d'ensemble

```
                       Internet
                          │
                    ┌─────▼─────┐   TLS auto + on-demand TLS (domaines clients)
                    │   Caddy   │
                    └─┬───┬───┬─┘
   beepclick.app       │   │   │      dashboard.beepclick.app
        ┌─────────────┘   │   └─────►┌──────────────────────────┐
        ▼                 │          │ backoffice-web (Next.js) │
 ┌────────────┐   /v1/*, /{slug}     │ backoffice-api (Java)    │
 │  beep-web  │          ▼           └───────┬───────────┬──────┘
 │ (Next.js)  │   ┌──────────────┐           │           │ SQL (rôle bo_app)
 └─────┬──────┘   │  beep-core   │◄──────────┘           │
       │  API     │ (Java)       │  API admin interne    │
       └─────────►│ API + redirec│  (réseau `backend`)   │
                  │ + worker     │                       │
                  └──────┬───────┘                       │
                         │ SQL (rôle beep_app)           │
                         ▼                               ▼
                  ┌───────────────────────────────────────────┐
                  │  PostgreSQL 16  ·  bases `beep` | `backoffice` │
                  └───────────────────────────────────────────┘
```

- **Monolithe modulaire** pour `beep-core` (Java 21, Spring Boot 3) : API publique, moteur de redirection et worker partagent le même artefact. Le **rôle** est choisi par profil Spring : sans profil `worker`, API + redirection (ordonnanceur de jobs désactivé) ; avec le profil `worker`, adapters HTTP entrants désactivés et ordonnanceur actif (seul le port de gestion répond). Même image, deux conteneurs. Scission ultérieure en services distincts possible sans refonte.
- **Un seul serveur PostgreSQL livré dans le Compose**, avec **deux bases séparées** (`beep`, `backoffice`) et **deux rôles applicatifs sans aucun droit croisé** (`beep_app` ne voit pas `backoffice`, et inversement).
- **Propriétaire unique de la donnée** : seul `beep-core` lit/écrit la base `beep`. Le back office ne s'y connecte **jamais** ; il passe par l'**API admin interne** (`/internal/admin/v1`), accessible uniquement sur le réseau Docker `backend`, protégée par jeton de service.
- Le back office a **sa propre base** (`backoffice`) : staff, rôles, audit, notes, contenu éditorial, brouillons de plans.

### 4.2 Clean Architecture (règles communes aux deux projets)

Règle de dépendance : **les dépendances pointent vers l'intérieur**.

```
adapters / bootstrap  ──►  application (use cases + ports)  ──►  domain
```

| Couche (module Maven) | Contenu | Peut dépendre de | Ne doit jamais importer |
|---|---|---|---|
| `domain` | Entités, *records* / objets-valeurs, règles métier pures, erreurs de domaine, services de domaine | JDK uniquement | Spring, JDBC/jOOQ, HTTP, SDK de paiement, Jackson |
| `application` | Cas d'usage, **ports** (interfaces) entrants/sortants, DTO, frontière transactionnelle (port `TransactionRunner`) | `domain` | adapters, Spring, SQL |
| `adapter-in-*` | REST (Spring Web), handler de redirection, API admin, tâches planifiées, consommateurs de webhooks | `application`, `domain` | `adapter-out-*` |
| `adapter-out-*` | Dépôts PostgreSQL (jOOQ), passerelles de paiement (Mobile Money / cartes), e-mail, GeoIP, Safe Browsing, rendu QR, horloge | `application` (ports), `domain` | `adapter-in-*` |
| `bootstrap` | Application Spring Boot, configuration, **câblage des dépendances**, profils, workers | tout | — |

**Contrôles automatisés (deux niveaux)**
1. **Structure Maven** : les dépendances entre modules rendent les violations impossibles à compiler (ex. `domain` n'a aucune dépendance sortante).
2. **ArchUnit** dans `make test-unit` : interdit les imports de framework dans `domain`/`application`, impose le sens des dépendances, vérifie la nomenclature (`*UseCase`, `*Port`, `*Adapter`).

### 4.3 Structure du dépôt (monorepo)

```
beep-platform/
├─ Makefile
├─ docker-compose.yml
├─ docker-compose.dev.yml
├─ docker-compose.prod.yml
├─ docker-compose.e2e.yml
├─ .env.example
├─ infra/
│  ├─ caddy/Caddyfile
│  └─ postgres/
│     ├─ Dockerfile                 # postgres:16 + WAL-G (prod)
│     └─ init/01-roles-and-databases.sh
├─ scripts/                         # backup.sh, restore.sh, contract-test.sh, openapi-gen.sh
├─ contracts/
│  ├─ beep-public.openapi.yaml      # API publique /v1
│  └─ beep-admin.openapi.yaml       # API admin interne (contrat partagé)
├─ beep/
│  ├─ core/                         # Java 21 · Spring Boot 3 · Maven multi-modules
│  │  ├─ pom.xml
│  │  ├─ domain/                    # Java pur
│  │  │  └─ io/beep/core/domain/{link,bio,qr,analytics,conversion,
│  │  │                           billing,workspace,customdomain,appcatalog}
│  │  ├─ application/               # cas d'usage + ports
│  │  │  └─ io/beep/core/app/{link,analytics,conversion,billing,...}
│  │  │       (ex. CreateLinkUseCase, ResolveLinkUseCase, port/LinkRepository)
│  │  ├─ adapter-in-http/           # REST /v1 + moteur de redirection
│  │  ├─ adapter-in-admin/          # API admin interne
│  │  ├─ adapter-out-postgres/      # jOOQ, migrations Flyway, EventStore partitionné
│  │  ├─ adapter-out-{payments,mail,geoip,safebrowsing,qrrender}/
│  │  ├─ bootstrap/                 # @SpringBootApplication, config, workers
│  │  └─ Dockerfile
│  └─ web/                          # Next.js (site + espace client)
│     └─ Dockerfile
└─ backoffice/
   ├─ api/                          # Java, mêmes modules
   │  └─ adapter-out-coregateway/   # client généré depuis beep-admin.openapi.yaml
   └─ web/                          # Next.js (back office)
```

Le contrat `beep-admin.openapi.yaml` est **la source de vérité** : l'interface serveur côté `core` et le client `coregateway` côté back office sont tous deux **générés** (openapi-generator). Toute évolution est versionnée et testée des deux côtés.

### 4.4 Routage sur un seul domaine

| Hôte + chemin | Destination |
|---|---|
| `beepclick.app/`, `/pricing`, `/blog`, `/features/*`, `/docs`, `/roadmap`, `/changelog`, `/affiliates`, `/report`, `/login`, `/signup`, `/app/*`, `/legal/*`, `/_next/*` | `beep-web` (Next.js) |
| `beepclick.app/v1/*`, `/healthz`, `/beep.js`, `/_b/*` | `beep-core` (API publique, script de suivi, collecte d'événements) |
| `beepclick.app/{slug}` (tout le reste) | `beep-core` (redirection ou page bio) |
| `beepclick.app/internal/*`, `/actuator/*` | **404** (jamais exposé) |
| `{domaine client}/*` (TLS à la demande) | `beep-core` |
| `dashboard.beepclick.app/api/*` | `backoffice-api` (Java) |
| `dashboard.beepclick.app/*` | `backoffice-web` (Next.js) |

Les cookies de session sont **host-only** (pas d'attribut `Domain`) : une session du site ne donne aucun accès au back office, et inversement. `/healthz` est un simple contrôle de vivacité exposé sur le port applicatif (8080). Les endpoints Actuator (santé détaillée, métriques) tournent sur un **port de gestion séparé** (8090 pour `core` et `worker`, 8091 pour `backoffice-api`), jamais routé par Caddy.

### 4.5 Moteur de redirection (chemin critique)

```
Requête → Caddy → beep-core
  1. Résolution (host, slug) : cache Caffeine en mémoire (TTL court)
     → sinon lecture PostgreSQL (index unique sur (domain_id, slug))
  2. Contrôles : lien actif ? expiré ? mot de passe ? bloqué par la modération ?
  3. Moteur de règles : appareil/OS, pays, langue, A/B pondéré
  4. Deeplink : détection d'app (catalogue) + navigateur intégré → réponse adaptée
  5. Génération de beep_cid, ajout UTM/paramètres
  6. Réponse 302/307 (ou page intermédiaire pour la bascule d'app)
  7. Événement de clic → file bornée en mémoire → écriture par lots (asynchrone)
```

- **Objectif** : p95 < 50 ms côté serveur, aucun appel réseau externe sur le chemin chaud (cache hit : aucun accès base).
- **Threads virtuels** (Java 21, `spring.threads.virtual.enabled=true`) : forte concurrence sans passer en programmation réactive.
- **Invalidation du cache** entre instances : `LISTEN/NOTIFY` PostgreSQL (canal `link_changed`) ; un Redis dédié n'est introduit qu'en phase 5 si nécessaire.
- Les redirections **continuent de fonctionner** même si l'écriture d'événements est saturée : la file est bornée, puis les événements sont échantillonnés et une alerte est levée.
- Filtrage des bots (liste d'agents connus + heuristiques) ; IP jamais stockée en clair (§8.3).
- **Démarrage** : image JRE 21 en couches (AppCDS en optimisation ultérieure) ; le passage à GraalVM native image reste une option si le démarrage à froid devient un enjeu.

### 4.6 Données : PostgreSQL 16 livré dans le Compose

**Pourquoi PostgreSQL plutôt qu'une base embarquée** : plusieurs processus (API, worker, back office) écrivent concurremment ; la croissance prévue (événements, analytics, équipes) demande réplication, restauration à un instant précis (PITR), partitionnement et un écosystème Java mûr (Flyway, jOOQ, Testcontainers, files de jobs).

**Configuration**
- Un conteneur `postgres` (volume `pg_data`), **deux bases** `beep` et `backoffice`, rôles `beep_app` et `bo_app` créés par `infra/postgres/init/01-roles-and-databases.sh` (`REVOKE CONNECT … FROM PUBLIC` sur chaque base).
- Accès Java : **HikariCP** + **jOOQ** (SQL typé, adapté aux requêtes analytiques) ; types `uuid`, `timestamptz`, `jsonb`.
- **Migrations Flyway**, politique **forward-only / expand-contract** (pas de `down` : on ajoute, on migre, puis on supprime en deux déploiements). Exécutées par un service Compose dédié `*-migrate` (one-shot) avant le démarrage des applications, pour éviter les migrations concurrentes.
- **Jobs** : table de jobs consommée avec `FOR UPDATE SKIP LOCKED` (bibliothèque type db-scheduler/JobRunr) : rollups, vérification de santé des liens, livraison de webhooks, re-scan Safe Browsing.

**Événements et analytics**
- `click_events` : table **partitionnée par mois** (`PARTITION BY RANGE (ts)`), index **BRIN** sur `ts` et index B-tree `(link_id, ts)`. Les partitions des 2 mois à venir sont créées par un job ; clé primaire `(ts, id)` (la clé de partition doit en faire partie). **Rétention** : les événements bruts sont conservés **13 mois** pour tous les plans (`DROP` de partition, instantané et sans bloat). La fenêtre visible par le client est filtrée à la lecture selon son plan, et un job purge par lots les événements bruts hors fenêtre pour le plan Starter (gratuit, fenêtre de 30 jours) (minimisation des données). Les plans Pro et Elite conservent les **rollups sans limite de durée** tant que le compte est actif (volumétrie faible) ; Starter est purgé à 30 jours. Besoins au-delà de 13 mois d'événements bruts (offres sur mesure) : archivage Parquet sur stockage objet.
- Écriture **par lots** (JDBC batch ou `COPY`) depuis la file en mémoire.
- **Rollups** horaires/journaliers par lien/QR/page/dimension via `INSERT … ON CONFLICT DO UPDATE` ; les tableaux de bord lisent les rollups, pas les événements bruts.
- Ingestion de conversions **idempotente** : unicité `(workspace_id, idempotency_key)`.

**Sauvegarde et reprise**
- Production : **archivage WAL continu + sauvegardes de base quotidiennes** vers un stockage S3-compatible (WAL-G), `archive_timeout=60s` → **RPO ≤ 1 minute**, restauration PITR (`make restore from=<horodatage>`).
- Développement : `pg_dump` à la demande (`make backup`).
- Test de restauration automatisé chaque mois (base éphémère, vérification de cohérence).

**Chemin de montée en charge (derrière les ports, sans toucher au domaine)**
1. Réglage PostgreSQL + partitionnement (déjà prévu).
2. **Réplica de lecture** pour les requêtes analytiques et le back office.
3. **PgBouncer** (mode transaction) devant PostgreSQL quand le nombre d'instances augmente ; la connexion `LISTEN/NOTIFY` reste directe.
4. Adapter `EventStore` alternatif : **TimescaleDB** (compression, agrégats continus) ou **ClickHouse** si le volume d'événements dépasse ce que PostgreSQL absorbe confortablement (ordre de grandeur : plusieurs milliers d'événements/s soutenus).
5. PostgreSQL managé (RDS/Cloud SQL/Scaleway) en remplacement du conteneur : seule l'URL de connexion change.

### 4.7 Modèle de données (extrait)

**beep-core**
| Table | Colonnes clés |
|---|---|
| `users` | id, email, password_hash, totp_secret, status, created_at |
| `workspaces` / `memberships` | id, name, plan_id, owner_id · workspace_id, user_id, role |
| `plans` / `subscriptions` / `entitlement_overrides` | quotas JSON, prix_xaf, version · provider, provider_ref, status, billing_cycle, current_period_end · workspace_id, key, value |
| `payments` / `invoices` | id, workspace_id, provider, provider_ref, amount_xaf, method (mtn_momo / orange_money / card / manual), status, paid_at · number, pdf_url, vat_rate, issued_at |
| `links` | id, workspace_id, domain_id, slug, type, status, expires_at, password_hash, folder_id |
| `link_destinations` | link_id, kind (default/ios/android/desktop/geo/ab), url, weight, rule JSON |
| `custom_domains` | id, workspace_id, hostname, dns_status, tls_status, verified_at |
| `qr_codes` | id, workspace_id, link_id?, payload, style JSON |
| `bio_pages` / `bio_blocks` | id, slug, theme JSON, seo · page_id, type, position, config JSON, schedule JSON |
| `click_events` *(partitions mensuelles)* | id, ts, link_id, cid, visitor_hash, country, city, os, device, browser, lang, referrer, is_bot |
| `conversion_events` | id, ts, workspace_id, cid, type, value, currency, source |
| `rollups_hourly` / `rollups_daily` | resource_id, bucket, dimension, key, clicks, uniques, leads, sales, revenue |
| `api_keys` | id, workspace_id, hash, scopes, last_used_at |
| `webhooks` / `webhook_deliveries` | url, secret, events · status, attempts, next_retry_at |
| `abuse_reports` / `blocklist` | target, reason, status · pattern, type |
| `app_catalog` | id, name, url_patterns, ios_scheme, android_intent, fallback, version |
| `audit_log` | actor, action, target, diff, ts |
| `folders` / `tags` / `link_tags` | id, workspace_id, name · link_id, tag_id |
| `invitations` | id, workspace_id, email, role, token_hash, expires_at |
| `bio_leads` | id, page_id, block_id, email/fields JSON, consent, ts |
| `tracking_sites` / `tracking_pixels` | id, workspace_id, public_key, allowed_origins · provider, config JSON, link_id? |
| `alert_rules` / `notifications` | workspace_id, type, threshold, channels · id, kind, payload, sent_at |
| `integrations` | id, workspace_id, provider, credentials (chiffrés), status |
| `link_health_checks` | link_id, status_code, checked_at, ok |
| `affiliates` / `referrals` / `payouts` | user_id, code · affiliate_id, workspace_id, status · period, amount, status |
| `published_content` / `announcements` / `feature_flags` | type, slug, body, locale, published_at · title, audience, window · key, rules JSON |

**backoffice**
`staff_users`, `staff_roles`, `staff_sessions`, `audit_log` (append-only, chaînage de hachage), `account_notes`, `plan_drafts`, `content_posts`, `roadmap_items`, `announcements`, `moderation_decisions`.

### 4.8 API

**API publique `/v1`** (extrait) :
```
POST/GET/PATCH/DELETE  /v1/links                    GET /v1/links/{id}/stats
POST                   /v1/links/bulk
POST/GET/PATCH/DELETE  /v1/qr-codes                 GET /v1/qr-codes/{id}/render?format=svg|png|pdf
POST/GET/PATCH/DELETE  /v1/bio-pages                POST /v1/bio-pages/{id}/blocks
POST/GET/DELETE        /v1/domains                  POST /v1/domains/{id}/verify
POST                   /v1/events                   (ingestion de conversions)
POST                   /v1/analytics/query
POST/GET/DELETE        /v1/webhooks                 GET /v1/webhooks/{id}/deliveries
POST/GET/DELETE        /v1/api-keys
GET/POST/DELETE        /v1/workspaces/{id}/members
GET                    /v1/apps/catalog
POST                   /v1/billing/payments         GET /v1/billing/invoices
POST                   /v1/billing/webhook/{provider}   (notifications de l'agrégateur, signature vérifiée)
GET                    /v1/public/content/{type}    (blog, roadmap, annonces — lu par beep-web)
POST                   /_b/collect                  (public, clé publique + origine autorisée, CORS)
```

**API admin interne `/internal/admin/v1`** (extrait) :
```
GET    /users  /workspaces  /links  /domains  /reports
PATCH  /users/{id}/status                POST /workspaces/{id}/plan
PUT    /workspaces/{id}/entitlements     POST /workspaces/{id}/impersonation   (lecture seule, TTL)
PUT    /plans/{id}                       GET  /subscriptions   POST /payments/manual
POST   /links/{id}/disable               PUT  /blocklist  /app-catalog/{id}
PUT    /feature-flags/{key}              GET  /metrics   /jobs
POST   /gdpr/export  /gdpr/delete        GET  /internal/tls/allow?domain=…   (réservé à Caddy)
PUT    /content/{type}/{id}              GET  /affiliates/payouts   POST /coupons
```

**Flux de publication back office → site** : le back office conserve les **brouillons et le workflow éditorial** (articles, éléments de roadmap, annonces, brouillons de plans, catalogue d'apps) dans sa base. L'action « Publier » appelle l'API admin ; `beep-core` stocke la **copie publiée** (`published_content`, `plans`, `app_catalog`, `feature_flags`) qui est la seule source lue à l'exécution. `beep-web` lit le contenu via `/v1/public/content/*` (rendu statique incrémental, cache). Le site reste donc disponible même si le back office est arrêté.

Les erreurs suivent le format `application/problem+json` ; toutes les listes sont paginées par curseur.

---

## 5. Infrastructure : Docker Compose, Caddy, Makefile

### 5.1 `docker-compose.yml`

```yaml
name: beep

x-common: &common
  restart: unless-stopped
  logging:
    driver: json-file
    options: { max-size: "10m", max-file: "5" }

services:
  proxy:
    <<: *common
    image: caddy:2
    ports: ["80:80", "443:443", "443:443/udp"]
    environment:
      BEEP_DOMAIN: ${BEEP_DOMAIN}
      ACME_EMAIL: ${ACME_EMAIL}
    volumes:
      - ./infra/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    networks: [edge]
    depends_on: [core, web, backoffice-api, backoffice-web]

  # ---------- PostgreSQL (deux bases : beep, backoffice) ----------
  postgres:
    <<: *common
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ${PG_SUPERUSER}
      POSTGRES_PASSWORD: ${PG_SUPERUSER_PASSWORD}
      BEEP_DB_PASSWORD: ${BEEP_DB_PASSWORD}
      BO_DB_PASSWORD: ${BO_DB_PASSWORD}
    command: ["postgres", "-c", "max_connections=200",
              "-c", "shared_preload_libraries=pg_stat_statements"]
    volumes:
      - pg_data:/var/lib/postgresql/data
      - ./infra/postgres/init:/docker-entrypoint-initdb.d:ro
    networks: [db]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${PG_SUPERUSER}"]
      interval: 5s
      timeout: 3s
      retries: 10

  # ---------- beep-core (Java) ----------
  core-migrate:                       # one-shot : Flyway puis arrêt
    image: beep/core:${BEEP_VERSION:-dev}
    build: ./beep/core
    restart: "no"
    environment:
      SPRING_PROFILES_ACTIVE: migrate
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/beep
      SPRING_DATASOURCE_USERNAME: beep_app
      SPRING_DATASOURCE_PASSWORD: ${BEEP_DB_PASSWORD}
    networks: [db]
    depends_on:
      postgres: { condition: service_healthy }

  core:
    <<: *common
    image: beep/core:${BEEP_VERSION:-dev}
    build: ./beep/core
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/beep
      SPRING_DATASOURCE_USERNAME: beep_app
      SPRING_DATASOURCE_PASSWORD: ${BEEP_DB_PASSWORD}
      MANAGEMENT_SERVER_PORT: "8090"
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75"
      BEEP_PUBLIC_URL: https://${BEEP_DOMAIN}
      ADMIN_API_TOKEN: ${ADMIN_API_TOKEN}
      PAYMENT_PROVIDER: ${PAYMENT_PROVIDER:-notchpay}
      NOTCHPAY_PUBLIC_KEY: ${NOTCHPAY_PUBLIC_KEY}
      NOTCHPAY_PRIVATE_KEY: ${NOTCHPAY_PRIVATE_KEY}
      PAYMENT_WEBHOOK_SECRET: ${PAYMENT_WEBHOOK_SECRET}
      GEOIP_DB_PATH: /geoip/city.mmdb
    volumes:
      - ./infra/geoip:/geoip:ro
    networks: [edge, backend, db]
    depends_on:
      core-migrate: { condition: service_completed_successfully }
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8090/actuator/health/readiness"]
      interval: 10s
      timeout: 3s
      retries: 6
      start_period: 40s

  worker:                             # même image, rôle « tâches de fond »
    <<: *common
    image: beep/core:${BEEP_VERSION:-dev}
    build: ./beep/core
    environment:
      SPRING_PROFILES_ACTIVE: prod,worker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/beep
      SPRING_DATASOURCE_USERNAME: beep_app
      SPRING_DATASOURCE_PASSWORD: ${BEEP_DB_PASSWORD}
      MANAGEMENT_SERVER_PORT: "8090"
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75"
      PAYMENT_PROVIDER: ${PAYMENT_PROVIDER:-notchpay}      # rappels d'échéance, rapprochement des paiements
      NOTCHPAY_PUBLIC_KEY: ${NOTCHPAY_PUBLIC_KEY}
      NOTCHPAY_PRIVATE_KEY: ${NOTCHPAY_PRIVATE_KEY}
    networks: [edge, db]              # edge : accès sortant (webhooks, Safe Browsing, paiements)
    depends_on:
      core: { condition: service_healthy }

  web:
    <<: *common
    build: ./beep/web
    environment:
      NEXT_PUBLIC_API_URL: https://${BEEP_DOMAIN}/v1      # navigateur
      CORE_INTERNAL_URL: http://core:8080/v1              # rendu serveur (SSR)
    networks: [edge]

  # ---------- back office ----------
  backoffice-migrate:
    image: beep/backoffice-api:${BEEP_VERSION:-dev}
    build: ./backoffice/api
    restart: "no"
    environment:
      SPRING_PROFILES_ACTIVE: migrate
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/backoffice
      SPRING_DATASOURCE_USERNAME: bo_app
      SPRING_DATASOURCE_PASSWORD: ${BO_DB_PASSWORD}
    networks: [db]
    depends_on:
      postgres: { condition: service_healthy }

  backoffice-api:
    <<: *common
    image: beep/backoffice-api:${BEEP_VERSION:-dev}
    build: ./backoffice/api
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/backoffice
      SPRING_DATASOURCE_USERNAME: bo_app
      SPRING_DATASOURCE_PASSWORD: ${BO_DB_PASSWORD}
      SERVER_PORT: "8081"
      MANAGEMENT_SERVER_PORT: "8091"
      JAVA_TOOL_OPTIONS: "-XX:MaxRAMPercentage=75"
      CORE_ADMIN_URL: http://core:8080/internal/admin/v1
      CORE_ADMIN_TOKEN: ${ADMIN_API_TOKEN}
      SESSION_SECRET: ${BO_SESSION_SECRET}
    networks: [edge, backend, db]
    depends_on:
      backoffice-migrate: { condition: service_completed_successfully }
      core: { condition: service_healthy }

  backoffice-web:
    <<: *common
    build: ./backoffice/web
    environment:
      BO_API_INTERNAL_URL: http://backoffice-api:8081/api   # rendu serveur (SSR)
    networks: [edge]

networks:
  edge:         # exposé au proxy, accès sortant autorisé
  backend:      # core <-> backoffice-api, jamais publié
    internal: true
  db:           # PostgreSQL uniquement, jamais publié
    internal: true

volumes:
  pg_data:
  caddy_data:
  caddy_config:
```

**`docker-compose.dev.yml`** (surcharge) : `BEEP_DOMAIN=beep.localhost`, profil Spring `dev` (Flyway au démarrage, logs lisibles), rechargement à chaud (Spring DevTools, `next dev`), port `5432` exposé pour les outils SQL, **Mailpit** pour les e-mails, sandbox de l'agrégateur de paiement (clés de test).

**`docker-compose.prod.yml`** (surcharge) : image PostgreSQL construite depuis `infra/postgres/Dockerfile` (PostgreSQL + WAL-G), avec :

```yaml
services:
  postgres:
    build: ./infra/postgres
    command: ["postgres",
              "-c", "shared_preload_libraries=pg_stat_statements",
              "-c", "archive_mode=on",
              "-c", "archive_timeout=60",
              "-c", "archive_command=wal-g wal-push %p",
              "-c", "max_connections=200"]
    environment:
      WALG_S3_PREFIX: ${BACKUP_S3_PREFIX}
      AWS_ACCESS_KEY_ID: ${BACKUP_ACCESS_KEY}
      AWS_SECRET_ACCESS_KEY: ${BACKUP_SECRET_KEY}

  pg-backup:                          # sauvegarde de base quotidienne
    build: ./infra/postgres
    profiles: ["prod"]
    entrypoint: ["/usr/local/bin/backup-cron.sh"]   # wal-g backup-push + rétention
    networks: [db]
```

### 5.2 `infra/postgres/init/01-roles-and-databases.sh`

Exécuté une seule fois, à l'initialisation du volume `pg_data` (les mots de passe viennent des variables d'environnement du conteneur).

```bash
#!/bin/sh
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
     -v beep_pwd="$BEEP_DB_PASSWORD" -v bo_pwd="$BO_DB_PASSWORD" <<'SQL'
CREATE ROLE beep_app LOGIN PASSWORD :'beep_pwd';
CREATE ROLE bo_app   LOGIN PASSWORD :'bo_pwd';

CREATE DATABASE beep       OWNER beep_app;
CREATE DATABASE backoffice OWNER bo_app;

REVOKE ALL ON DATABASE beep       FROM PUBLIC;
REVOKE ALL ON DATABASE backoffice FROM PUBLIC;
GRANT CONNECT ON DATABASE beep       TO beep_app;
GRANT CONNECT ON DATABASE backoffice TO bo_app;
SQL
```

### 5.3 `infra/caddy/Caddyfile`

```caddyfile
{
	email {$ACME_EMAIL}
	on_demand_tls {
		ask http://core:8080/internal/tls/allow
	}
}

# --- Site + espace client + API + redirections ---------------------------
{$BEEP_DOMAIN} {
	encode zstd gzip

	handle /internal/* {
		respond 404
	}
	handle /actuator/* {
		respond 404
	}

	@web path / /pricing* /blog* /features* /docs* /roadmap* /changelog* /affiliates* /report* /login* /signup* /app* /legal* /_next/* /favicon.ico /robots.txt /sitemap.xml
	handle @web {
		reverse_proxy web:3000
	}

	handle /v1/* {
		reverse_proxy core:8080
	}

	# tout le reste : slug de lien court ou de page bio
	handle {
		reverse_proxy core:8080
	}
}

# --- Back office ----------------------------------------------------------
dashboard.{$BEEP_DOMAIN} {
	encode zstd gzip
	header {
		Strict-Transport-Security "max-age=31536000; includeSubDomains"
		X-Frame-Options "DENY"
		X-Content-Type-Options "nosniff"
		Referrer-Policy "same-origin"
		Content-Security-Policy "default-src 'self'; frame-ancestors 'none'"
	}

	# Optionnel : restreindre à une IP/VPN d'entreprise
	# @blocked not remote_ip {$BO_ALLOWED_IPS}
	# respond @blocked 403

	handle /actuator/* {
		respond 404
	}
	handle /api/* {
		reverse_proxy backoffice-api:8081
	}
	handle {
		reverse_proxy backoffice-web:3000
	}
}

# --- Domaines personnalisés des clients (TLS à la demande) ----------------
https:// {
	tls {
		on_demand
	}

	handle /internal/* {
		respond 404
	}
	handle {
		reverse_proxy core:8080
	}
}
```

### 5.4 `Dockerfile` des services Java (extrait)

```dockerfile
# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -B -q -DskipTests package \
 && java -Djarmode=layertools -jar bootstrap/target/*.jar extract --destination /layers

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER app
EXPOSE 8080 8090
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Image non-root, couches Docker séparées (dépendances / code) pour des builds rapides. Le profil `migrate` démarre Spring sans serveur web, exécute Flyway et se termine.

### 5.5 `Makefile`

```makefile
SHELL := /bin/bash
.DEFAULT_GOAL := help

COMPOSE  ?= docker compose
DEV      := $(COMPOSE) -f docker-compose.yml -f docker-compose.dev.yml
PROD     := $(COMPOSE) -f docker-compose.yml -f docker-compose.prod.yml --profile prod
ENV_FILE ?= .env
MVN_CORE := cd beep/core && ./mvnw -B
MVN_BO   := cd backoffice/api && ./mvnw -B

.PHONY: help
help: ## Affiche cette aide
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-22s\033[0m %s\n", $$1, $$2}'

# ---------- Environnement ----------
.PHONY: init up down restart logs ps clean
init: ## Crée .env à partir de .env.example
	@test -f $(ENV_FILE) || cp .env.example $(ENV_FILE)

up: init ## Démarre la stack de développement (Postgres, migrations, services)
	$(DEV) up -d --build

down: ## Arrête la stack
	$(DEV) down

restart: down up ## Redémarre la stack

logs: ## Suit les logs (make logs s=core)
	$(DEV) logs -f $(s)

ps: ## État des services
	$(DEV) ps

clean: ## Supprime conteneurs ET volumes (données perdues)
	$(DEV) down -v --remove-orphans

# ---------- Base de données (PostgreSQL + Flyway) ----------
.PHONY: migrate migrate-info migrate-validate seed db-shell
migrate: ## Applique les migrations Flyway (core + backoffice)
	$(DEV) run --rm core-migrate
	$(DEV) run --rm backoffice-migrate

migrate-info: ## État des migrations
	$(MVN_CORE) -q flyway:info -pl adapter-out-postgres
	$(MVN_BO) -q flyway:info -pl adapter-out-postgres

migrate-validate: ## Vérifie l'intégrité de l'historique de migrations
	$(MVN_CORE) -q flyway:validate -pl adapter-out-postgres
	$(MVN_BO) -q flyway:validate -pl adapter-out-postgres

seed: ## Données de démo (le profil `seed` exécute puis quitte)
	$(DEV) run --rm -e SPRING_PROFILES_ACTIVE=dev,seed core
	$(DEV) run --rm -e SPRING_PROFILES_ACTIVE=dev,seed backoffice-api

db-shell: ## Shell psql (make db-shell db=beep|backoffice)
	$(DEV) exec postgres sh -c 'psql -U "$$POSTGRES_USER" -d $(or $(db),beep)'

# ---------- Sauvegarde / restauration ----------
.PHONY: backup restore
backup: ## Dump logique des deux bases (dev) -> ./backups
	./scripts/backup.sh

restore: ## Restauration (dev : dump ; prod : PITR) make restore from=<fichier|horodatage>
	./scripts/restore.sh $(from)

# ---------- Qualité ----------
.PHONY: fmt lint test test-unit test-int test-contract test-e2e
fmt: ## Formate le code (Spotless + Prettier)
	$(MVN_CORE) -q spotless:apply
	$(MVN_BO) -q spotless:apply
	cd beep/web && npm run format
	cd backoffice/web && npm run format

lint: ## Lint (Checkstyle, Spotless, ESLint)
	$(MVN_CORE) -q spotless:check checkstyle:check
	$(MVN_BO) -q spotless:check checkstyle:check
	cd beep/web && npm run lint
	cd backoffice/web && npm run lint

test-unit: ## Unitaires : domaine, cas d'usage (fakes) + règles ArchUnit
	$(MVN_CORE) test -pl domain,application,bootstrap -Dtest='*Test,*ArchTest'
	$(MVN_BO) test -pl domain,application,bootstrap -Dtest='*Test,*ArchTest'

test-int: ## Intégration : adapters avec Testcontainers (PostgreSQL réel)
	$(MVN_CORE) verify -Pintegration
	$(MVN_BO) verify -Pintegration

test-contract: ## Tests de contrat core <-> backoffice (OpenAPI)
	./scripts/contract-test.sh

test-e2e: ## Bout en bout sur la stack Compose
	$(DEV) -f docker-compose.e2e.yml up --abort-on-container-exit --exit-code-from e2e

test: test-unit test-int test-contract ## Tous les tests rapides

# ---------- Contrats & génération ----------
.PHONY: openapi-gen
openapi-gen: ## Régénère interfaces serveur et clients depuis contracts/*.yaml
	./scripts/openapi-gen.sh

# ---------- Production ----------
.PHONY: build prod-up prod-down release
build: ## Construit les images de production
	$(PROD) build

prod-up: ## Démarre la production (avec archivage WAL-G)
	$(PROD) up -d

prod-down: ## Arrête la production
	$(PROD) down

release: lint test build ## Pipeline de release locale
	@echo "Images prêtes. Tag : $$(git describe --tags --always)"
```

### 5.6 `.env.example`

```dotenv
# Domaine (dev : beep.localhost ; le back office est sur dashboard.<domaine>)
BEEP_DOMAIN=beep.localhost
ACME_EMAIL=ops@beepclick.app
BEEP_VERSION=dev

# PostgreSQL
PG_SUPERUSER=postgres
PG_SUPERUSER_PASSWORD=change-me
BEEP_DB_PASSWORD=change-me
BO_DB_PASSWORD=change-me

# Secrets applicatifs
ADMIN_API_TOKEN=change-me          # jeton de service back office -> core
BO_SESSION_SECRET=change-me

# Paiements (Mobile Money + cartes) — clés de test en dev
PAYMENT_PROVIDER=notchpay
NOTCHPAY_PUBLIC_KEY=xxx
NOTCHPAY_PRIVATE_KEY=xxx
PAYMENT_WEBHOOK_SECRET=change-me

# Sauvegardes (production, WAL-G)
BACKUP_S3_PREFIX=s3://beep-backups/pg
BACKUP_ACCESS_KEY=
BACKUP_SECRET_KEY=
```

---

## 6. Offres et tarification

Trois offres, inspirées de la structure de la grille publique de Taap.it (gratuit → payant → premium, quotas de liens/QR/pages/domaines/membres, clics et scans illimités), adaptées au marché **FCFA (XAF)**.

> Parité fixe : **1 € = 655,957 FCFA**. Les montants en euros ci-dessous sont indicatifs.
> Les prix sont supposés **TTC** (hypothèse H6). Montants en FCFA, sans décimales.

### 6.1 Grille (propositions, prix en FCFA par mois)

| | **Starter** | **Pro** | **Elite** |
|---|---|---|---|
| **Prix mensuel** | **Gratuit** | **4 999 FCFA** (≈ 7,62 €) | **14 999 FCFA** (≈ 22,87 €) |
| Prix trimestriel (−10 %) | — | 13 500 FCFA | 40 500 FCFA |
| Prix annuel (−30 %) | — | 41 990 FCFA (≈ 3 499 / mois) | 125 990 FCFA (≈ 10 499 / mois) |
| Paiement requis pour s'inscrire | Non | Non (essai Elite 14 jours) | Non (essai Elite 14 jours) |
| **Liens courts / deeplinks** | 5 au total | 50 au total | 500 créations / mois |
| **QR codes dynamiques** | 5 au total | 50 au total | 500 créations / mois |
| **Pages link in bio** | 1 | 3 | 10 |
| **Clics et scans** | Illimités² | Illimités² | Illimités² |
| **Domaines personnalisés** | — | 1 | 10 |
| **Membres d'équipe** | 1 | 3 | 15 |
| Rétention des analytics¹ | 30 jours | Illimitée (agrégats) | Illimitée (agrégats) |
| Redirection vers l'app mobile (deeplinks, 50+ apps visées) | ✔ | ✔ | ✔ |
| Onelink (un lien pour tous les stores) | ✔ | ✔ | ✔ |
| Chemins personnalisés (slugs) | ✔ | ✔ | ✔ |
| Analytics de base (clics, appareil, pays) | ✔ | ✔ | ✔ |
| Analytics avancés (source, OS, langue, comparaison) | — | ✔ | ✔ |
| Générateur UTM + modèles | — | ✔ | ✔ |
| Personnalisation avancée des QR codes | — | ✔ | ✔ |
| Règles de lien : expiration, mot de passe | — | ✔ | ✔ |
| Règles avancées : A/B, routage géo / appareil | — | — | ✔ |
| Accès API (requêtes / minute) | — | 60 | 600 |
| Flux d'événements en temps réel | — | — | ✔ |
| **Suivi de conversion** (pixel, événements, attribution) | — | — | ✔ |
| Analytics de site web | — | — | ✔ |
| Pixels de retargeting + postbacks | — | — | ✔ |
| Webhooks | — | — | ✔ |
| Customer insights (parcours) | — | — | ✔ |
| Radar (alertes) | — | E-mail | E-mail + Slack + webhook |
| Publication d'analytics (rapports partageables) | — | — | ✔ |
| Export de données | — | CSV | CSV + JSON |
| Création en masse (CSV) | — | — | ✔ |
| Intégrations natives | — | Zapier / Make | Toutes (Shopify, WooCommerce, GA4, Meta Pixel, Slack…) |
| Journal d'audit, CSS personnalisé | — | — | ✔ |
| Badge « Propulsé par Beepclick » sur les pages bio | Obligatoire | Obligatoire | Retirable |
| **Support** | Communauté + e-mail | **WhatsApp** | Prioritaire (WhatsApp / canal privé) + accompagnement à la prise en main |

¹ **Rétention** : « illimitée » concerne les agrégats (rollups, faible volumétrie), conservés tant que le compte est actif. Les événements bruts et les *customer insights* portent sur 13 mois maximum (§4.6). Starter : 30 jours.

² **Clics et scans illimités**, sous réserve d'une **politique d'usage raisonnable** : trafic automatisé massif, attaques ou usage détourné peuvent être limités ou bloqués par la modération (§8.4).

**Hors grille** : les besoins spécifiques (volumes très élevés, SSO, SLA, intégrations sur mesure) sont traités **sur devis**, sans offre publique.

### 6.2 Deux types de quotas

| Type | Offres | Fonctionnement |
|---|---|---|
| **Stock** | Starter, Pro | Nombre maximal de liens / QR **actifs en même temps** (5 ; 50). Supprimer un lien libère une place. |
| **Créations mensuelles** | Elite | 500 liens et 500 QR **créés par cycle de facturation** ; le compteur repart à zéro au renouvellement. Les ressources existantes ne sont jamais désactivées. |

Les pages bio, domaines et membres suivent toujours un quota de **stock**. Les clés d'entitlement (`links.stock_max`, `links.monthly_creations`, `qr.stock_max`, `qr.monthly_creations`, `bio.max`, `domains.max`, `members.max`, `api.rpm`, `features.*`) sont portées par le plan et surchargeables par workspace (§3).

### 6.3 Options (add-ons, par mois)

| Option | Prix indicatif |
|---|---|
| Domaine personnalisé supplémentaire | 1 500 FCFA |
| Membre d'équipe supplémentaire | 1 000 FCFA |
| Pack +100 liens et +100 QR (stock pour Pro, créations mensuelles pour Elite) | 1 500 FCFA |

### 6.4 Règles de facturation et de quotas
1. **Essai** : 14 jours du plan **Elite** à l'inscription, sans paiement. À l'issue, retour automatique au plan **Starter** (les ressources au-delà des quotas passent en lecture seule, règle 4).
2. **Les redirections ne s'arrêtent jamais** à cause d'un quota. Les clics et scans sont illimités (usage raisonnable, note ²).
3. **Création de ressources** : au-delà du quota, la création est bloquée avec invitation à passer à l'offre supérieure (avertissement à 80 % puis 100 %) ; l'existant n'est jamais supprimé.
4. **Changement d'offre** : l'upgrade est immédiat avec prorata ; le downgrade s'applique à la fin de la période payée, et les ressources en excès passent en **lecture seule** (toujours redirigées), modifiables après réduction ou upgrade.
5. **Abonnements prépayés** : le paiement Mobile Money ne permet pas toujours le prélèvement automatique. Les périodes (mensuelle, trimestrielle, annuelle) sont donc **payées d'avance** ; la date d'échéance est visible partout.
   - **Rappels** par e-mail (et notification dans l'application) à J−7, J−3 et J−1.
   - **Renouvellement en un clic** : demande de paiement poussée vers le téléphone (USSD / confirmation Mobile Money) ou lien de paiement.
   - **Période de grâce** de 7 jours après l'échéance, avec accès complet.
   - À **J+7**, retour au plan Starter ; les ressources en excès sont en lecture seule mais **continuent de rediriger**.
   - À **J+30**, les liens en excès sont **mis en pause** (page d'information) jusqu'à régularisation ; suppression des données après **90 jours** d'impayé, avec préavis.
6. **Engagement trimestriel / annuel** : remboursement au prorata pendant 30 jours, par Mobile Money ou virement.
7. **Plans modifiables sans déploiement** : quotas, prix affichés et overrides par workspace sont pilotés depuis le back office (module *Plans & droits*), avec versionnement et publication vers `core` (§4.8).
8. **Affiliation** : commission de 30 % sur les paiements pendant 12 mois (proposition), **versée par Mobile Money** au-delà de 10 000 FCFA cumulés.
9. **Disponibilité progressive** : la grille décrit le produit cible. Chaque ligne n'est affichée comme disponible qu'une fois livrée (calendrier dans `beep-roadmap.md`, §2) ; les autres sont marquées « bientôt ». L'offre Elite est commercialisée à partir de la phase 3.
10. **Paiements manuels** : l'équipe finance peut enregistrer un paiement hors ligne (virement, espèces) depuis le back office ; il prolonge l'abonnement comme un paiement en ligne et génère la facture.

---

## 7. Exigences non fonctionnelles

| Domaine | Exigence |
|---|---|
| **Performance** | Redirection : p95 < 50 ms, p99 < 150 ms (hors réseau). Tableaux de bord : p95 < 500 ms sur 90 jours. Pages bio : LCP < 2 s en 4G, poids ≤ 150 Ko hors médias (usage mobile, connexions instables). |
| **Disponibilité** | Hôte unique (phases 0 à 4) : redirections ≥ 99,9 %, espace client ≥ 99,5 %, back office ≥ 99 %. Après mise en haute disponibilité (phase 5 : 2 nœuds minimum + PostgreSQL managé/répliqué) : redirections ≥ 99,95 %. |
| **Durabilité** | RPO ≤ 1 minute (archivage WAL continu), RTO ≤ 30 minutes (restauration PITR). Restauration testée chaque mois. |
| **Scalabilité** | Démarrage sur un hôte unique ; chemin de montée en charge documenté (§4.6) : partitionnement, réplica de lecture, PgBouncer, `EventStore` TimescaleDB/ClickHouse, PostgreSQL managé, séparation API/worker en services distincts. |
| **Observabilité** | Logs JSON structurés, métriques Micrometer/Prometheus via Actuator (port de gestion interne), traces OpenTelemetry (agent Java), profil Compose optionnel Grafana. Alertes sur latence, erreurs, retard des jobs, connexions et espace disque PostgreSQL. |
| **Accessibilité** | WCAG 2.1 AA pour le site, l'espace client et les pages bio. |
| **Internationalisation** | FR et EN dès la v1 ; textes externalisés. |
| **Compatibilité** | Deux dernières versions des navigateurs majeurs ; iOS 16+ et Android 10+ pour le comportement des deeplinks. |
| **Qualité** | Couverture JaCoCo ≥ 85 % sur `domain` et `application` ; règles ArchUnit et tests de contrat core ↔ backoffice obligatoires ; CI bloquante. |

---

## 8. Sécurité, conformité et abus

### 8.1 Authentification et accès
- Mots de passe argon2id, sessions par cookie `HttpOnly; Secure; SameSite=Lax`, rotation à chaque élévation de privilège, protection CSRF.
- Clés API stockées **hachées**, affichées une seule fois, révocables, avec portée par workspace.
- Back office : TOTP obligatoire, sessions de 8 h maximum, verrouillage après échecs répétés, impersonation lecture seule, toute action sensible exige une ré-authentification.
- Webhooks : signature HMAC-SHA256 + horodatage anti-rejeu.

### 8.2 Sécurité applicative
- Limitation de débit par IP, par clé API et par workspace (jeton seau).
- Protection **SSRF** pour toute récupération d'URL côté serveur (aperçu de lien, vérification de santé) : résolution DNS puis blocage des plages privées/loopback.
- En-têtes de sécurité (CSP, HSTS, `nosniff`, `frame-ancestors`), validation stricte des entrées, requêtes SQL préparées.
- Secrets via variables d'environnement / Docker secrets, jamais dans le dépôt.
- Analyse des dépendances et des images en CI (OWASP Dependency-Check, npm audit, Trivy).

### 8.3 Données personnelles (RGPD)
- Adresse IP **jamais stockée en clair** : empreinte salée avec sel quotidien pour compter les visiteurs uniques ; seuls pays/ville sont conservés (base GeoIP locale, aucun appel externe).
- Mode **sans cookie** disponible pour `beep.js` ; prise en charge du Consent Mode.
- Registre des traitements, **DPA** disponible ; région d'hébergement choisie selon la latence vers les utilisateurs et les exigences légales, avec CDN/edge pour les redirections ; conformité aux lois locales de protection des données (Cameroun, Gabon…) à valider avec un juriste.
- Droits des personnes : export et suppression (back office + espace client), purge selon la rétention du plan.

### 8.4 Lutte contre les abus
- Vérification Safe Browsing à la création et à la modification d'un lien, puis re-scan périodique.
- Liste noire de domaines/motifs, signalement public (`/report`), traitement dans la file *Trust & Safety*.
- **Slugs réservés** : `app, login, signup, pricing, blog, features, docs, roadmap, changelog, affiliates, report, legal, api, v1, internal, actuator, admin, dashboard, static, assets, healthz, beep.js, _b, _next, .well-known, favicon.ico, robots.txt, sitemap.xml`, plus noms de marque protégés. Cette liste doit rester **identique** aux chemins routés vers `beep-web` dans le Caddyfile (test automatisé en CI).
- Limites de création pour les comptes gratuits récents ; détection des chaînes de redirections.
- Les liens désactivés affichent une page d'avertissement sans redirection.

---

## 9. Stratégie de tests et qualité

| Niveau | Portée | Outil / règle |
|---|---|---|
| Unitaire | `domain` (règles de quotas, moteur de règles de redirection, attribution) + règles **ArchUnit** | JUnit 5, tests purs sans I/O |
| Cas d'usage | `application` avec **fakes** des ports | JUnit 5 + Mockito/fakes, pas de base de données |
| Intégration | adapters PostgreSQL (jOOQ, Flyway), HTTP, passerelle de paiement (mock/sandbox), génération QR | **Testcontainers** (PostgreSQL réel) |
| Contrat | OpenAPI core ↔ backoffice | génération de clients + tests Pact-like en CI |
| Bout en bout | création d'un lien → clic → conversion → affichage dans les stats + action staff dans le back office | stack Compose éphémère |
| Charge | moteur de redirection | k6 : palier 1 000 req/s en phase 1, cible 5 000 req/s en phase 3, sur un hôte moyen |
| Sécurité | OWASP ASVS niveau 2 sur auth, API et back office | revue avant chaque phase |

CI (GitHub Actions) : `lint → test-unit → test-int → test-contract → build images → test-e2e → scan` ; déploiement continu sur préproduction, production sur tag.

---

## 10. Glossaire
- **Deeplink** : lien qui ouvre un contenu directement dans une application native.
- **Onelink** : lien unique avec destinations différentes selon la plateforme.
- **`beep_cid`** : identifiant de clic propriétaire servant à l'attribution.
- **Entitlement (droit)** : capacité ou quota accordé à un workspace par son plan ou par un override.
- **Rollup** : agrégat pré-calculé des événements (par heure/jour et par dimension).
- **Port / Adapter** : interface définie par la couche application / implémentation concrète en périphérie.
