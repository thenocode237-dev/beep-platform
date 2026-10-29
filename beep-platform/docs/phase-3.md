# Phase 3 — Conversion et API

**25 octobre 2026 — développement livré et recette locale validée.** Les mesures de charge en préproduction et la validation avec les régies publicitaires restent des critères distincts du développement.

## Livré

- `beep_cid` signé, lié au workspace, au lien et à l’instant du clic. Le chemin de redirection utilise une configuration en cache et une file bornée ; les robots et préchargements n’émettent pas de reçu. Une panne de mesure ne bloque pas la navigation.
- `/beep.js` et `/_b/collect` : pages vues, `lead`, `sale`, `signup`, `subscription`, `custom:*`. Clés publiques `pk_` limitées à la collecte et aux origines exactes autorisées. Taille de collecte limitée à 16 Ko, identifiants d’événement dédupliqués.
- Mode sans cookie par défaut. Cookies first-party uniquement après `beep('consent', true)` ; retrait par `beep('consent', false)`. `data-cookieless="true"` et Global Privacy Control empêchent leur activation. Aucun identifiant visiteur n’est inféré depuis l’IP. Les chemins de pages sont conservés sans leur chaîne de requête ; seuls les cinq champs UTM prévus sont collectés séparément.
- Attribution premier/dernier clic, fenêtre inclusive de 1 à 90 jours et arbitrage déterministe des égalités. Funnel par lien, revenu et valeur par clic **par devise**, pages vues et campagnes UTM. L’identifiant visiteur est pseudonymisé par HMAC avec séparation par workspace.
- Flux SSE avec curseur date/UUID, reprise par `Last-Event-ID`, contrôle de session et de droits à chaque interrogation. L’écran conserve les 20 derniers événements et se reconnecte automatiquement.
- API `/v1` avec clés secrètes `sk_` par workspace, portées `read`, `write`, `events`, empreintes SHA-256 et révocation immédiate. Les clés ne donnent pas accès au compte, aux clés, aux paiements ni aux fonctions de confidentialité. Liens, QR, pages bio, domaines, statistiques et ingestion sont exposés ; les droits métier restent appliqués.
- Limites API partagées entre clés, persistées dans PostgreSQL : Pro **60 req/min**, Elite **600 req/min**, ajustables par workspace. Idempotence des mutations sur 24 h, conflit si le même identifiant désigne une autre requête. Les mutations réussies et leur réponse sont enregistrées dans la même transaction. Réponse 429 avec `Retry-After`.
- Webhooks transactionnels `click`, `lead`, `sale`, `signup`, `subscription`, `link.created`, `domain.status_changed` et événements personnalisés. Outbox durable, baux `SKIP LOCKED`, concurrence bornée, signature HMAC-SHA256 horodatée, huit tentatives avec backoff, journal et rejeu. Les destinations exigent HTTPS sur 443, une adresse publique vérifiée et épinglée, ainsi qu’un certificat valide ; aucune redirection HTTP n’est suivie.
- Pixels Meta, Google et TikTok par lien : page de choix avant chargement des scripts tiers, contrôle de l’origine du formulaire et respect de GPC côté serveur et navigateur. Un suivi désactivé, expiré ou indisponible laisse atteindre la destination. Postbacks **Meta CAPI** et **Google GA4 Measurement Protocol** sur les leads/ventes consentis avec référence visiteur ; secrets fournisseurs chiffrés et absents des vues et exports. Les postbacks Meta utilisent la source `other` ; GA4 reçoit un identifiant BeepClick pseudonymisé et ne rattache pas automatiquement l’événement à une session Google existante.
- Écran client FR/EN `/app/developer`, documentation publique `/docs` et `/en/docs`, spécification `/v1/openapi.json`, exemples JavaScript, Python et PHP.
- Back office `/developer` : débit d’événements, refus, volume et erreurs API, retard des livraisons, clés sans secrets, révocation, rejeu, limites et export/suppression d’un visiteur. Les actions sensibles exigent permission, motif et TOTP, puis sont auditées dans les deux systèmes. Le back office ne lit pas la base core.
- Elite ouvert à la vente dans le catalogue livré. Compteurs de créations persistants après suppression, avec remise à zéro à l’anniversaire mensuel de l’abonnement, y compris pour les paiements trimestriels/annuels. Suppression automatique du badge bio lorsque le droit effectif le permet ; invalidation du cache lors des changements de plan et d’overrides. Équipes et intégrations natives restent annoncées « bientôt ».

## Configuration et exploitation

Contrat admin **0.7.0** ; le back office exige un core ≥ 0.7. Migrations additives **V13–V16**, rejouables de zéro. Le profil `migrate` n’instancie pas les services applicatifs et n’exige aucun secret de suivi. Le contrat public est embarqué dans l’image core ; son contexte de construction est désormais la racine du monorepo.

- `BEEP_CONVERSION_SECRET` : secret indépendant de 256 bits recommandé en production. En local, Compose utilise `TOTP_ENCRYPTION_KEY` si cette variable est vide. Conserver le secret stable pendant la fenêtre d’attribution ; sa rotation invalide les anciens reçus.
- Droits : `features.conversion`, `features.webhooks`, `features.retargeting`, `api.rpm`, `collect.rpm`, `events.monthly_max`.
- Quota initial Elite : **100 000 événements/période**. Limite de collecte par défaut : **2 000 requêtes/minute/workspace**, indépendante de la limite des clés serveur. Ces valeurs techniques sont ajustables ; elles ne constituent pas une validation des coûts de l’offre.
- Conservation : conversions brutes 13 mois calendaires, touchpoints 90 jours, livraisons et compteurs d’API 30 jours, réponses d’idempotence 24 h. Agrégats quotidiens anonymes conservés sans limite pour Pro/Elite, purgés selon la rétention effective de Starter. Les visiteurs des pages sont dédupliqués par journée UTC, puis additionnés. L’export du compte exclut les secrets et identifiants visiteurs. L’effacement d’un visiteur retire aussi ses touchpoints et livraisons encore stockées ; il ne retire pas un événement déjà transmis à un fournisseur. Les agrégats anonymes ne contiennent aucun identifiant visiteur et restent conservés selon le plan.
- Métriques Prometheus `conversion.queue` et `conversion.dropped`, métriques HTTP existantes, jobs `webhooks.deliver` et `conversion.retention`.

Le script s’installe avec une clé publique et une fonction de file d’attente avant le chargement différé ; le panneau et la documentation fournissent le code à copier. La bannière de consentement du site marchand doit appeler explicitement `beep('consent', true)` après accord. Les reçus expirés, falsifiés ou issus d’un autre espace ne sont jamais attribués.

## Recette

- **265 tests Java distincts** : 217 core et 48 back office, dont 56 intégrations PostgreSQL core et 5 back office ; aucun échec. Les tests de domaine, d’application et d’architecture passent, ainsi que les nouvelles migrations jusqu’à V16. L’export de 10 001 événements et la frontière des 13 mois calendaires sont vérifiés.
- **253 contrôles de bout en bout** sur la stack : parcours existants, achat Elite **simulé**, limites mensuelles, API/scopes/idempotence/révocation, attribution et agrégats, SSE, droits visiteurs, pixels, consentement, GPC, protection contre un formulaire tiers, robots connus, pages FR/EN et menu mobile.
- **56 contrôles de contrat** contre le core réel ; les deux spécifications OpenAPI sont valides avec Redocly.
- **4 tests du script navigateur** : absence de cookie par défaut, consentement/retrait, mode forcé sans cookie et GPC.
- Typage et builds de production des deux fronts, images Compose, 34 paires de contrastes et réservation des routes vérifiés. Le scénario k6 est validé par `inspect` avec une clé factice, sans émission de trafic de charge.

Les journaux locaux sont conservés dans `phase3-core-verify.log`, `phase3-bo-verify.log`, `phase3-e2e.log`, `phase3-contract.log` et `phase3-openapi.log` (ignorés par Git).

Les tests couvrent les jeux de référence d’attribution, signatures et rejets de reçus, consentement/cookieless/GPC, quotas et déduplication, stockage et révocation des clés, baux et backoff, protection des destinations, payloads fournisseurs, droits staff, audit, migrations et architecture. Les parcours HTTP couvrent aussi les phases précédentes et l’effacement complet du compte.

Commandes : `make test-unit`, `make test-int`, `make test-contract`, `make test-web`, `make lint`, `node --test scripts/tracking-script.test.mjs`, puis `make test-e2e`. Sous Windows : `./scripts/test-e2e.ps1` sur la stack locale avec paiements sandbox.

## Critères externes et limites

- **Charge non mesurée** à 2 000 événements/s et 5 000 redirections/s. `make test-load-phase3` fournit les deux scénarios simultanés et le seuil p95 de redirection < 50 ms. Utiliser des espaces de recette dédiés, des clés publiques autorisant `COLLECT_ORIGIN`, et `SLUG` pour un lien dont le suivi est activé. Fournir `COLLECT_KEYS` comme tableau JSON. Pour un essai dans un seul espace, augmenter explicitement `collect.rpm` et `events.monthly_max` ; une limite de plan ne doit pas être confondue avec la capacité du pipeline.
- **Pas d’appel réel aux régies** pendant cette recette : formats, signatures et consentement sont testés localement. Vérifier les diagnostics Meta/Google/TikTok, les hôtes régionaux autorisés par la CSP et la réception effective avec les comptes et identifiants de production. GA4 peut accepter HTTP sans garantir l’apparition dans les rapports.
- **Pas de recette visuelle interactive ni sur appareils physiques** : aucun navigateur pilotable n’était connecté. Les builds, le typage et les réponses HTTP des écrans sont vérifiés ; mobile, lecteurs d’écran et intégrations réelles restent à contrôler.
- Les réserves de lancement de la [phase 2](phase-2.md) continuent de s’appliquer. Aucune production n’a été déployée et le mode d’inscription local reste sur invitation.

Références fournisseurs : [Consent Mode Google](https://developers.google.com/tag-platform/security/guides/consent), [consentement TikTok](https://business-api.tiktok.com/gateway/docs/index?doc_id=1795929012554754), [GA4 Measurement Protocol](https://developers.google.com/analytics/devguides/collection/protocol/ga4/sending-events?client_type=gtag), [exemple officiel Meta](https://github.com/fbsamples/lead-ads-webhook-sample/blob/main/postman/FB%20Conversions%20API%20%28Part%201%20-%20online%29.postman_collection.json).

La [documentation CSP de Google](https://developers.google.com/tag-platform/security/guides/csp) sert de référence pour les hôtes autorisés par les balises publicitaires ; leurs diagnostics restent à vérifier dans l’environnement de production.
