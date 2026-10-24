/** Message commun quand une donnée du core ne peut pas être lue (core injoignable, droits, ressource inconnue). */
export function Unavailable({ status, what }: { status: number; what: string }) {
  const text =
    status === 0 || status >= 500
      ? `Impossible d'afficher ${what} : le service principal est injoignable. Réessayez dans un instant.`
      : status === 403
        ? `Votre rôle ne donne pas accès à ${what}.`
        : status === 404
          ? `Élément introuvable : il a peut-être été supprimé.`
          : `Impossible de charger ${what}.`;
  return (
    <div className="bp-card" role="alert">
      {text}
    </div>
  );
}
