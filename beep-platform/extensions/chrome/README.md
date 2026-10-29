# Ajouter à BeepClick — Manifest V3

Dans Chrome, ouvrir `chrome://extensions`, activer le mode développeur, puis **Charger l’extension non empaquetée** et sélectionner ce dossier. Le paquet n’est pas publié sur le Chrome Web Store.

Créer une clé secrète `sk_` avec la portée `write` dans `/app/developer` (Pro ou Elite). L’origine proposée par défaut est `https://beepclick.app`. Dans l’extension, saisir cette clé, puis accorder l’accès à cette origine. Le workspace est celui de la clé. Pour une instance de développement, `http://beep.localhost` est également accepté.

L’extension lit uniquement l’adresse et le titre de l’onglet actif lorsqu’on ouvre son panneau. Aucun script n’est injecté et aucun historique n’est demandé. La destination est envoyée uniquement lors du clic « Ajouter à BeepClick ». La clé est dans `chrome.storage.session`, jamais dans `sync` ni dans le contenu d’une page. Elle disparaît à la fermeture du navigateur. L’origine seule est mémorisée localement. Déconnecter efface la clé ; révoquer la clé dans BeepClick coupe immédiatement son accès.

Les tentatives après un délai réseau réutilisent `Idempotency-Key`. Vérifier dans un navigateur réel l’installation, les permissions optionnelles, la copie et l’ouverture des deeplinks avant publication sur le Store.

Références : [activeTab](https://developer.chrome.com/docs/extensions/develop/concepts/activeTab), [storage.session](https://developer.chrome.com/docs/extensions/reference/api/storage).
