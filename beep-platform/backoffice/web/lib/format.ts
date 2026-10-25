const dateTime = new Intl.DateTimeFormat("fr-FR", { dateStyle: "medium", timeStyle: "short", timeZone: "UTC" });
const day = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", timeZone: "UTC" });
const number = new Intl.NumberFormat("fr-FR");

/** Dates affichées en UTC (même référence que les agrégats et l'audit). */
export const fmtDateTime = (iso: string | null | undefined) => (iso ? `${dateTime.format(new Date(iso))} UTC` : "—");
export const fmtDay = (iso: string) => day.format(new Date(`${iso}T00:00:00Z`));
export const fmtNumber = (n: number) => number.format(n);
export const fmtPercent = (r: number) => `${number.format(Math.round(r * 1000) / 10)} %`;
export const fmtXaf = (n: number) => `${number.format(n)} FCFA`;
export const cycleLabels: Record<string, string> = { monthly: "Mensuel", quarterly: "Trimestriel", annual: "Annuel" };
export const methodLabels: Record<string, string> = { mtn_momo: "MTN MoMo", orange_money: "Orange Money", card: "Carte", manual: "Manuel" };
export const subscriptionLabels: Record<string, string> = { trialing: "Essai", active: "Actif", grace: "Impayé (grâce)", expired: "Expiré" };
export const paymentLabels: Record<string, string> = { pending: "En attente", succeeded: "Payé", failed: "Échoué", refunded: "Remboursé" };
export const fmtLimit = (n: number) => (n === -1 ? "illimité" : number.format(n));

export function fmtBytes(n: number) {
  const units = ["o", "Ko", "Mo", "Go", "To"];
  let v = n;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${number.format(Math.round(v * 10) / 10)} ${units[i]}`;
}

export function fmtDuration(seconds: number | null) {
  if (seconds === null) return "—";
  if (seconds < 120) return `${seconds} s`;
  if (seconds < 7200) return `${Math.round(seconds / 60)} min`;
  return `${Math.round(seconds / 3600)} h`;
}

/** Libellés des clés de droits (specs §6.2). */
export const entitlementLabels: Record<string, string> = {
  "links.stock_max": "Liens actifs (stock)",
  "links.monthly_creations": "Liens créés par mois",
  "qr.stock_max": "QR dynamiques actifs (stock)",
  "qr.monthly_creations": "QR créés par mois",
  "bio.max": "Pages bio",
  "domains.max": "Domaines personnalisés",
  "members.max": "Membres d'équipe",
  "api.rpm": "API (requêtes / minute)",
  "analytics.retention_days": "Rétention des analytics (jours)",
  "features.analytics_advanced": "Analytics avancés",
  "features.bio_badge_removable": "Badge Beep retirable",
  "features.export_csv": "Export CSV",
};

export const resourceLabels: Record<string, string> = { links: "Liens", qr: "QR dynamiques", bio: "Pages bio" };
