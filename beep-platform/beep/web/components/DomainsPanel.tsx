"use client";

import { useState, type FormEvent } from "react";
import { Alert, Badge, Button, TextField } from "@beep/ui";
import { send, type DomainRow, type DomainsView } from "@/lib/api";
import type { DomainsDict } from "@/lib/dictionaries/domains";
import type { ConsoleDict } from "@/lib/dictionaries/console-fr";

function useRun(t: ConsoleDict, d: DomainsDict) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [done, setDone] = useState(false);
  async function run(fn: () => ReturnType<typeof send>) {
    setBusy(true);
    setError(undefined);
    setDone(false);
    const r = await fn();
    setBusy(false);
    if (!r.ok) return setError(d.errors[r.code ?? ""] ?? t.errors[r.code ?? ""] ?? t.errors.generic);
    setDone(true);
    window.setTimeout(() => window.location.reload(), 500);
  }
  return { busy, error, done, run };
}

const val = (e: FormEvent<HTMLFormElement>, name: string) => String(new FormData(e.currentTarget).get(name) ?? "");

function AddDomain({ t, d }: { t: ConsoleDict; d: DomainsDict }) {
  const a = useRun(t, d);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        a.run(() => send("POST", "/domains", { hostname: val(e, "hostname") }));
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      <TextField name="hostname" label={d.hostname} hint={d.hostnameHint} inputMode="url" autoCapitalize="none" autoCorrect="off" placeholder="go.votre-marque.com" required />
      <Button type="submit" loading={a.busy}>
        {d.submit}
      </Button>
    </form>
  );
}

function Mono({ children }: { children: string }) {
  return <code className="break bp-tabular">{children}</code>;
}

function DomainCard({ dom, t, d }: { dom: DomainRow; t: ConsoleDict; d: DomainsDict }) {
  const verify = useRun(t, d);
  const settings = useRun(t, d);
  const remove = useRun(t, d);
  return (
    <section className="bp-card bp-card--lg bp-stack" aria-label={dom.hostname}>
      <div className="link-row">
        <h2 className="break">{dom.hostname}</h2>
        <span className="bp-row">
          <Badge soon={dom.dnsStatus !== "verified"}>{d.dns[dom.dnsStatus]}</Badge>
          {dom.dnsStatus === "verified" && <Badge soon={dom.tlsStatus !== "active"}>{d.tls[dom.tlsStatus]}</Badge>}
          {dom.isDefault && <Badge>{d.isDefault}</Badge>}
        </span>
      </div>
      {dom.blocked && <Alert tone="danger">{d.blocked}</Alert>}
      {dom.lastError && <Alert tone="info">{d.lastError[dom.lastError] ?? dom.lastError}</Alert>}

      {dom.dnsStatus !== "verified" && (
        <div className="bp-stack">
          <h3>{d.steps}</h3>
          <p className="muted">{d.step1}</p>
          <dl className="kv">
            <dt>{d.name}</dt>
            <dd><Mono>{dom.dns.txtName ?? ""}</Mono></dd>
            <dt>{d.value}</dt>
            <dd><Mono>{dom.dns.txtValue ?? ""}</Mono></dd>
          </dl>
          <p className="muted">{d.step2}</p>
          <dl className="kv">
            <dt>{d.name}</dt>
            <dd><Mono>{dom.hostname}</Mono></dd>
            <dt>{d.value}</dt>
            <dd><Mono>{dom.dns.cnameTarget}</Mono></dd>
          </dl>
          {verify.error && <Alert tone="danger">{verify.error}</Alert>}
          <div className="bp-row">
            <Button variant="secondary" loading={verify.busy} onClick={() => verify.run(() => send("POST", `/domains/${dom.id}/verify`))}>
              {d.verify}
            </Button>
            <span className="muted small">{d.verifyNote}</span>
          </div>
        </div>
      )}

      {dom.dnsStatus === "verified" && !dom.blocked && (
        <form
          className="bp-stack"
          onSubmit={(e) => {
            e.preventDefault();
            settings.run(() => send("PATCH", `/domains/${dom.id}`, { rootRedirectUrl: val(e, "root"), notFoundUrl: val(e, "notfound") }));
          }}
        >
          {settings.error && <Alert tone="danger">{settings.error}</Alert>}
          <TextField name="root" label={d.root} hint={d.redirectHint} defaultValue={dom.rootRedirectUrl ?? ""} inputMode="url" />
          <TextField name="notfound" label={d.notFound} hint={d.redirectHint} defaultValue={dom.notFoundUrl ?? ""} inputMode="url" />
          <div className="bp-row">
            <Button type="submit" loading={settings.busy}>
              {d.save}
            </Button>
            <Button type="button" variant="secondary" loading={settings.busy} onClick={() => settings.run(() => send("PATCH", `/domains/${dom.id}`, { makeDefault: !dom.isDefault }))}>
              {dom.isDefault ? d.unsetDefault : d.makeDefault}
            </Button>
          </div>
        </form>
      )}

      <div className="bp-row">
        <span className="muted small bp-tabular">
          {dom.links} {d.links}
        </span>
        {remove.error && <Alert tone="danger">{remove.error}</Alert>}
        <Button variant="secondary" size="sm" disabled={dom.links > 0} loading={remove.busy} onClick={() => remove.run(() => send("DELETE", `/domains/${dom.id}`))}>
          {d.remove}
        </Button>
        {dom.links > 0 && <span className="muted small">{d.removeBlocked}</span>}
      </div>
    </section>
  );
}

export function DomainsPanel({ view, t, d, canAdd }: { view: DomainsView; t: ConsoleDict; d: DomainsDict; canAdd: boolean }) {
  return (
    <div className="app-page bp-stack bp-stack--lg">
      <div className="app-heading">
        <h1>{d.title}</h1>
        <p className="muted">{d.sub}</p>
      </div>
      <div className="app-grid app-grid--aside">
        <div className="bp-stack bp-stack--lg">
          {view.items.length === 0 ? <p className="bp-card muted">{d.empty}</p> : view.items.map((dom) => <DomainCard key={dom.id} dom={dom} t={t} d={d} />)}
        </div>
        {canAdd && (
          <section className="bp-card bp-card--lg bp-stack" aria-labelledby="add-domain">
            <h2 id="add-domain">{d.add}</h2>
            <AddDomain t={t} d={d} />
          </section>
        )}
      </div>
    </div>
  );
}
