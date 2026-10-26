import { pathToFileURL } from "node:url";
import { validateServeConfig } from "./prod-check.mjs";

export function validateBackofficeVpn({ origin, status, serve } = {}) {
  const errors = [];
  const domain = status?.Self?.DNSName?.replace(/\.$/, "");
  if (status?.BackendState !== "Running" || !status?.Self?.Online) errors.push("Tailscale : nœud non connecté");
  if (!domain || origin !== `https://${domain}` || !/^beep-admin\.[a-z0-9-]+\.ts\.net$/.test(domain ?? "")) errors.push("BO_PUBLIC_URL : ne correspond pas au nom HTTPS réel du nœud Tailscale");
  if (!serve || !validateServeConfig(serve, domain)) errors.push("Tailscale Serve : configuration HTTPS privée attendue, sans Funnel");
  return errors;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  let input = "";
  for await (const chunk of process.stdin) input += chunk;
  try {
    const errors = validateBackofficeVpn(JSON.parse(input));
    if (errors.length) {
      console.error(errors.join("\n"));
      process.exitCode = 1;
    } else console.log("Backoffice : nœud Tailscale connecté, origine correcte, HTTPS privé sans Funnel.");
  } catch {
    console.error("Impossible de vérifier le statut privé Tailscale.");
    process.exitCode = 1;
  }
}
