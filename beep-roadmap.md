# Beepclick — Roadmap de développement (plateforme + back office)

Ce document planifie l'évolution **simultanée** de `beep` et de `backoffice`, en Clean Architecture, sur Docker Compose, avec les deux projets sous le même domaine (`beepclick.app` et `dashboard.beepclick.app`).
Il complète `beep-specs.md`.

## Direction d’interface — mise à jour du 24 octobre 2026

La [refonte Signal](beep-platform/docs/refonte-atelier.md) applique à Beepclick une direction inspirée de Vendeer : nouvelle identité, accueil argumenté autour du partage et de la destination des liens, médias locaux, parcours de compte et back office harmonisés. La [charte](beep-platform/docs/design-system.md) devient la référence des prochaines phases.

Les nouvelles interfaces sont conçues dès 320 px : formulaires prioritaires sur mobile, cibles de 44 à 52 px, CTA fixe sur l’accueil et utilisateurs en cartes. Le typage, les builds des deux fronts et les contrastes sont vérifiés ; captures et résultats navigateur sont documentés dans la recette. Les essais sur appareils physiques et les mesures Lighthouse restent ouverts.

Les aperçus marketing restent illustratifs. L’état des liens, QR codes, pages bio, statistiques et évolutions suivantes est suivi dans les documents de phase ; la refonte ne modifie pas leur logique.

---

## 1. Principes de pilotage

1. **Deux pistes, un seul incrément.** Chaque jalon livre une fonctionnalité côté `beep` **et** son outil d'exploitation côté `backoffice`. Une fonctionnalité sans outil d'administration n'est pas « terminée ».
2. **Contrat d'abord.** Toute interaction core ↔ back office passe par `contracts/beep-admin.openapi.yaml`. On modifie le contrat en premier, on génère les clients/serveurs, puis on implémente. Les tests de contrat sont bloquants en CI.
3. **Clean Architecture dès le premier jour.** Domaine pur → cas d'usage + ports → adapters → composition. Les règles de dépendance sont garanties par la structure Maven (modules) et contrôlées par **ArchUnit** (`make test-unit`).
4. **Tranches verticales.** On livre de petites fonctionnalités bout en bout (domaine → API → UI → back office → tests) plutôt que des couches complètes.
5. **Publier, pas partager la base.** Le back office garde brouillons et workflow ; « Publier » pousse une copie vers `core` via l'API admin (plans, catalogue d'apps, feature flags, puis contenu éditorial). Le runtime ne lit que `core`.
6. **Déployable en permanence.** `main` est toujours déployable sur préproduction ; la production se déploie sur tag.
7. **Les redirections sont sacrées.** Aucune évolution ne doit dégrader la latence ou la disponibilité du moteur de redirection (budget de performance vérifié en CI).

### Définition de « terminé » (DoD) — valable pour chaque tranche
- [ ] Règles métier couvertes par des tests unitaires (domaine et cas d'usage).
- [ ] Tests d'intégration des adapters concernés.
- [ ] Contrat OpenAPI mis à jour et tests de contrat verts.
- [ ] Écran ou action correspondant dans le **back office** (ou justification écrite si non applicable).
- [ ] Action staff associée **journalisée** dans l'audit.
- [ ] Migrations Flyway **rétro-compatibles** (expand/contract, forward-only), validées par `make migrate` et `make migrate-validate`, rejouées sur PostgreSQL réel (Testcontainers).
- [ ] Métriques et logs ajoutés ; alertes mises à jour si nécessaire.
- [ ] Documentation (README du module, changelog).
- [ ] Revue de sécurité allégée (entrées, droits, données personnelles).

### Organisation proposée
- Cycles de **2 semaines**, démonstration commune beep + back office à chaque fin de cycle.
- Équipe type : 2 développeurs backend Java, 1 développeur front (Next.js) site/espace client, 1 développeur Next.js/full-stack back office, 1 produit/design à mi-temps, 1 ops/DevOps à mi-temps. Les durées ci-dessous supposent cette taille ; elles doublent à peu près avec une équipe de moitié.

---

## 2. Vue d'ensemble des phases

| Phase | Durée | Objectif | Sortie publique |
|---|---|---|---|
| **0 — Fondations** | S1–S2 | Squelettes Clean Architecture, Compose, CI, Makefile, auth | Aucune |
| **1 — MVP** | S3–S8 | Liens courts, redirection, QR, link in bio v1, analytics de base, plan Starter (gratuit) | **Bêta privée** |
| **2 — Monétisation & deeplinks** | S9–S14 | Plan Pro, paiements Mobile Money, domaines perso, deeplinks, Onelink, modération | **Lancement public** (Starter / Pro) |
| **3 — Conversion & API** | S15–S22 | Pixel, événements, attribution, API, webhooks | **Lancement de l'offre Elite** |
| **4 — Équipes & écosystème** | S23–S30 | Workspaces d'équipe, intégrations, extension Chrome, Radar, affiliation | Elite complet (15 membres, intégrations) |
| **5 — Industrialisation** | S31+ | Scalabilité, SSO, offres sur mesure, i18n supplémentaires, deferred deeplink | Offres sur mesure (hors grille) |

### Disponibilité des lignes de la grille tarifaire

La grille de `beep-specs.md` (§6.1) décrit le produit cible. Le site n'affiche une ligne comme disponible qu'une fois livrée ; les autres sont marquées « bientôt » (statut piloté depuis le back office via les plans versionnés).

| Lignes de la grille | Livrées en |
|---|---|
| Liens, QR dynamiques, link in bio, analytics de base, export CSV, quotas (stock), plan Starter (gratuit) | Phase 1 |
| Plan Pro et paiements Mobile Money, essai Elite, domaines personnalisés, deeplinks, Onelink, règles expiration/mot de passe, UTM, QR avancés, support WhatsApp | Phase 2 |
| Suivi de conversion, analytics de site web, pixels de retargeting, webhooks, API (Pro et Elite), limites de débit par plan, quotas de créations mensuelles, **commercialisation de l'offre Elite** | Phase 3 |
| Membres d'équipe, intégrations natives, Radar, temps réel, santé des destinations, customer insights, rapports planifiés, affiliation, 15 membres et intégrations natives (Elite complet) | Phase 4 |
| SSO, SLA, offres sur mesure, deferred deeplink | Phase 5 |

---

## 3. Détail par phase

### Phase 0 — Fondations (S1–S2)

| Piste `beep` | Piste `backoffice` | Transverse |
|---|---|---|
| Squelette **Java 21 / Spring Boot 3** en Clean Architecture (modules Maven `domain`, `application`, `adapter-in-*`, `adapter-out-*`, `bootstrap`), profils `prod` / `worker` / `migrate` / `seed`, règles ArchUnit | Même squelette (module `adapter-out-coregateway` généré depuis le contrat admin) | Monorepo, `Makefile`, `docker-compose.yml` + surcharges dev/prod |
| **PostgreSQL 16 dans le Compose** (2 bases, 2 rôles sans droits croisés), jOOQ + HikariCP, **Flyway** via service one-shot `core-migrate` | Base `backoffice` (rôle `bo_app`), service `backoffice-migrate` | **Caddy** : routage `beep.localhost` / `dashboard.beep.localhost`, réseaux `backend` et `db` internes |
| Auth : inscription, connexion, sessions, TOTP, **e-mails transactionnels** (vérification, réinitialisation ; Mailpit en dev) | Auth staff : TOTP obligatoire, rôles de base, session courte, **journal d'audit append-only** (écriture) | CI : lint + règles d'architecture, tests unitaires, build des images |
| `healthz`, logs JSON, métriques | API admin interne v0 : `GET /users`, jeton de service | Contrats OpenAPI initiaux + génération de clients |
| Site Next.js minimal (accueil, login, signup), **i18n FR/EN** dès le départ | Back office **Next.js** : login, layout, page « utilisateurs » (lecture) | `pg_dump`/restauration en dev, archivage WAL (WAL-G) configuré en staging |

**Critères de sortie**
- `make up` démarre toute la stack en une commande, `make test` est vert.
- Un utilisateur s'inscrit sur `beep.localhost` ; un membre du staff le voit sur `dashboard.beep.localhost`.
- Une violation de règle d'architecture fait échouer la CI.
- Restauration PITR d'une base depuis l'archivage WAL démontrée en staging.

---

### Phase 1 — MVP (S3–S8)

> **État (24 octobre 2026)** : les trois cycles sont implémentés côté `beep` et `backoffice` (contrat admin 0.2.0) — détail, décisions et écarts dans [`beep-platform/docs/phase-1.md`](beep-platform/docs/phase-1.md). Restent les critères de sortie mesurés hors du poste de développement : bêta de 100 invités, palier de 1 000 req/s en préproduction, restauration vérifiée.

**Cycle 1 (S3–S4) : liens courts et redirection**
- `beep` : entité `Link`, création/édition, slugs (personnalisés, générés, réservés), **moteur de redirection** avec cache Caffeine (invalidation `LISTEN/NOTIFY`), threads virtuels, écriture d'événements par lots, page 404.
- `backoffice` : recherche de liens, **désactivation d'un lien**, liste des slugs réservés.
- Perf : test de charge k6 en CI (budget p95 < 50 ms).

**Cycle 2 (S5–S6) : analytics de base et QR codes**
- `beep` : événements de clic (appareil, OS, pays, référent, bots filtrés), rollups horaires/journaliers par le worker, partitions mensuelles des événements et job de purge de rétention (13 mois bruts, durée du plan pour les rollups), tableau de bord des liens, **export CSV** des statistiques, **QR codes** dynamiques/statiques avec export SVG/PNG/PDF.
- `backoffice` : métriques plateforme v1 (inscriptions, clics/jour, taille des bases, retard du worker), vue des jobs.

**Cycle 3 (S7–S8) : link in bio v1 et quotas**
- `beep` : pages bio (blocs lien/bouton/titre/texte/image/réseaux sociaux), thèmes de base, SEO, rendu SSR avec cache, connexion **OAuth Google/GitHub** et lien magique ; **domaine `Plan` + `Entitlements`** : plan **Starter** gratuit (5 liens, 5 QR, 1 page, clics et scans illimités, analytics 30 jours).
- `backoffice` : **module Plans & droits** (lecture + overrides par workspace), fiche workspace (usage vs quotas), suspension de compte, écran de consultation du **journal d'audit** (alimenté depuis la phase 0).

**Critères de sortie**
- Bêta privée de 100 utilisateurs, **sur invitation uniquement** (pas de modération automatisée avant la phase 2), avec taux d'activation mesuré.
- p95 de redirection < 50 ms à 1 000 req/s sur l'hôte de préproduction.
- Toute action staff est dans l'audit ; sauvegarde/restauration vérifiées.

---

### Phase 2 — Monétisation et deeplinks (S9–S14)

> **État (25 octobre 2026)** : développement des cycles 4–6 livré et recette locale validée (231 tests Java, 53 contrôles de contrat, 191 contrôles de bout en bout). Le lancement public reste soumis aux paiements réels, validations fiscales/légales, configuration des prestataires et tests sur appareils physiques — voir [`beep-platform/docs/phase-2.md`](beep-platform/docs/phase-2.md).

**Cycle 4 (S9–S10) : facturation**
- `beep` : port `PaymentGateway` + adapter **Mobile Money / cartes** (agrégateur régional, ex. Notch Pay : MTN MoMo, Orange Money), abonnements **prépayés** (mensuel / trimestriel / annuel), rappels d'échéance J−7/J−3/J−1, renouvellement en un clic, période de grâce, factures PDF ; plan **Pro**, essai **Elite** 14 jours, quotas de stock appliqués avec avertissements 80 % / 100 % (§6 des specs).
- `backoffice` : **vue Facturation** (abonnements, paiements, échéances à venir, MRR/ARR, impayés), **paiements manuels** (virement/espèces) avec facture, coupons, changement de plan manuel, édition et **versionnement des plans** sans déploiement (brouillon dans `backoffice`, **publication vers `core`** via l'API admin).

**Cycle 5 (S11–S12) : domaines personnalisés**
- `beep` : ajout de domaine, vérification DNS, **TLS à la demande** (endpoint `ask` de Caddy), domaine par défaut/par lien, page racine et 404 personnalisées.
- `backoffice` : liste globale des domaines, état DNS/TLS, relance de vérification, blocage d'un domaine.

**Cycle 6 (S13–S14) : deeplinks et modération**
- `beep` : détection d'app, schémas iOS / intents Android, bascule hors navigateurs intégrés, simulateur de comportement, **Onelink** (destinations iOS/Android/desktop), règles avancées (expiration, mot de passe, géo/appareil, A/B), constructeur UTM, site marketing complet (fonctionnalités, tarifs, légal, blog en Markdown versionné dans `beep/web`).
- `backoffice` : **catalogue d'apps** (CRUD, test en direct, publication versionnée), **file de modération** (signalements, Safe Browsing, liste noire), feature flags v1, **export et suppression de compte (RGPD)** côté staff, avec le parcours équivalent dans l'espace client.

**Critères de sortie — lancement public**
- Paiements réels en production, factures conformes, TVA et factures conformes générées par Beepclick (taux validés avec un expert-comptable) ; test de bout en bout d'un paiement MTN MoMo et Orange Money en production.
- ≥ 25 apps populaires dans le catalogue, testées sur iOS/Android réels.
- Processus d'abus opérationnel (délai de traitement cible < 24 h).
- Droits RGPD (export et suppression de compte) opérationnels.
- Page de statut publique.

---

### Phase 3 — Conversion et API (S15–S22)

> **État (25 octobre 2026)** : développement livré et recette locale validée côté `beep` et `backoffice` (contrat admin 0.7.0). Les mesures de charge à 2 000 événements/s et 5 000 redirections/s, les comptes publicitaires réels et la recette sur appareils restent ouverts — voir [`beep-platform/docs/phase-3.md`](beep-platform/docs/phase-3.md).

**Cycle 7–8 (S15–S18) : suivi de conversion**
- `beep` : `beep_cid`, script **`beep.js`** (pixel, pages vues, mode sans cookie), API/ingestion d'événements (`lead`, `sale`, `custom`), **moteur d'attribution** (dernier/premier clic, fenêtres), funnel clics → leads → ventes, revenu par lien, UTM avancé, analytics de site web.
- `backoffice` : monitoring du pipeline d'événements (débit, rejets, retard), outils de **RGPD au niveau visiteur** (export/suppression de données pseudonymisées), contrôle des quotas d'événements.

**Cycle 9–10 (S19–S22) : API et webhooks**
- `beep` : **API publique `/v1`** documentée (OpenAPI), clés API par workspace et par portée, limites de débit par plan, idempotence ; **webhooks** signés avec rejeu et journal ; pixels de retargeting et postbacks (Meta, Google, TikTok).
- `backoffice` : gestion des clés API (révocation), **journal des livraisons de webhooks**, limites de débit ajustables par workspace, tableau de bord API (volume, erreurs).

**Critères de sortie**
- Attribution vérifiée sur des scénarios de référence (jeux de tests dorés).
- Ingestion soutenue de 2 000 événements/s et redirection à 5 000 req/s sans dégradation de la latence (p95 < 50 ms).
- Documentation API publique avec exemples dans 3 langages.
- **Offre Elite commercialisée** : suivi de conversion, flux temps réel, webhooks, API à 600 req/min, quotas de créations mensuelles ; les lignes « 15 membres » et « intégrations natives » restent marquées « bientôt » jusqu'à la phase 4.

---

### Phase 4 — Équipes et écosystème (S23–S30)

> **État (25 octobre 2026)** : développement des cycles 11–14 livré côté `beep` et `backoffice` (contrat admin 0.8.0), avec recette locale et extension chargeable en mode développeur. Les comptes fournisseurs réels, la publication Chrome Web Store, la recette interactive, la charge et les critères produit (NPS, rétention, cinq clients Elite) restent à valider — voir [`beep-platform/docs/phase-4.md`](beep-platform/docs/phase-4.md).

**Cycle 11–12 (S23–S26) : équipes et intégrations**
- `beep` : rôles `owner/admin/editor/viewer`, invitations, dossiers/tags, journal d'audit du workspace ; intégrations **Stripe, Shopify, WooCommerce, Zapier, Make, Slack, GA4** ; création en masse (CSV) et import/export.
- `backoffice` : rôles staff granulaires (`support`, `finance`, `trust_safety`, `content`), **impersonation lecture seule**, supervision des intégrations, gestion des erreurs d'intégration.

**Cycle 13–14 (S27–S30) : Radar, extension, croissance**
- `beep` : **extension Chrome** « Ajouter à Beepclick », **Radar** (alertes), flux temps réel (SSE), vérification de santé des destinations, customer insights (plan Elite), rapports e-mail planifiés, blocs bio avancés (capture d'e-mail, formulaire, vidéo/audio), **programme d'affiliation**.
- `backoffice` : **gestion de contenu** (blog, en remplacement du Markdown versionné ; feuille de route publique ; annonces), paiements d'affiliation, métriques de cohortes et de rétention, support léger (notes, historique).

**Critères de sortie**
- Offre Elite complète (15 membres, intégrations natives, Radar).
- NPS ≥ 40, rétention à 90 jours mesurée, au moins 5 clients Elite actifs.

---

### Phase 5 — Industrialisation (S31+)

> **État (26 octobre 2026)** : capacités logicielles implémentées côté `beep` et `backoffice` (contrat 0.9.0), avec recette locale, PgBouncer et adapter TimescaleDB vérifiés. SSO OIDC, contrats versionnés, politiques IP/rétention, SDK de transfert différé, traductions éditoriales et registres de conformité livrés. Hébergement managé, véritable multi-région, fournisseur SSO et appareils mobiles réels, audits et certifications restent des validations externes — voir [`beep-platform/docs/phase-5.md`](beep-platform/docs/phase-5.md).

| Axe | Contenu `beep` | Contenu `backoffice` |
|---|---|---|
| **Scalabilité** | Séparation `redirector` / `api` / `worker` en conteneurs distincts ; **réplica de lecture**, **PgBouncer**, adapter `EventStore` **TimescaleDB ou ClickHouse** derrière le port existant (aucun changement de domaine) ; PostgreSQL managé ; multi-région pour la redirection | Vue des nœuds et de la réplication, bascule assistée, indicateurs de capacité |
| **Offres sur mesure** | SSO SAML/OIDC, SLA, DPA signé, IP allowlist par workspace, rétention sur mesure | Gestion des contrats sur mesure, plans sur mesure, facturation manuelle |
| **Produit** | Deferred deeplink + SDK mobile, i18n supplémentaires, marque blanche | Outils de traduction du contenu, gestion des versions de SDK |
| **Conformité** | Audit de sécurité externe, préparation SOC 2 / ISO 27001, tests d'intrusion | Preuves de conformité, revues d'accès périodiques |

---

## 4. Calendrier synthétique (par cycles de 2 semaines)

```
Cycle  S      beep                              backoffice
0      1-2    Fondations, auth                  Auth staff, squelette, audit
1      3-4    Liens + redirection               Recherche / désactivation de liens
2      5-6    Analytics + QR                    Métriques plateforme, jobs
3      7-8    Link in bio + quotas (Starter)    Plans & droits, consultation audit                ── Bêta privée
4      9-10   Mobile Money, plan Pro            Facturation, paiements manuels, plans versionnés
5      11-12  Domaines perso + TLS              Domaines globaux, blocage
6      13-14  Deeplinks, Onelink, règles, UTM   Catalogue d'apps, modération, RGPD compte         ── Lancement public
7-8    15-18  Conversion, beep.js, attribution  Pipeline événements, RGPD visiteur
9-10   19-22  API, webhooks, retargeting        Clés API, livraisons webhooks                     ── Lancement Elite
11-12  23-26  Équipes, intégrations             Rôles staff, impersonation
13-14  27-30  Radar, extension, affiliation     Contenu, affiliation, cohortes                    ── Elite complet
15+    31+    Scalabilité, SSO, sur mesure      Capacité, contrats                                ── Offres sur mesure
```

---

## 5. Gestion des dépendances entre les deux projets

| Sujet | Règle |
|---|---|
| **Versionnement du contrat admin** | SemVer sur `beep-admin.openapi.yaml`. Changement **additif** : un cycle suffit. Changement **cassant** : nouvelle version `/internal/admin/v2` + période de coexistence d'un cycle. |
| **Ordre de livraison** | 1) contrat fusionné, 2) `beep-core` expose l'endpoint, 3) `backoffice` consomme. Le back office ne peut jamais être déployé avant la version du core dont il dépend (vérifié par un test de compatibilité au démarrage). |
| **Données** | Le back office ne lit/écrit jamais la base de Beepclick. Toute donnée métier passe par l'API admin. |
| **Environnements** | `dev` (Compose local) → `staging` (même Compose, données anonymisées) → `prod`. Les migrations des deux bases sont jouées séparément et indépendamment. |
| **Incidents** | Si `beep-core` est indisponible, le back office reste accessible en mode dégradé (données propres : audit, notes, contenu) et affiche l'état du core. |
| **Sécurité croisée** | Jeton de service de l'API admin rotatif (rotation trimestrielle, double jeton pendant la transition), appels admin journalisés des deux côtés. |

---

## 6. Risques et parades

| Risque | Impact | Probabilité | Parade |
|---|---|---|---|
| Écriture massive d'événements saturant PostgreSQL | Élevé | Moyenne | Écritures par lots (`COPY`/batch), partitions mensuelles, rollups, tests de charge dès la phase 1, **port `EventStore`** prêt pour TimescaleDB/ClickHouse, réplica de lecture |
| Deeplinks instables (changements des schémas d'URL des apps) | Élevé | Élevée | Catalogue versionné et éditable sans déploiement, tests réguliers sur appareils réels, repli web systématique |
| Abus (phishing, spam via liens courts) | Élevé | Élevée | Safe Browsing, liste noire, limites sur comptes récents, file de modération dès le lancement public |
| Couplage excessif entre beep et back office | Moyen | Moyenne | API admin contractuelle, tests de contrat, interdiction d'accès direct à la base |
| Conformité RGPD (pixel, IP, cookies) | Élevé | Moyenne | Hachage d'IP, mode sans cookie, DPA, outils d'export/suppression dès la phase 3 |
| Domaines personnalisés : émission TLS massive, limites Let's Encrypt | Moyen | Moyenne | Endpoint `ask` strict (domaine vérifié uniquement), limitation de débit, alerte sur les échecs |
| Sauvegardes inutilisables le jour J | Élevé | Faible | Restauration automatisée testée chaque mois, alerte si le retard de réplication dépasse 1 minute |
| Dépendance à une seule équipe sur le moteur de redirection | Moyen | Moyenne | Documentation d'architecture, revue croisée, budget de performance en CI |
| Sous-estimation de la phase Conversion | Moyen | Moyenne | Jeux de tests d'attribution dorés, découpage en deux cycles, jalon intermédiaire à S18 |
| Dépendance à un agrégateur de paiement, échecs ou délais Mobile Money, absence de prélèvement automatique | Élevé | Élevée | Port `PaymentGateway` avec second adapter prêt, abonnements prépayés + rappels + période de grâce, rapprochement automatique des paiements, paiements manuels depuis le back office |

---

## 7. Indicateurs par phase

| Phase | Indicateurs clés |
|---|---|
| 1 | Taux d'activation, temps jusqu'au premier lien, p95 de redirection, erreurs 5xx |
| 2 | Conversion Starter (gratuit) → payant, MRR, taux de paiement réussi, délai de traitement des abus, taux de repli deeplink |
| 3 | Part des workspaces avec conversion activée, événements/jour, latence d'ingestion, adoption API |
| 4 | Nombre d'équipes, intégrations activées par compte, revenus d'affiliation, NPS |
| 5 | Disponibilité, coût par million de clics, délai de restauration, nombre de contrats sur mesure |

---

## 8. Prochaines actions immédiates (semaine 1)

1. Valider les hypothèses H1 à H7 du document de spécifications (PostgreSQL dans le Compose, stack Java/Next.js, « Radar », grille tarifaire, nom).
2. Réserver le nom de domaine, la marque et les comptes (compte marchand chez l'agrégateur de paiement Mobile Money, avec vérification des frais, plafonds et délais de reversement ; hébergeur ; stockage de sauvegarde).
3. Créer le monorepo, la CI et `make init && make up` (phase 0).
4. Rédiger le contrat `beep-admin.openapi.yaml` v0.1 (utilisateurs, workspaces, plans).
5. Décider de l'hébergement initial (un serveur dédié ou VPS UE avec volumes persistants) et du stockage S3-compatible pour l'archivage WAL (WAL-G).
6. Constituer la liste des 25 applications prioritaires du catalogue de deeplinks.
