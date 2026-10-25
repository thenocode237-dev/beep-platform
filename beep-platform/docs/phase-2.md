# Phase 2 — Monétisation et deeplinks

**25 octobre 2026 : développement livré et recette locale validée. Lancement public non validé.**

Contrat admin : **0.6.0** (additif ; le back office exige un core ≥ 0.6). Les critères de lancement dépendant de prestataires, d’informations légales et d’appareils physiques restent ouverts ci-dessous.

## Livré

### Cycle 4 — facturation
- Plans Pro ouvert à la vente, prix trimestriel −10 % / annuel −30 % (arrondis à 10 FCFA), plans versionnés (brouillon dans `backoffice`, publication vers `core` avec TOTP, détection de publication concurrente).
- Abonnements prépayés, upgrade immédiat avec crédit du temps restant, essai Elite 14 jours, rappels J−7/J−3/J−1, grâce 7 jours, retour à Starter.
- Paiement : port `PaymentGateway`, adapters `notchpay` (MTN MoMo, Orange Money, cartes) et `sandbox`. Le fournisseur fait foi (webhook signé → vérification → règlement idempotent) ; rapprochement toutes les minutes.
- Factures `BEEP-AAAA-NNNNNN` sans trou, TVA par pays, PDF + e-mail. Coupons, paiements manuels, changement de plan manuel.
- Back office : Facturation (MRR/ARR, échéances, impayés, paiements), Coupons, éditeur de plans, paiement manuel et changement de plan depuis la fiche utilisateur. Espace client : page « Abonnement ».

### Cycle 5 — domaines personnalisés
- Ajout (quota `domains.max`), vérification DNS (TXT de propriété + CNAME/A), TLS à la demande (`/internal/tls/allow` pour Caddy), domaine par défaut, page racine et chemin inconnu redirigés.
- Slugs uniques **par domaine** (migration V8 « contract »), routage par hôte en cache invalidé par `LISTEN/NOTIFY`.
- Back office : liste globale, état DNS/TLS, relance, blocage (motif + TOTP). Espace client : page « Domaines », choix du domaine à la création d'un lien.

### Cycle 6 — règles de lien, deeplinks et exploitation
- Règles par lien : expiration, mot de passe (formulaire, 10 essais / 10 min), ciblage pays / appareil / langue, test A/B pondéré, Onelink iOS/Android/ordinateur, UTM sans écrasement. Droits par plan (`features.link_rules`, `link_routing`, `utm`).
- Deeplinks : catalogue de 30 apps (migration V9), intent Android, page intermédiaire iOS et navigateurs intégrés (Instagram, Facebook, TikTok…), simulateur. Back office : catalogue (publication versionnée, test sur 7 profils).
- Espace client : formulaire de règles sur la fiche du lien (expiration locale, mot de passe, Onelink, UTM, ciblage et A/B selon les droits), simulateur accessible depuis cette fiche.
- Modération : formulaire public `/report`, file staff avec compteurs ouverts et retard > 24 h, classement ou blocage avec motif et TOTP, liste noire couvrant les sous-domaines et destinations alternatives. Un signalement de lien court est associé à sa destination. La réactivation d’un lien respecte la liste noire.
- Contrôle des destinations dans le worker, hors du chemin de redirection : Web Risk par défaut, Safe Browsing en option non commerciale. Les erreurs et l’absence de configuration sont distinctes d’un résultat sans menace. Les résultats d’un scan devenu périmé après modification du lien sont ignorés.
- Feature flags publiés et audités : règles de lien et simulateur, déploiement progressif stable, contrôle de version concurrente. Les droits du plan restent appliqués.
- Export portable et suppression de compte dans les paramètres et la fiche staff. Confirmation e-mail de 15 minutes à usage unique et TOTP si activé pour la suppression client ; permissions dédiées et TOTP côté staff. Sessions, ressources, registre des slugs et statistiques retirés ; factures et paiements facturés conservés dans une archive séparée. Les secrets et empreintes visiteurs sont exclus de l’export.
- Liens en excès après échéance impayée : pause à J+30, suppression à J+90 avec au moins sept jours après un préavis effectivement envoyé. Une panne SMTP reporte la suppression. Un renouvellement ou une réduction d’usage annule le traitement des liens régularisés ; les suspensions de modération sont conservées.
- QR avancés Pro/Elite : modules carrés, arrondis ou points, dégradé contrasté, logo PNG/JPEG local limité à 64 Ko et 512 × 512, cadre avec « SCANNEZ » ou « SCAN ME ». Correction H avec logo, marge adaptée, exports SVG/PNG/PDF ; modules vectoriels conservés en PDF. Les neuf combinaisons forme/cadre avec logo et dégradé sont décodées par ZXing dans les tests.
- Site FR/EN : fonctionnalités, tarifs lus depuis les plans publiés, mentions légales, confidentialité, conditions, journal Markdown versionné et page de statut avec contrôles HTTP réels. Support WhatsApp depuis les paramètres, activable avec un numéro configuré ; aucune conversation n’est envoyée automatiquement.

## Recette exécutée

- **186 tests Java core** et **45 tests Java back office**, dont **42 intégrations PostgreSQL core** et **5 intégrations PostgreSQL back office**, sans échec. Migrations rejouées de zéro et validées jusqu’à V12.
- **53 contrôles de contrat** contre le core réel, version 0.6.0.
- **191 contrôles de bout en bout** sur la stack Compose : anciens parcours, pages FR/EN, paiements **simulés** MTN/Orange, abonnement, facture PDF, QR avancés, règles, simulateur, flags et conflit de version, signalement/blocage/déblocage, exports et suppression avec révocation des sessions.
- Typage et builds de production des deux fronts, contrastes des tokens et réservation des routes vérifiés.
- Recette navigateur interactive et appareils physiques non exécutée : aucun navigateur pilotable n’est disponible dans cette session. Le statut public vérifie la disponibilité HTTP, sans prétendre valider un paiement ou un certificat personnalisé.

Les compilations Java ont été réalisées dans une copie isolée des sources dans Docker pour éviter les artefacts `target` locaux périmés. Les tests PostgreSQL utilisent une instance jetable séparée. La recette Windows utilise un rôle temporaire limité à la table d’audit ; elle révoque ce rôle et désactive son compte staff de test en fin de passage.

Commandes habituelles : `make test-unit`, `make test-int`, `make test-contract`, `make test-web`, `make lint`, puis `make test-e2e`. Sous Windows : `./scripts/test-e2e.ps1` depuis PowerShell, sur la stack locale avec `PAYMENT_PROVIDER=sandbox`.

## Configuration livrée

- Paiements : `PAYMENT_PROVIDER`, `NOTCHPAY_*`, `BILLING_*`, désormais transmis par Compose au core et au worker. La sandbox reste interdite sous le profil `prod`.
- Sécurité des URL : `URL_SAFETY_PROVIDER=web_risk` et `WEB_RISK_API_KEY`. L’option `safe_browsing` utilise `SAFE_BROWSING_API_KEY`. [Google réserve Safe Browsing aux usages non commerciaux](https://developers.google.com/safe-browsing/v4/usage-limits) ; [Web Risk fournit l’API de recherche commerciale](https://docs.cloud.google.com/web-risk/docs/lookup-api).
- Mentions publiques : `LEGAL_PUBLISHER`, `LEGAL_ADDRESS`, `LEGAL_REGISTRATION`, `LEGAL_CONTACT`, `LEGAL_HOST`. Les pages n’inventent pas les coordonnées manquantes.
- Support : `SUPPORT_WHATSAPP` (numéro international sans `+`), `SUPPORT_EMAIL`.
- Migrations V10–V12 additives ; le nom « contract » de V10 correspond à la protection contre `TRUNCATE`, celui de V11 à l’élargissement du CHECK des jetons, comme V5. Aucune donnée existante n’est supprimée par ces migrations.

## Réserves à lever avant le lancement public
- Adapter Notch Pay **jamais exécuté** contre leur bac à sable ; test de bout en bout MTN MoMo et Orange Money en production requis.
- Taux de TVA (CM 19,25 %, GA 18 %) et mentions légales du vendeur : à valider avec un expert-comptable.
- Schémas iOS et intents Android du catalogue : valeurs de départ, **non testées sur appareils réels** (critère : ≥ 25 apps testées).
- Renseigner et vérifier les identifiants de production, les coordonnées légales et le numéro WhatsApp ; activer le fournisseur de sécurité et confirmer son fonctionnement avec une clé réelle.
- Tester DNS et émission TLS sur un domaine public réel ; organiser la permanence de modération et mesurer le délai < 24 h.
- Faire valider les textes, la durée de conservation des pièces comptables et la gestion des effacements après restauration. La recette locale ne constitue pas une validation juridique.
- Publier et superviser la page de statut sur l’hébergement de production. Aucun lancement public ni changement de `SIGNUP_MODE` n’a été effectué ici.
