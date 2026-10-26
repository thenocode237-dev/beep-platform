# Exploitation phase 5

## Monter en capacité

1. Appliquer les migrations et démarrer la stack locale avec `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d`.
2. Ajouter `-f docker-compose.scale.yml` pour PgBouncer, puis reconstruire ce service. Ce pool n’expose aucun port hôte. `BEEP_DATABASE_DIRECT_URL` doit rester une URL PostgreSQL directe ; ne pas la diriger vers un pool de transactions.
3. Augmenter les API/redirectors avec `--scale core=2 --scale redirector=2`. Le nom de nœud est le hostname du conteneur ; ne pas imposer un `BEEP_NODE_ID` identique à tous les réplicas. Les tâches nommées sont sérialisées par verrou PostgreSQL, les files par leurs baux.
4. Sur PostgreSQL managé, configurer le endpoint applicatif avec `BEEP_DATABASE_URL`, le endpoint direct avec `BEEP_DATABASE_DIRECT_URL` et les secrets de déploiement. Les deux bases et rôles restent séparés. Conserver le TLS du pilote (ex. `sslmode=verify-full`) et le CA du fournisseur ; aucun script ne crée un compte cloud.
5. Pour un réplica déjà provisionné, créer un rôle de lecture limité à `rollups_daily` et `rollups_hourly`, puis définir `BEEP_READ_REPLICA_URL`, `BEEP_READ_REPLICA_USER` et `BEEP_READ_REPLICA_PASSWORD`. Les droits sont vérifiés sur le primaire avant les lectures. Les statistiques sont éventuellement cohérentes ; la console de base distingue activité de rejeu et état de réplication.
6. Relever les séries `http.server.requests`, `beep.events.queued/dropped/written`, `beep.node.heartbeat.failures`, le nombre de connexions et le retard du worker. Le p95 < 50 ms reste une cible à mesurer sur le chemin public avec un jeu de données réaliste.

## TimescaleDB

Pré-requis : sauvegarde/restauration vérifiée, PostgreSQL 16 avec une version de TimescaleDB compatible et `shared_preload_libraries=timescaledb,pg_stat_statements`, migrations core à jour, opérateur propriétaire des tables.

Exécuter `infra/timescale/enable.sql` via psql avec `ON_ERROR_STOP=1`. Le script garde la table partitionnée sous `click_events_legacy` et crée `click_events_timescale`, avec une vue de compatibilité `click_events`. Les anciens événements ne sont pas copiés et ne sont pas comptés deux fois. Les writers peuvent continuer à insérer dans la vue ; le nouvel adapter écrit dans l’hypertable. Les protections contre la réinsertion après effacement sont conservées.

Configurer `BEEP_EVENT_STORE=timescale`, redémarrer les services et vérifier insertion, agrégats, purge et effacement sur un workspace de recette. Ne pas renommer ou supprimer les anciennes partitions manuellement tant qu’une rétention active peut en avoir besoin. Un retour au writer `postgres` reste possible via la vue ; le retour au partitionnement natif exige une migration de données distincte, pas un rollback Flyway.

## Bascule assistée et régions

1. Examiner nœuds, probes, réplication, files et dernier succès des jobs. Confirmer la panne depuis une seconde source ; un heartbeat absent ne prouve pas à lui seul la panne d’une région.
2. Ouvrir une demande dans la console Entreprise avec le nœud concerné, le motif et TOTP.
3. Protéger le système contre deux writers : retirer le trafic du primaire défaillant et utiliser le mécanisme de fencing/promotion du fournisseur. Relever le LSN et la perte potentielle avant promotion.
4. Basculer les endpoints PostgreSQL directs et poolés dans le gestionnaire de secrets, puis redémarrer API, redirectors et workers. LISTEN/NOTIFY reconnecté invalide les caches.
5. Contrôler migrations, santé, rôle PostgreSQL, nouveaux writes, redirections, quotas et retrait d’un lien suspendu. Relever le RPO/RTO réellement observé.
6. Mettre à jour le routage géographique privé/public et `REDIRECT_UPSTREAMS` si nécessaire. Les upstreams doivent être sur un réseau privé authentifié ; ne pas exposer les ports core/management à Internet. Le primaire de configuration reste unique ; les caches des redirectors ne constituent pas une réplication de base.
7. Confirmer la demande après intervention, avec le résultat dans le motif. Une annulation ou une confirmation est un enregistrement d’exploitation, jamais une commande de promotion.

## SSO et reprise d’accès

Autoriser les hôtes exacts du fournisseur dans `BEEP_SSO_ALLOWED_HOSTS`. Créer un client OIDC code/PKCE RS256 et enregistrer `https://<site>/v1/auth/sso/<workspace-id>/callback` comme redirect URI exacte. Les endpoints d’autorisation, token et JWKS sont explicites ; aucune découverte vers un hôte arbitraire n’est autorisée.

Inviter les comptes avant le premier SSO et vérifier leur adresse. Le premier lien d’identité exige une adresse vérifiée par les deux parties et un membership effectif ; les connexions suivantes utilisent issuer/subject. Tester nonce, expiration et refus d’un non-membre avant de cocher « Exiger SSO ».

Une édition de contrat invalide les preuves SSO de la version précédente. Le staff autorisé peut retirer l’exigence SSO ou corriger le CIDR depuis le back-office avec motif/TOTP. Ne pas contourner les restrictions par un en-tête IP fourni directement par un client. Les frontends SSR transmettent l’adresse fournie par le proxy ; seuls les proxys privés approuvés doivent pouvoir joindre les ports applicatifs.

## Conformité

Référencer de vrais documents dans les outils de preuve ; les URL sont des références, sans téléchargement automatique. Contrôler expiration et accès au stockage des preuves. Tous les 90 jours, examiner les memberships et les comptes staff avant de consigner une approbation ou une correction nécessaire. Une décision de revue ne retire pas automatiquement un rôle : utiliser le module d’équipe ou de staff et vérifier la révocation des sessions.
