# Sauvegarde et restauration

## Développement : dump logique

```bash
make backup                                   # -> backups/<horodatage>-beep.dump et -backoffice.dump
make restore from=backups/20260101T100000Z    # arrête core/worker/backoffice-api, restaure, redémarre
```

## Production / staging : archivage WAL continu + PITR (WAL-G)

**Principe.** PostgreSQL archive chaque segment WAL vers le stockage S3-compatible (`archive_timeout=60` : **RPO ≤ 1 minute**).
Le service `pg-backup` pousse une sauvegarde de base par jour (03:00 UTC) et conserve les `BACKUP_RETAIN_FULL` dernières.

**Variables** (`.env.production`) : `BACKUP_S3_PREFIX`, `BACKUP_ACCESS_KEY`, `BACKUP_SECRET_KEY`, et pour un stockage non-AWS `BACKUP_S3_ENDPOINT` + `BACKUP_S3_PATH_STYLE=true`.

**Démarrage** : `make deploy-prod ENV_FILE=.env.production` pour construire, migrer et démarrer ; `make prod-up ENV_FILE=.env.production` pour démarrer les images existantes. Le projet Compose est `beep-prod` (pour une installation historique, conserver son nom avec `PROD_PROJECT`). Voir le [guide de production](production.md).

Pour les commandes manuelles, définir cette fonction Bash depuis la racine du projet (adapter `PROD_PROJECT` au nom de l’installation existante) :

```bash
prod() {
  docker compose --project-name "${PROD_PROJECT:-beep-prod}" --env-file .env.production \
    -f docker-compose.yml -f docker-compose.prod.yml --profile prod "$@"
}
```

**Contrôles** : `prod exec postgres wal-g backup-list` · `prod exec postgres wal-g wal-verify integrity`.

### Restaurer à un instant précis (PITR)

1. Arrêter les applications : `prod stop core redirector worker backoffice-api web backoffice-web`.
2. Arrêter PostgreSQL et **conserver** l'ancien volume (le renommer, ne rien supprimer avant validation).
3. Créer un volume vide, y récupérer la dernière sauvegarde de base :
   `wal-g backup-fetch $PGDATA LATEST`
4. Dans `$PGDATA/postgresql.auto.conf` :
   ```
   restore_command = 'wal-g wal-fetch %f %p'
   recovery_target_time = '2026-01-01 10:00:00+00'
   recovery_target_action = 'promote'
   ```
   puis `touch $PGDATA/recovery.signal`.
5. Démarrer PostgreSQL ; attendre « database system is ready to accept connections » et la promotion.
6. Avant de rouvrir le service, rapprocher la restauration du registre d’effacement le plus récent : événements `privacy.erase` de `trust_audit` et identifiants de `erased_workspaces`, conservés depuis l’état postérieur au point restauré. Réappliquer les effacements de compte sur l’API admin privée et vérifier l’absence des utilisateurs, ressources et statistiques concernés. Sans registre fiable, garder le service fermé et faire examiner la restauration.
7. Vérifier (`select now(), count(*) from users;` sur `beep`, `select max(seq) from audit_log;` sur `backoffice`), puis redémarrer les applications. Tester ce rapprochement d’effacements lors de la démonstration PITR de staging.

### Exigence de sortie de la phase 0

> « Restauration PITR d'une base depuis l'archivage WAL démontrée en staging. »

Cette démonstration exige un stockage S3 et un environnement de staging : **elle reste à réaliser et à consigner ici** (date, instant cible, résultat, durée). Les fichiers nécessaires sont livrés (`infra/postgres/Dockerfile`, `backup-cron.sh`, `docker-compose.prod.yml`) mais n'ont pas été exécutés contre un vrai stockage.

### Test mensuel automatisé (à maintenir en production)

Restaurer la dernière sauvegarde dans une base éphémère, lancer `make migrate-validate` puis un `select count(*)` sur les tables clés, alerter en cas d'échec ou si le retard d'archivage dépasse 1 minute.
