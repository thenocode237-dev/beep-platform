# Phase 5 — Industrialisation

Implémentation locale terminée le 26 octobre 2026, contrat admin/public **0.9.0**. Cette phase ajoute les capacités logicielles et les procédures d’exploitation. L’infrastructure managée, les régions supplémentaires, les contrats signés et les audits externes dépendent des environnements et prestataires choisis.

## Services et capacité

- Trois rôles de processus : `core` (API), `redirector` (liens, QR et bio) et `worker`. Le proxy envoie les API au premier et les redirections au second. Un filtre refuse aussi les routes d’un autre rôle lors d’un accès direct. Aucun port applicatif ou de gestion n’est publié sur l’hôte en production.
- Les redirectors gardent leur cache Caffeine et l’invalidation par LISTEN/NOTIFY. Ces notifications, les migrations et les verrous de jobs utilisent `BEEP_DATABASE_DIRECT_URL`, même si les requêtes applicatives passent par PgBouncer.
- `docker-compose.scale.yml` fournit PgBouncer en mode transaction. Les paramètres JDBC désactivent les prepared statements nommés sur ce chemin. Le rôle de la base du back-office reste séparé.
- La limitation de débit d’authentification est atomique en PostgreSQL : plusieurs API comptent les mêmes tentatives, avec une horloge commune et des clés hachées. Les quotas API partagés de phase 3 restent actifs. Les jobs utilisent une connexion directe et un verrou consultatif PostgreSQL par nom de tâche ; les files gardent leurs baux SKIP LOCKED.
- Un réplica optionnel, configuré avec `BEEP_READ_REPLICA_*`, sert les lectures des agrégats. Une panne de lecture revient au primaire. Les permissions, mutations, états de suspension et redirections restent sur le primaire pour éviter une autorisation ou une destination obsolète.
- Le registre des nœuds enregistre toutes les trente secondes le rôle, la région et la capacité mémoire/CPU. Un signal âgé de plus de 90 secondes est marqué en retard. La console montre aussi la taille de base, les connexions et `pg_stat_replication`. L’âge du dernier rejeu sur une base inactive ne mesure pas, à lui seul, la perte de données.
- Une bascule assistée possède une demande, une confirmation ou une annulation, avec motif, permission et TOTP. La demande exige un nœud sans signal récent. Elle enregistre une intervention ; elle ne promeut pas PostgreSQL et ne modifie pas le DNS.
- `REDIRECT_UPSTREAMS` peut contenir plusieurs upstreams privés. Caddy contrôle leur santé et réessaie les autres. Un vrai déploiement multi-région exige encore le réseau privé, le routage géographique et une stratégie de primaire unique : voir le [runbook](runbook-industrialisation.md).

### EventStore TimescaleDB

Le port existant `ClickEventRepository` accepte un adapter TimescaleDB, sans modifier le domaine. PostgreSQL natif reste le défaut. Sur une base équipée de l’extension, `infra/timescale/enable.sql` garde les anciennes partitions et installe un hypertable ainsi qu’une vue de compatibilité. Les agrégations, les anciens writers, la rétention et l’effacement gardent le même chemin logique. `BEEP_EVENT_STORE=timescale` sélectionne l’adapter ; le démarrage échoue si l’hypertable manque.

La bascule est explicite et transactionnelle, après les migrations normales, pendant une fenêtre d’intervention. Aucun script de cette phase ne convertit automatiquement une base de production. Compression et agrégats continus ne sont pas activés par défaut ; ils demandent une mesure de volume et une validation de la rétention et des effacements.

## Offre entreprise

Parcours client : `/app/enterprise`, accessible également depuis « Plus ». Parcours staff : `/enterprise`.

- Contrats versionnés par workspace : référence, validité, état, SLA en points de base, référence et empreinte du DPA signé, rétention de 1 à 3 650 jours, CIDR IPv4/IPv6, nom et couleur de marque. Les éditions concurrentes sont refusées. Le registre stocke la preuve de signature fournie ; Beep ne signe pas juridiquement le DPA.
- Les plans non publics, overrides de quotas et paiements manuels utilisent les modules existants Plans et Facturation ; les écrans entreprise y donnent accès.
- Les restrictions IP couvrent les sessions et les clés API. Les routes de réception des fournisseurs conservent leur propre signature. La liste IP et l’exigence SSO ne disparaissent pas silencieusement à l’échéance commerciale. Le staff peut corriger une politique qui a bloqué l’accès.
- La rétention contractuelle active remplace celle du plan pour les statistiques et conversions. La maintenance conserve les partitions nécessaires aux contrats longs. Les empreintes de visiteur et les touches d’attribution gardent leurs durées courtes existantes.
- Le nom de marque est affiché dans l’espace client, avec une couleur décorative qui ne change pas les contrastes des contrôles. Le badge bio est supprimé pendant un contrat actif portant une marque distincte. La personnalisation du domaine et des thèmes bio utilise les capacités existantes.

### SSO OIDC

Code d’autorisation avec PKCE S256, état lié à un cookie HttpOnly, nonce et état serveur consommé une seule fois. Le jeton ID est vérifié cryptographiquement en RS256 : clé publiée par le fournisseur, issuer, audience/azp, dates et nonce. Les requêtes ont des limites de temps et de taille et ne suivent aucune redirection HTTP. Les hôtes IdP doivent d’abord être approuvés dans `BEEP_SSO_ALLOWED_HOSTS` par l’opérateur.

Le secret client est chiffré. Aucun auto-enrôlement : la première connexion exige une adresse vérifiée correspondant à un membre déjà vérifié et autorisé, puis l’identité issuer/subject est liée au compte. Le TOTP personnel reste obligatoire lorsqu’il est activé. Les preuves de connexion SSO sont liées à la session, au workspace et à la version du contrat ; logout, révocation de membership et changement de version retirent la capacité d’accès. Les clés API restent des identités de service, soumises à la liste IP, aux portées et aux quotas.

Le SSO livré est **OIDC**. Un IdP SAML doit passer par une passerelle SAML→OIDC administrée par l’organisation ; aucun parseur SAML natif n’est fourni. Les fournisseurs ne parlant que d’autres algorithmes de signature doivent être adaptés avant activation.

## Deferred deeplink et SDK

Les administrateurs d’un workspace sous contrat actif déclarent une application, son schéma et ses URL Google Play/App Store. Une demande consentie crée un jeton opaque, haché en base, avec destination chiffrée et expiration de 24 heures. La résolution est atomique, à usage unique et liée à l’application et à la plateforme. Désactiver l’application révoque les jetons encore présents. La suppression du compte efface les données associées.

Android transmet le jeton via Install Referrer. iOS utilise un code explicitement conservé et fourni par la personne après installation. Aucun rapprochement probabiliste, identifiant publicitaire, lecture automatique du presse-papiers ou fingerprinting n’est utilisé. Un échec réseau après consommation peut nécessiter un nouveau jeton ; les SDK ne rejouent pas automatiquement une résolution.

Les sources des SDK TypeScript, Kotlin/Android et Swift/iOS sont dans [sdk/mobile](../sdk/mobile/README.md). Le registre staff garde plateforme, version, URL, empreinte SHA-256 et statut. Il permet de déprécier/révoquer une version sans modifier l’archive de cette version. Il ne publie pas de paquet npm, Maven ou Swift et ne force pas de mise à jour sur les appareils.

## Traductions et conformité

Le workflow éditorial accepte **FR, EN, ES et PT**, avec un brouillon et une publication versionnée par langue. Le journal propose le choix de langue et sert le contenu traduit, sans traduction automatique. Le chrome du site et l’espace client gardent leurs interfaces FR/EN ; les langues supplémentaires portent sur le contenu éditorial.

Les preuves de SOC 2, ISO 27001, audit externe ou pentest sont référencées avec une empreinte, une échéance, un auteur et un motif. Les revues d’accès prennent un snapshot des membres/rôles réels, une décision et une échéance à 90 jours. Les revues staff restent dans la base du back-office et sont append-only. Les actions sont soumises à des permissions départementales et à TOTP, avec audit. Aucun résultat d’audit indépendant, rapport de pentest ou certificat n’est simulé.

## Déploiement et validation

Migrations core **V20–V21**, back-office **V5–V6**. V20/V5 sont l’expansion ; V21/V6 élargissent les contraintes de langue. Les writers FR/EN antérieurs restent compatibles. Déployer les migrations puis le core 0.9, le redirector/worker, le back-office qui exige au moins 0.9, puis les interfaces. Les migrations déjà appliquées des phases précédentes restent inchangées.

La recette est suivie dans `phase5-*.log` (fichiers locaux ignorés) : 349 tests Java réussis, dont 99 tests d’intégration, 379 contrôles HTTP de bout en bout, 65 contrôles de contrat et 12 tests Node réussis. Les deux interfaces passent le typage et le build de production. Les essais PostgreSQL, TimescaleDB et PgBouncer ont réussi. Les descriptions OpenAPI sont valides ; les deux avertissements concernent les réponses de redirection des routes SSO. Le dernier redémarrage des services a abouti et leurs contrôles de santé passent.

Restent à réaliser dans les environnements concernés : fournisseur OIDC réel, SDK sur appareils physiques, revue visuelle interactive, réplica et bascule sur l’hébergement retenu, charge en préproduction, restauration et exercice multi-région, signature des contrats, audit/pentest externes et certification éventuelle.

Références techniques : [OpenID Connect Core](https://openid.net/specs/openid-connect-core-1_0.html), [vérification JWT Spring Security](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [API create_hypertable TimescaleDB](https://docs.timescale.com/api/latest/hypertable/create_hypertable/).
