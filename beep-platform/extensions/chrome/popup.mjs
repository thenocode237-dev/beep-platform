import { apiOrigin, destination, linkRequest } from "./api.mjs";
const form = document.querySelector("#create"),
  connect = document.querySelector("#connect"),
  status = document.querySelector("#status"),
  settings = document.querySelector("#settings"),
  save = document.querySelector("#save");
let config = await chrome.storage.session.get(["key", "origin"]);
let idempotency = crypto.randomUUID(),
  fingerprint = "",
  short = "";
if (!config.key) settings.open = true;
const local = await chrome.storage.local.get("origin");
connect.elements.origin.value =
  config.origin || local.origin || "https://beep.click";
try {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  form.elements.url.value = destination(tab?.url || "");
  form.elements.title.value = (tab?.title || "").slice(0, 200);
} catch {
  status.textContent = "Ouvrez une page HTTP ou HTTPS pour continuer.";
}
connect.addEventListener("submit", async (e) => {
  e.preventDefault();
  try {
    const origin = apiOrigin(connect.elements.origin.value),
      key = connect.elements.key.value.trim();
    if (!/^sk_[A-Za-z0-9_-]{20,}$/.test(key))
      throw Error("Saisissez une clé sk_ avec la portée write.");
    if (!(await chrome.permissions.request({ origins: [origin + "/*"] })))
      throw Error("L’accès à cette adresse n’a pas été accordé.");
    config = { origin, key };
    await chrome.storage.session.set(config);
    await chrome.storage.local.set({ origin });
    connect.elements.key.value = "";
    settings.open = false;
    status.textContent = "Workspace connecté pour cette session.";
  } catch (e) {
    status.textContent = e.message;
  }
});
document.querySelector("#disconnect").addEventListener("click", async () => {
  await chrome.storage.session.clear();
  config = {};
  settings.open = true;
  document.querySelector("#result").hidden = true;
  status.textContent = "Clé effacée de cette session.";
});
form.addEventListener("submit", async (e) => {
  e.preventDefault();
  save.disabled = true;
  status.textContent = "Création du lien…";
  try {
    const data = {
      ...config,
      url: form.elements.url.value,
      title: form.elements.title.value,
      deeplink: form.elements.deeplink.checked,
    };
    const fresh = JSON.stringify([
      data.origin,
      data.url,
      data.title,
      data.deeplink,
    ]);
    if (fingerprint !== fresh) {
      idempotency = crypto.randomUUID();
      fingerprint = fresh;
    }
    const request = linkRequest({ ...data, idempotency });
    const r = await fetch(request.url, {
      ...request.options,
      signal: AbortSignal.timeout(15000),
    });
    if (!r.ok)
      throw Error(
        r.status === 429
          ? "Limite atteinte. Patientez une minute."
          : r.status === 401
            ? "Clé expirée ou révoquée. Reconnectez ce workspace."
            : r.status === 409
              ? "Quota atteint ou slug indisponible. Consultez votre workspace."
              : "Création refusée. Vérifiez la portée write et votre offre.",
      );
    const result = await r.json();
    short = result.shortUrl;
    const a = document.querySelector("#short");
    a.textContent = short;
    a.href = short;
    document.querySelector("#result").hidden = false;
    status.textContent = "Lien créé.";
  } catch (e) {
    status.textContent =
      e.name === "TimeoutError"
        ? "Délai dépassé. Réessayez : la même requête reste dédupliquée."
        : e.message;
  } finally {
    save.disabled = false;
  }
});
document.querySelector("#copy").addEventListener("click", () =>
  navigator.clipboard
    .writeText(short)
    .then(() => {
      status.textContent = "Lien copié.";
    })
    .catch(() => {
      status.textContent = "Sélectionnez le lien pour le copier.";
    }),
);
