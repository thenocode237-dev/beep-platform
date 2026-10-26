export function apiOrigin(value) {
  const u = new URL(value);
  if (
    u.username ||
    u.password ||
    u.search ||
    u.hash ||
    u.pathname !== "/" ||
    u.port ||
    (u.protocol !== "https:" &&
      !(u.protocol === "http:" && u.hostname === "beep.localhost"))
  )
    throw new Error(
      "Utilisez une origine HTTPS, ou http://beep.localhost en local.",
    );
  return u.origin;
}
export function destination(value) {
  const u = new URL(value);
  if (
    !["http:", "https:"].includes(u.protocol) ||
    u.username ||
    u.password ||
    value.length > 2048
  )
    throw new Error("Ouvrez une page HTTP ou HTTPS.");
  return u.href;
}
export function linkRequest({
  origin,
  key,
  url,
  title,
  deeplink,
  idempotency,
}) {
  if (!/^sk_[A-Za-z0-9_-]{20,}$/.test(key))
    throw new Error(
      "Une clé API secrète sk_ avec la portée write est nécessaire.",
    );
  return {
    url: apiOrigin(origin) + "/v1/links",
    options: {
      method: "POST",
      credentials: "omit",
      redirect: "error",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + key,
        "Idempotency-Key": idempotency,
      },
      body: JSON.stringify({
        destination: destination(url),
        title: title.slice(0, 200),
        ...(deeplink ? { rules: { deeplink: true } } : {}),
      }),
    },
  };
}
