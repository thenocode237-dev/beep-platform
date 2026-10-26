# Accès privé au backoffice

En production, seul un appareil connecté au réseau Tailscale et autorisé par la politique réseau accède à `https://beep-admin.<réseau>.ts.net`. Le mot de passe, le TOTP obligatoire, les rôles et les audits de Beep restent en vigueur. L’espace client `https://beep.click/app` conserve son accès public.

## 1. Créer le réseau dédié

Créer un compte/réseau Tailscale dédié à Beep avec l’identité de son propriétaire. Activer la protection MFA de cette identité. Dans DNS, activer MagicDNS et les certificats HTTPS ; relever le suffixe DNS réel du réseau, par exemple `tail123.ts.net`. Installer Tailscale sur l’ordinateur et les appareils mobiles des administrateurs et les connecter à ce réseau.

Dans Access controls, **remplacer** la politique initiale par [`infra/tailscale/policy.hujson`](../infra/tailscale/policy.hujson), après remplacement de toutes les occurrences de `owner@your-company.invalid` par le compte réel du propriétaire. Vérifier les tests intégrés avant d’enregistrer. La politique initiale « allow all » doit disparaître. Ajouter explicitement les autres comptes autorisés à `group:beep-staff`. Les autres utilisateurs ne reçoivent aucun accès au serveur.

La seule autorisation est `group:beep-staff` → `tag:beep-admin` sur TCP 443. Le serveur ne doit être ni partagé hors du réseau, ni configuré comme exit node ou routeur de sous-réseau. Aucun attribut `funnel` ne doit être accordé. Conserver cette politique en place lors des ajouts ultérieurs de services.

## 2. Inscrire le serveur

Dans Keys, créer une **auth key**, préautorisée, à usage unique, **non éphémère**, avec le tag `tag:beep-admin`. Depuis Bash, à la racine du projet, enregistrer la clé sans la mettre dans l’historique, une ligne de commande ou `.env.production` :

```bash
install -d -m 700 secrets
read -r -s -p "Clé d'inscription Tailscale : " BEEP_ENROLLMENT_KEY
printf '\n'
(umask 077; printf '%s' "$BEEP_ENROLLMENT_KEY" > secrets/tailscale-authkey)
unset BEEP_ENROLLMENT_KEY
chmod 600 secrets/tailscale-authkey
```

Sous Windows, restreindre aussi les permissions NTFS du fichier au compte d’exploitation. `secrets/` est ignoré par Git et par le contexte de build Docker. Le fichier est monté comme secret ; `containerboot` le lit via `TS_AUTHKEY=file:/run/secrets/tailscale_authkey`. La valeur de la clé n’apparaît pas dans `docker compose config` ou dans les arguments du processus.

Si le chemin est personnalisé, conserver le fichier dans `secrets/` ou en dehors du dépôt : `prod-check` refuse un secret placé ailleurs dans le contexte de build.

Dans `.env.production`, renseigner le suffixe réel :

```dotenv
BO_PUBLIC_URL=https://beep-admin.tail123.ts.net
TAILSCALE_AUTHKEY_FILE=./secrets/tailscale-authkey
```

Le nœud sera nommé `beep-admin`. Réserver ce nom : un ancien appareil portant déjà ce nom peut produire un suffixe `-1` et fera échouer la vérification finale. Le fichier d’inscription doit rester présent pour Compose ; après sa première utilisation, une clé à usage unique ne peut plus inscrire un autre appareil. Le volume `tailscale_state` conserve l’identité lors des redémarrages. Ne pas le supprimer. Pour réinscrire un serveur après perte de son état, révoquer l’ancien nœud et fournir une nouvelle clé.

Autoriser les connexions sortantes nécessaires à Tailscale (contrôle et relais HTTPS, DNS ; UDP sortant pour les connexions directes). Aucun port entrant supplémentaire n’est publié. Le déploiement ne crée pas le compte Tailscale ni la politique à votre place.

## 3. Déployer et contrôler

Suivre le [guide de production](production.md) pour les autres variables, les sauvegardes et le nom historique du projet Compose, puis lancer :

```bash
make prod-check ENV_FILE=.env.production
make deploy-prod ENV_FILE=.env.production
make prod-vpn-check ENV_FILE=.env.production
make prod-logs ENV_FILE=.env.production s=backoffice-vpn
```

Les images utilisent Tailscale `v1.104.2` en mode userspace, sans privilèges réseau ni `/dev/net/tun`. L’API et le front du backoffice sont retirés du réseau public ; seul le VPN dispose d’un réseau sortant dédié. La passerelle Caddy partage l’espace réseau du VPN et écoute **uniquement** `127.0.0.1:8080`. Serve termine HTTPS sur le réseau privé et transmet vers cette socket ; `/api/*` et l’interface partagent la même origine. Les routes de gestion et internes restent bloquées. Les en-têtes d’identité Tailscale ne remplacent jamais l’authentification Beep.

Le proxy public ne dépend d’aucun service privé. Les services publics sont démarrés/rechargés avant l’attente du VPN. L’ancien domaine du dashboard répond 404 en HTTP et dans le routage HTTPS générique ; si aucun certificat n’existe plus, sa connexion TLS peut être refusée avant HTTP. Ne jamais rétablir la route publique comme solution de secours.

## 4. Recette avec les vrais appareils

Ces contrôles exigent le réseau Tailscale et les comptes réels ; les tests locaux ne peuvent pas les remplacer.

1. Depuis un appareil autorisé, ouvrir l’URL privée : certificat HTTPS valide, connexion avec mot de passe puis TOTP, navigation et chargement des ressources. Dans le navigateur, contrôler les cookies de session `Secure`, `HttpOnly`, `SameSite=Strict`, et les restrictions d’un rôle sans droits d’écriture.
2. Hors VPN, puis depuis un compte du réseau absent de `group:beep-staff`, vérifier l’impossibilité de joindre l’interface et `/api/*`. Retirer temporairement un compte de test du groupe et confirmer la révocation sur une nouvelle connexion.
3. Depuis Internet, vérifier que `dashboard.beep.click/login` et `/api/*` n’exposent aucune page d’administration, même en forçant le serveur d’origine. Exemple : `curl --resolve dashboard.beep.click:443:IP_DU_SERVEUR https://dashboard.beep.click/login` ; refus TLS ou 404 attendu. Avec le SNI public : `curl -H 'Host: dashboard.beep.click' https://beep.click/api/auth/session` ; 404 attendu.
4. Redémarrer `backoffice-vpn` et `backoffice-gateway` avec le même projet et les mêmes volumes ; relancer `make prod-vpn-check`. Vérifier que l’identité et l’URL restent identiques, puis arrêter temporairement ces deux services et confirmer que `beep.click`, `/app`, les API publiques et les liens personnalisés fonctionnent toujours. Les redémarrer avec `make prod-up`.
5. Après réussite, supprimer l’ancien enregistrement DNS public `dashboard.beep.click`. Se reconnecter au backoffice sur la nouvelle adresse ; aucun mot de passe, secret TOTP ou donnée métier n’est migré.

Pour révoquer un administrateur : le retirer du groupe Tailscale, révoquer ses appareils perdus et désactiver son compte staff Beep. En cas d’accès privé indisponible, utiliser la console sécurisée du serveur ou le compte propriétaire Tailscale pour réparer le tunnel, sans publier le backoffice. Le propriétaire conserve un accès de récupération à son fournisseur d’identité.

## Vérifications automatiques

```bash
node --test scripts/prod-check.test.mjs scripts/backoffice-vpn-check.test.mjs
# Docker requis ; utilise uniquement des réseaux et conteneurs temporaires :
node --test scripts/backoffice-network.test.mjs
```

Les tests réseau utilisent les routes Caddy réelles avec des services factices et une autorité TLS locale. Ils vérifient le routage privé, les en-têtes, la liaison loopback, les refus HTTP/HTTPS de l’ancien domaine et le fonctionnement du public lors d’une panne privée. Aucun compte Tailscale ni secret de production n’est utilisé.

Références : [Serve privé](https://tailscale.com/docs/features/tailscale-serve), [paramètres Docker](https://tailscale.com/docs/features/containers/docker/docker-params), [lecture des secrets dans containerboot](https://github.com/tailscale/tailscale/blob/v1.104.2/cmd/containerboot/main.go), [grants réseau](https://tailscale.com/docs/reference/grants).
