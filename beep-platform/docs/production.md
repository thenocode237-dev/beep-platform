# SEO et déploiement en production

## Préparer le serveur

Exécuter les commandes depuis la racine `beep-platform`, sur l’hôte qui doit héberger la production. Prérequis : Docker avec Compose v2 récent (`--wait` et `--wait-timeout`), Node ≥ 20, GNU Make et Bash. Sous Windows, utiliser Git Bash avec Node dans le `PATH`.

Configurer les enregistrements DNS de `BEEP_DOMAIN` et `dashboard.BEEP_DOMAIN` vers le serveur et ouvrir les ports 80/TCP et 443/TCP/UDP. Caddy gère les certificats HTTPS. Les ports 80 et 443 doivent être libres ; deux projets Compose distincts ne peuvent pas les publier simultanément sur la même adresse.

```bash
cp .env.example .env.production
make init ENV_FILE=.env.production
chmod 600 .env.production
```

Renseigner dans `.env.production` :

| Variables | Configuration attendue |
|---|---|
| `BEEP_DOMAIN`, `BEEP_PUBLIC_URL`, `BO_PUBLIC_URL` | Domaine sans schéma, puis `https://<domaine>` et `https://dashboard.<domaine>` sans sous-chemin |
| `ACME_EMAIL`, `BEEP_VERSION` | Adresse d’exploitation réelle et identifiant de release stable, par exemple `v1.0.0` ; ni `dev` ni `latest` |
| Mots de passe PostgreSQL, `ADMIN_API_TOKEN`, clés TOTP | Secrets générés par `make init` ; conserver ceux d’une installation existante |
| `BEEP_CONVERSION_SECRET` | Secret indépendant de 32 octets base64 ; `openssl rand -base64 32` |
| `SMTP_*`, `MAIL_FROM` | Serveur SMTP réel, identifiants et TLS selon le fournisseur, expéditeur autorisé |
| `PAYMENT_PROVIDER`, `NOTCHPAY_*` | `notchpay` et clés du fournisseur ; la sandbox est refusée par le profil serveur `prod` |
| `BILLING_SELLER_*` | Identité, adresse et identifiant fiscal de facturation |
| `URL_SAFETY_PROVIDER`, `WEB_RISK_API_KEY` | `web_risk` et clé Google Web Risk |
| `LEGAL_*` | Éditeur, adresse, immatriculation, contact et hébergeur |
| `BACKUP_S3_PREFIX`, `BACKUP_ACCESS_KEY`, `BACKUP_SECRET_KEY` | Dépôt `s3://bucket/chemin` et accès WAL-G ; paramètres endpoint/région si nécessaire |

Adapter aussi `SIGNUP_MODE`, les contacts de support, les fournisseurs OAuth et `DOMAINS_CNAME_TARGET` aux fonctions utilisées. Les vérifications locales ne valident pas les accès aux fournisseurs : effectuer leurs recettes avec les comptes réels.

Le fichier de secrets est ignoré par Git et exclu du contexte Docker. L’URL publique, non secrète, est transmise au build du site pour que les pages pré-générées utilisent le même domaine que le rendu serveur.

PostgreSQL et le service de sauvegarde disposent du réseau sortant `backup-egress` pour joindre S3 ; le réseau de données reste interne et aucun port PostgreSQL n’est publié sur l’hôte.

## Vérifier et déployer

```bash
make prod-check ENV_FILE=.env.production
make deploy-prod ENV_FILE=.env.production
```

`prod-check` examine la configuration Compose résolue en mémoire, sans afficher les valeurs des secrets et sans démarrer de service. Il échoue sur les valeurs locales, les secrets absents, les clés de chiffrement mal formées ou un décalage du domaine entre le build et le runtime.

`deploy-prod` exécute séquentiellement la vérification, le build des images, le démarrage de PostgreSQL, les deux migrations Flyway, le démarrage de la stack avec attente de santé, puis le rechargement de Caddy. Toute commande en échec arrête la suite. Les dépendances Compose conservent leur propre vérification des migrations ; une nouvelle exécution Flyway sur un schéma à jour est sans effet. Aucun volume n’est supprimé.

Le projet Compose de production s’appelle **`beep-prod`**. Les commandes de développement conservent le projet `beep`. Pour une production déjà installée sous un autre nom, fournir **le même nom historique** à toutes les commandes, par exemple `PROD_PROJECT=beep` : changer le nom crée de nouveaux volumes et ne migre pas les anciennes données.

```bash
make prod-ps ENV_FILE=.env.production
make prod-logs ENV_FILE=.env.production s=core
# Si le serveur démarre lentement :
make deploy-prod ENV_FILE=.env.production DEPLOY_TIMEOUT=300
```

Avant chaque mise à jour, vérifier les sauvegardes et leur restauration selon le [runbook](runbook-backup.md). Les migrations suivent la politique expand/contract existante. Un déploiement sur un seul hôte peut interrompre brièvement le service ; cette commande n’implémente pas de bascule sans interruption ni de retour arrière automatique de la base. En cas d’échec, consulter les logs, corriger puis relancer avec les mêmes secrets et le même nom de projet.

## Vérifier le SEO après déploiement

Les pages publiques FR/EN ont des titres et descriptions localisés, une canonique propre à chaque page, des liens `hreflang` correspondants et des métadonnées Open Graph/Twitter. L’accueil contient les données structurées `Organization` et `WebSite`. Les articles publiés disposent de leurs propres métadonnées ; aucun lien de traduction n’est inventé entre des slugs différents.

`/sitemap.xml` liste les pages publiques et les articles FR/EN publiés. Les articles sont chargés à la requête : si l’API éditoriale est indisponible, les pages fixes restent listées. Les dates de publication réelles sont utilisées, sans date de modification artificielle. `/robots.txt` annonce ce sitemap. Les routes de compte, de connexion et de signalement renvoient `X-Robots-Tag: noindex, nofollow` ; l’espace client et le back-office ont aussi une métadonnée `noindex`. Les pages bio clientes conservent leurs réglages SEO existants.

```bash
curl -fsS https://<domaine>/robots.txt
curl -fsS https://<domaine>/sitemap.xml
curl -fsS https://<domaine>/en/features
curl -sSI https://<domaine>/login
```

Vérifier que les canoniques, les `hreflang`, les URL du sitemap et les aperçus de partage utilisent le domaine HTTPS final. Soumettre ensuite le sitemap dans Google Search Console et Bing Webmaster Tools. Un changement de domaine nécessite de reconstruire l’image du site.

Références : [métadonnées Next.js](https://nextjs.org/docs/app/getting-started/metadata-and-og-images), [sitemap Next.js](https://nextjs.org/docs/app/api-reference/file-conventions/metadata/sitemap), [démarrage Compose et santé des services](https://docs.docker.com/reference/cli/docker/compose/up/).
