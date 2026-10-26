import { publicMetadata } from "@/lib/seo";
export const generateMetadata = publicMetadata("/docs");
import { notFound } from "next/navigation";
import { PublicPage } from "@/components/PublicPage";
import { isLocale, href } from "@/lib/i18n";
export default async function Page({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  if (!isLocale(locale)) notFound();
  const en = locale === "en";
  const examples = {
    JavaScript: `const response = await fetch('https://YOUR-BEEP-HOST/v1/links', {\n  method: 'POST',\n  headers: {Authorization: 'Bearer ' + process.env.BEEP_API_KEY,\n    'Content-Type': 'application/json', 'Idempotency-Key': 'order-123-link'},\n  body: JSON.stringify({destination: 'https://example.com/offer'})\n});\nconsole.log(await response.json());`,
    Python: `import os, requests\nresponse = requests.post('https://YOUR-BEEP-HOST/v1/links',\n  headers={'Authorization': 'Bearer ' + os.environ['BEEP_API_KEY'],\n           'Idempotency-Key': 'order-123-link'},\n  json={'destination': 'https://example.com/offer'})\nresponse.raise_for_status()\nprint(response.json())`,
    PHP: `<?php\n$ch = curl_init('https://YOUR-BEEP-HOST/v1/links');\ncurl_setopt_array($ch, [CURLOPT_POST => true, CURLOPT_RETURNTRANSFER => true,\n  CURLOPT_HTTPHEADER => ['Authorization: Bearer '.getenv('BEEP_API_KEY'),\n    'Content-Type: application/json', 'Idempotency-Key: order-123-link'],\n  CURLOPT_POSTFIELDS => json_encode(['destination' => 'https://example.com/offer'])]);\necho curl_exec($ch);\ncurl_close($ch);`,
  };
  return (
    <PublicPage
      locale={locale}
      path="/docs"
      title={en ? "Build with Beep." : "Développez avec Beep."}
      intro={
        en
          ? "Links, conversion events and signed webhooks."
          : "Liens, événements de conversion et webhooks signés."
      }
    >
      <section className="bp-card bp-stack">
        <h2>{en ? "Getting started" : "Premiers pas"}</h2>
        <p>
          {en
            ? "Create a scoped key in your workspace. Keep sk_ keys on your server. Pro: 60 requests/minute. Elite: 600. read allows GET, write allows link mutations, events allows server-side event ingestion."
            : "Créez une clé dans votre espace. Conservez les clés sk_ sur votre serveur. Pro : 60 requêtes/minute. Elite : 600. read autorise les lectures, write les modifications de liens, events l’ingestion serveur."}
        </p>
        <a href={href(locale, "/app/developer")}>
          {en ? "Manage keys" : "Gérer les clés"}
        </a>
        <a href="/v1/openapi.json">OpenAPI 3.1 (JSON)</a>
        <p>
          {en
            ? "Supported endpoints: GET/POST /v1/links, GET/PATCH/DELETE /v1/links/{id}, GET /v1/links/{id}/stats, POST /v1/events, GET /v1/conversions/analytics. Other workspace routes require a session."
            : "Routes disponibles : GET/POST /v1/links, GET/PATCH/DELETE /v1/links/{id}, GET /v1/links/{id}/stats, POST /v1/events, GET /v1/conversions/analytics. Les autres routes de l’espace exigent une session."}
        </p>
        <p>
          {en
            ? "Pagination uses limit and nextCursor. Mutations accept Idempotency-Key for 24 hours. Reusing it with a different request returns 409. A 429 response includes Retry-After. Errors use application/problem+json and a stable code."
            : "La pagination utilise limit et nextCursor. Les mutations acceptent Idempotency-Key pendant 24 heures. Une réutilisation pour une requête différente renvoie 409. Les réponses 429 incluent Retry-After. Les erreurs utilisent application/problem+json avec un code stable."}
        </p>
      </section>
      {Object.entries(examples).map(([name, code]) => (
        <section className="bp-card bp-stack" key={name}>
          <h2>{name}</h2>
          <pre className="developer-code">{code}</pre>
        </section>
      ))}
      <section className="bp-card bp-stack">
        <h2>beep.js & {en ? "consent" : "consentement"}</h2>
        <pre className="developer-code">{`<script>window.beep=window.beep||function(){(window.beep.q=window.beep.q||[]).push(arguments)};</script>\n<script defer src="https://YOUR-BEEP-HOST/beep.js" data-key="pk_YOUR_PUBLIC_KEY"></script>\n// Only after explicit consent from your CMP:\nbeep('consent', true);\nbeep('track', 'sale', {value: 49, currency: 'EUR'});\n// Withdrawal:\nbeep('consent', false);`}</pre>
        <p>
          {en
            ? 'Public keys only collect events from exact allowed origins. The default mode records anonymous page views without cookies. Add data-cookieless="true" to force that mode. Consent is never inferred. lead, sale, signup, subscription and custom:name require consent. Enable tracking separately for each link.'
            : 'Les clés publiques collectent uniquement depuis les origines exactes autorisées. Par défaut, les pages vues sont anonymes et sans cookie. Ajoutez data-cookieless="true" pour forcer ce mode. Le consentement n’est jamais implicite. lead, sale, signup, subscription et custom:nom exigent un consentement. Activez le suivi séparément pour chaque lien.'}
        </p>
        <pre className="developer-code">
          {JSON.stringify(
            {
              eventId: "a1b2c3d4-0000-4000-8000-000000000001",
              name: "sale",
              cid: "SIGNED_BEEP_CID",
              visitor: "your-consented-visitor-id",
              value: 49,
              currency: "EUR",
              consent: true,
            },
            null,
            2,
          )}
        </pre>
        <p>
          {en
            ? "Send this JSON to POST /v1/events with an events-scoped server key. Reuse eventId when retrying. Amounts are grouped by currency and never summed across currencies."
            : "Envoyez ce JSON à POST /v1/events avec une clé serveur de portée events. Réutilisez eventId lors des reprises. Les montants sont regroupés par devise et ne sont jamais additionnés entre devises."}
        </p>
      </section>
      <section className="bp-card bp-stack">
        <h2>
          {en
            ? "Verify webhook signatures"
            : "Vérifier les signatures de webhooks"}
        </h2>
        <p>
          {en
            ? "Beep-Signature contains t=UNIX_SECONDS,v1=HEX_HMAC. Compute HMAC-SHA256 with your whsec_ secret over timestamp + '.' + the exact request body. Compare in constant time, reject timestamps older than 5 minutes, and deduplicate the event id. Delivery is at least once, with eight attempts and exponential backoff. 3xx responses are not followed."
            : "Beep-Signature contient t=UNIX_SECONDS,v1=HMAC_HEX. Calculez HMAC-SHA256 avec le secret whsec_ sur timestamp + '.' + le corps exact. Comparez en temps constant, refusez les horodatages de plus de 5 minutes et dédupliquez l’identifiant d’événement. La livraison peut être répétée : huit tentatives avec attente exponentielle. Les redirections 3xx ne sont pas suivies."}
        </p>
        <pre className="developer-code">{`import {createHmac, timingSafeEqual} from 'node:crypto';\nconst expected = createHmac('sha256', process.env.BEEP_WEBHOOK_SECRET)\n  .update(timestamp + '.' + rawBody).digest();\nconst received = Buffer.from(signatureHex, 'hex');\nconst valid = received.length === expected.length &&\n  timingSafeEqual(received, expected) &&\n  Math.abs(Date.now()/1000 - Number(timestamp)) < 300;`}</pre>
      </section>
    </PublicPage>
  );
}
