# BeepClick Deferred SDK — 0.1.0

Sources de référence pour un transfert explicite après installation : [TypeScript](typescript/index.mjs), [Android/Kotlin](android/BeepDeferred.kt), [iOS/Swift](ios/BeepDeferred.swift).

## Préparer le parcours

Créer une application dans `/app/enterprise` (contrat actif). Déclarer son schéma et ses deux URL de boutique. Après consentement, POST `/v1/mobile/deferred/<app-id>` avec `platform`, `destination` et `consent:true`. Le résultat contient `token`, `storeUrl` et `expiresAt`. L’endpoint est public mais limité en débit ; il ne doit jamais recevoir de secret de compte ou de clé API.

Le jeton est valable 24 heures et consommable une seule fois, pour la même application et plateforme. La destination doit appartenir au schéma déclaré. Ne pas mettre d’informations personnelles ou de secrets dans cette destination. Les jetons ne sont jamais mis dans une URL de résolution ni dans les logs du SDK.

## Android

Ajouter `BeepDeferred.kt` au module Android, avec `kotlinx-coroutines-android` et la permission réseau habituelle. Le code utilise les API Android `Uri`/`JSONObject` et exécute la requête dans `Dispatchers.IO`.

Lire le referrer fourni par le client officiel Google Play Install Referrer, après consentement dans la page d’installation, puis appeler `BeepDeferred.tokenFromInstallReferrer(referrer)`. Si le jeton existe, appeler `BeepDeferred(origin, appId, scheme).resolve(token)`. Le SDK retourne une URI à vérifier et traiter dans le routeur de l’application ; il ne lance pas lui-même une activité.

## iOS

Ajouter `BeepDeferred.swift` à une cible disposant de URLSession async/await (iOS 15+). Demander à la personne de saisir ou de coller explicitement le code conservé avant installation, puis appeler `try await BeepDeferred(origin:appId:scheme:).resolve(token:)`. Ne pas lire automatiquement le presse-papiers. Le SDK retourne une URL interne, sans l’ouvrir.

L’App Store ne transmet pas ce jeton d’installation : ce parcours iOS est un transfert volontaire de code, sans attribution probabiliste.

## TypeScript / React Native

Importer `resolveDeferred` depuis `typescript/index.mjs` et fournir `baseUrl` (origine HTTPS), `appId`, `token`, `platform` et `scheme`. Le runtime doit fournir fetch, URL et AbortSignal.timeout, ou fournir un `fetchImpl` compatible. La résolution envoie un POST JSON sans cookies, refuse les redirections et retourne uniquement une destination du schéma attendu.

Pour BeepClick en production, utiliser `baseUrl: 'https://beepclick.app'`.

Ne pas réessayer automatiquement un token après une erreur de transport : le serveur peut l’avoir consommé avant la perte de réponse. Proposer une navigation d’accueil ou un nouveau parcours d’installation.

## Distribution et vérification

Les sources ne sont pas publiées sur npm, Maven Central, CocoaPods ou un registre Swift. Les tests Node couvrent le protocole du SDK TypeScript. Les sources Kotlin et Swift doivent encore être compilées et testées dans leurs projets mobiles cibles sur appareils réels. Le registre staff permet de référencer une archive réellement produite et son SHA-256, de la déprécier ou de la révoquer ; il n’envoie pas de code aux appareils.
