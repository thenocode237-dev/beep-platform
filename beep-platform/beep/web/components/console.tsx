"use client";
import { QrDecorationFields } from "@/components/QrDecorationFields";

import { useState, type FormEvent } from "react";
import { Alert, Button, Icon, TextField } from "@beep/ui";
import { send, type BioView, type BlockView, type LinkView, type QrView } from "@/lib/api";
import type { ConsoleDict } from "@/lib/dictionaries/console-fr";
import { href, type Locale } from "@/lib/i18n";

type T = { t: ConsoleDict };
const err = (t: ConsoleDict, code?: string) => t.errors[code ?? ""] ?? t.errors.generic;
const val = (e: FormEvent<HTMLFormElement>, name: string) => {
  const v = new FormData(e.currentTarget).get(name);
  return v === null ? undefined : String(v);
};

function useAction(t: ConsoleDict) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  async function run<R>(fn: () => Promise<{ ok: boolean; code?: string; data?: R }>, onOk?: (data?: R) => void) {
    setBusy(true);
    setError(undefined);
    const r = await fn();
    setBusy(false);
    if (!r.ok) {
      setError(err(t, r.code));
      return false;
    }
    onOk?.(r.data);
    return true;
  }
  return { busy, error, setError, run };
}

export function CopyButton({ text, t, compact }: T & { text: string; compact?: boolean }) {
  const [done, setDone] = useState(false);
  return (
    <button
      type="button"
      className={compact ? "icon-btn" : "bp-btn bp-btn--secondary bp-btn--sm"}
      aria-label={compact ? `${t.common.copy} ${text}` : undefined}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          setDone(true);
          window.setTimeout(() => setDone(false), 1500);
        } catch {
          /* presse-papiers indisponible : le texte reste sélectionnable */
        }
      }}
    >
      <Icon name={done ? "check" : "copy"} size={18} />
      {!compact && <span aria-live="polite">{done ? t.common.copied : t.common.copy}</span>}
    </button>
  );
}

/** Création « en un champ » sur l'accueil. */
export function QuickLinkForm({ t }: T) {
  const a = useAction(t);
  const [created, setCreated] = useState<LinkView>();
  return (
    <form
      className="quick-link"
      onSubmit={(e) => {
        e.preventDefault();
        const form = e.currentTarget;
        a.run<LinkView>(() => send<LinkView>("POST", "/links", { destination: val(e, "destination") }), (link) => {
          setCreated(link);
          form.reset();
        });
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      <div className="quick-link__row">
        <TextField name="destination" label={t.overview.quickLabel} placeholder={t.overview.quickPlaceholder} inputMode="url" autoComplete="url" required />
        <Button type="submit" loading={a.busy}>
          {t.overview.quickSubmit}
        </Button>
      </div>
      {created && (
        <div className="created" role="status">
          <span>{t.overview.created}</span>
          <a href={created.shortUrl} target="_blank" rel="noopener noreferrer">
            {created.shortUrl.replace(/^https?:\/\//, "")}
          </a>
          <CopyButton text={created.shortUrl} t={t} />
        </div>
      )}
    </form>
  );
}

export function CreateLinkForm({ t, locale, domains = [], labels }: T & { locale: Locale; domains?: { id: string; hostname: string }[]; labels?: { title: string; byDefault: string; main: string } }) {
  const a = useAction(t);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const body = { destination: val(e, "destination"), slug: val(e, "slug") || undefined, title: val(e, "title") || undefined, domain: val(e, "domain") || undefined };
        a.run<LinkView>(() => send<LinkView>("POST", "/links", body), (link) => window.location.assign(href(locale, `/app/links/${link!.id}`)));
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      <TextField name="destination" label={t.links.destination} hint={t.links.destinationHint} inputMode="url" required />
      <TextField name="slug" label={`${t.links.slug} (${t.common.optional})`} hint={t.links.slugHint} autoCapitalize="none" autoCorrect="off" maxLength={64} />
      <TextField name="title" label={`${t.links.title_} (${t.common.optional})`} hint={t.links.titleHint} maxLength={200} />
      {domains.length > 0 && labels && (
        <div className="bp-field">
          <label className="bp-field__label" htmlFor="link-domain">
            {labels.title}
          </label>
          <select id="link-domain" name="domain" className="bp-input" defaultValue="">
            <option value="">{labels.byDefault}</option>
            <option value="main">{labels.main}</option>
            {domains.map((d) => (
              <option key={d.id} value={d.id}>
                {d.hostname}
              </option>
            ))}
          </select>
        </div>
      )}
      <Button type="submit" loading={a.busy}>
        {t.links.submit}
      </Button>
    </form>
  );
}

export function EditLinkForm({ t, link }: T & { link: LinkView }) {
  const a = useAction(t);
  const [saved, setSaved] = useState(false);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        setSaved(false);
        const body = { destination: val(e, "destination"), slug: val(e, "slug"), title: val(e, "title") ?? "" };
        a.run(() => send("PATCH", `/links/${link.id}`, body), () => {
          setSaved(true);
          window.setTimeout(() => window.location.reload(), 700);
        });
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      {saved && <Alert tone="success">{t.common.saved}</Alert>}
      <TextField name="destination" label={t.links.destination} defaultValue={link.destination} inputMode="url" required />
      <TextField name="slug" label={t.links.slug} hint={t.links.slugHint} defaultValue={link.slug} autoCapitalize="none" autoCorrect="off" required />
      <TextField name="title" label={`${t.links.title_} (${t.common.optional})`} defaultValue={link.title ?? ""} maxLength={200} />
      <Button type="submit" loading={a.busy}>
        {t.common.save}
      </Button>
    </form>
  );
}

/** Suppression en deux temps (pas de boîte de dialogue bloquante). */
export function DeleteButton({ t, path, redirectTo, text }: T & { path: string; redirectTo: string; text: string }) {
  const a = useAction(t);
  const [armed, setArmed] = useState(false);
  return (
    <div className="bp-stack">
      <p className="muted">{text}</p>
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      {armed ? (
        <div className="bp-row">
          <Button variant="primary" loading={a.busy} onClick={() => a.run(() => send("DELETE", path), () => window.location.assign(redirectTo))}>
            <Icon name="trash" size={18} /> {t.common.confirmDelete}
          </Button>
          <Button variant="ghost" onClick={() => setArmed(false)}>
            {t.common.cancel}
          </Button>
        </div>
      ) : (
        <Button variant="secondary" onClick={() => setArmed(true)}>
          <Icon name="trash" size={18} /> {t.common.delete}
        </Button>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------------------- QR

const STATIC_FIELDS: Record<string, string[]> = {
  url: ["url"],
  text: ["text"],
  wifi: ["ssid", "security", "password"],
  email: ["to", "subject", "body"],
  sms: ["phone", "message"],
  phone: ["phone"],
  vcard: ["name", "phone", "email", "org", "url"],
};
const REQUIRED = new Set(["url", "text", "ssid", "to", "phone", "name"]);

export function QrCreateForm({ t, locale, links, presetLink, advancedAllowed=false }: T & { locale: Locale; links: LinkView[]; presetLink?: string; advancedAllowed?: boolean }) {
  const a = useAction(t);
  const [kind, setKind] = useState<"dynamic" | "static">(links.length ? "dynamic" : "static");
  const [type, setType] = useState("url");
  const [security, setSecurity] = useState("WPA");
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const fields: Record<string, string> = {};
        if (kind === "static") for (const f of STATIC_FIELDS[type]) {
          const v = val(e, f);
          if (v) fields[f] = v;
        }
        const body = {
          kind,
          linkId: kind === "dynamic" ? val(e, "linkId") : undefined,
          contentType: kind === "static" ? type : undefined,
          fields: kind === "static" ? fields : undefined,
          foreground: val(e, "foreground"),
          background: val(e, "background"),
          errorCorrection: val(e, "ecc"),
          advanced: val(e,"advancedEnabled") ? {shape:val(e,"shape"),gradient:val(e,"gradientEnabled")?val(e,"gradient"):null,logo:val(e,"logo")||null,frame:val(e,"frame")} : undefined,
          title: val(e, "title") || undefined,
        };
        a.run(() => send("POST", "/qr-codes", body), () => window.location.assign(href(locale, "/app/qr")));
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      <fieldset className="choice">
        <legend>{t.qr.kind}</legend>
        <label className="choice__item">
          <input type="radio" name="kind" checked={kind === "dynamic"} onChange={() => setKind("dynamic")} disabled={!links.length} /> {t.qr.dynamic}
        </label>
        <label className="choice__item">
          <input type="radio" name="kind" checked={kind === "static"} onChange={() => setKind("static")} /> {t.qr.static}
        </label>
      </fieldset>
      {kind === "dynamic" ? (
        links.length ? (
          <div className="bp-field">
            <label className="bp-field__label" htmlFor="qr-link">
              {t.qr.link}
            </label>
            <select id="qr-link" name="linkId" className="bp-input" defaultValue={presetLink ?? links[0].id}>
              {links.map((l) => (
                <option key={l.id} value={l.id}>
                  /{l.slug} → {l.destination.replace(/^https?:\/\//, "").slice(0, 60)}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <Alert tone="info">{t.qr.noLinks}</Alert>
        )
      ) : (
        <>
          <div className="bp-field">
            <label className="bp-field__label" htmlFor="qr-type">
              {t.qr.contentType}
            </label>
            <select id="qr-type" className="bp-input" value={type} onChange={(e) => setType(e.target.value)}>
              {Object.entries(t.qr.types).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </div>
          {STATIC_FIELDS[type].map((f) =>
            f === "security" ? (
              <div className="bp-field" key={f}>
                <label className="bp-field__label" htmlFor="qr-security">
                  {t.qr.fields.security}
                </label>
                <select id="qr-security" name="security" className="bp-input" value={security} onChange={(e) => setSecurity(e.target.value)}>
                  <option value="WPA">WPA / WPA2</option>
                  <option value="WEP">WEP</option>
                  <option value="NOPASS">{t.qr.noPassword}</option>
                </select>
              </div>
            ) : f === "password" && security === "NOPASS" ? null : (
              <TextField key={`${type}-${f}`} name={f} label={t.qr.fields[f] ?? f} required={REQUIRED.has(f) || (f === "password" && security !== "NOPASS")} />
            ),
          )}
        </>
      )}
      <div className="color-row">
        <label className="color-field">
          <span>{t.qr.foreground}</span>
          <input type="color" name="foreground" defaultValue="#222B25" />
        </label>
        <label className="color-field">
          <span>{t.qr.background}</span>
          <input type="color" name="background" defaultValue="#FFFFFF" />
        </label>
      </div>
      <p className="muted small">{t.qr.contrastHint}</p>
      <div className="bp-field">
        <label className="bp-field__label" htmlFor="qr-ecc">
          {t.qr.ecc}
        </label>
        <select id="qr-ecc" name="ecc" className="bp-input" defaultValue="M" aria-describedby="qr-ecc-hint">
          {Object.entries(t.qr.eccLevels).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <p id="qr-ecc-hint" className="bp-field__hint">
          {t.qr.eccHint}
        </p>
      </div>
      <QrDecorationFields en={locale==="en"} allowed={advancedAllowed}/>
      <TextField name="title" label={`${t.qr.title_} (${t.common.optional})`} maxLength={200} />
      <Button type="submit" loading={a.busy} disabled={kind === "dynamic" && !links.length}>
        {t.qr.submit}
      </Button>
    </form>
  );
}

export function QrDeleteButton({ t, qr }: T & { qr: QrView }) {
  const a = useAction(t);
  const [armed, setArmed] = useState(false);
  return (
    <>
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      {armed ? (
        <Button size="sm" loading={a.busy} onClick={() => a.run(() => send("DELETE", `/qr-codes/${qr.id}`), () => window.location.reload())}>
          {t.common.confirmDelete}
        </Button>
      ) : (
        <Button size="sm" variant="ghost" onClick={() => setArmed(true)}>
          <Icon name="trash" size={16} /> {t.common.delete}
        </Button>
      )}
    </>
  );
}

// --------------------------------------------------------------------------------------------------------------- Bio

export function BioCreateForm({ t, locale, site }: T & { locale: Locale; site: string }) {
  const a = useAction(t);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        a.run<BioView>(() => send<BioView>("POST", "/bio-pages", { slug: val(e, "slug"), title: val(e, "title") }), (p) =>
          window.location.assign(href(locale, `/app/bio/${p!.id}`)),
        );
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      <TextField name="title" label={t.bio.pageTitle} maxLength={80} required />
      <TextField name="slug" label={t.bio.slug} hint={t.bio.slugHint.replace("{url}", site.replace(/^https?:\/\//, ""))} autoCapitalize="none" autoCorrect="off" required />
      <Button type="submit" loading={a.busy}>
        {t.bio.submit}
      </Button>
    </form>
  );
}

export function BioSettingsForm({ t, page }: T & { page: BioView }) {
  const a = useAction(t);
  const [saved, setSaved] = useState(false);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        setSaved(false);
        const body = {
          slug: val(e, "slug"),
          title: val(e, "title"),
          description: val(e, "description"),
          avatarUrl: val(e, "avatarUrl"),
          theme: val(e, "theme"),
          seoTitle: val(e, "seoTitle"),
          seoDescription: val(e, "seoDescription"),
          seoImageUrl: val(e, "seoImageUrl"),
          noindex: new FormData(e.currentTarget).get("noindex") === "on",
        };
        a.run(() => send("PUT", `/bio-pages/${page.id}`, body), () => {
          setSaved(true);
          window.setTimeout(() => window.location.reload(), 700);
        });
      }}
    >
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      {saved && <Alert tone="success">{t.common.saved}</Alert>}
      <TextField name="title" label={t.bio.pageTitle} defaultValue={page.title} maxLength={80} required />
      <TextField name="slug" label={t.bio.slug} defaultValue={page.slug} autoCapitalize="none" required />
      <div className="bp-field">
        <label className="bp-field__label" htmlFor="bio-description">
          {t.bio.description}
        </label>
        <textarea id="bio-description" name="description" className="bp-input bp-textarea" maxLength={300} defaultValue={page.description ?? ""} rows={3} />
      </div>
      <TextField name="avatarUrl" label={`${t.bio.avatar} (${t.common.optional})`} defaultValue={page.avatarUrl ?? ""} inputMode="url" />
      <fieldset className="choice choice--grid">
        <legend>{t.bio.theme}</legend>
        {Object.entries(t.bio.themes).map(([k, v]) => (
          <label key={k} className={`theme-swatch theme-swatch--${k}`}>
            <input type="radio" name="theme" value={k} defaultChecked={page.theme === k} /> <span>{v}</span>
          </label>
        ))}
      </fieldset>
      <details className="seo">
        <summary>{t.bio.seo}</summary>
        <div className="bp-stack">
          <TextField name="seoTitle" label={`${t.bio.seoTitle} (${t.common.optional})`} defaultValue={page.seoTitle ?? ""} maxLength={70} />
          <TextField name="seoDescription" label={`${t.bio.seoDescription} (${t.common.optional})`} defaultValue={page.seoDescription ?? ""} maxLength={200} />
          <TextField name="seoImageUrl" label={`${t.bio.seoImage} (${t.common.optional})`} defaultValue={page.seoImageUrl ?? ""} inputMode="url" />
          <label className="check">
            <input type="checkbox" name="noindex" defaultChecked={page.noindex} /> {t.bio.noindex}
          </label>
        </div>
      </details>
      <p className="muted small">{t.bio.imageNote}</p>
      <Button type="submit" loading={a.busy}>
        {t.common.save}
      </Button>
    </form>
  );
}

const BLOCK_FIELDS: Record<string, string[]> = {
  link: ["label", "url"],
  button: ["label", "url"],
  heading: ["text"],
  text: ["text"],
  image: ["url", "alt", "href"],
  video: ["url", "label"], audio: ["url", "label"], email: ["label", "description", "privacyUrl", "consentText"], form: ["label", "description", "privacyUrl", "consentText"],
};
const NETWORKS = ["instagram", "tiktok", "facebook", "x", "youtube", "linkedin", "whatsapp", "telegram", "github", "website", "email"];
const NETWORK_LABELS: Record<string, string> = {
  instagram: "Instagram", tiktok: "TikTok", facebook: "Facebook", x: "X", youtube: "YouTube", linkedin: "LinkedIn", whatsapp: "WhatsApp",
  telegram: "Telegram", github: "GitHub", website: "Web", email: "E-mail",
};

type DraftBlock = { key: string; id?: string; type: string; config: Record<string, string> };

/** Éditeur de blocs : ajout, édition, ordre (flèches, accessible au clavier), suppression ; enregistrement en une fois. */
export function BlocksEditor({ t, page }: T & { page: BioView }) {
  const a = useAction(t);
  const [saved, setSaved] = useState(false);
  const [blocks, setBlocks] = useState<DraftBlock[]>(page.blocks.map((b: BlockView) => ({ key: b.id, id: b.id, type: b.type, config: { ...b.config } })));
  const [newType, setNewType] = useState("link");
  const update = (i: number, field: string, value: string) =>
    setBlocks((bs) => bs.map((b, j) => (j === i ? { ...b, config: { ...b.config, [field]: value } } : b)));
  const move = (i: number, d: -1 | 1) =>
    setBlocks((bs) => {
      const next = [...bs];
      const [x] = next.splice(i, 1);
      next.splice(i + d, 0, x);
      return next;
    });
  return (
    <div className="bp-stack">
      {a.error && <Alert tone="danger">{a.error}</Alert>}
      {saved && <Alert tone="success">{t.common.saved}</Alert>}
      {blocks.length === 0 && <p className="muted">{t.bio.noBlocks}</p>}
      <ol className="blocks">
        {blocks.map((b, i) => (
          <li key={b.key} className="block">
            <div className="block__head">
              <strong>{t.bio.blockTypes[b.type as keyof ConsoleDict["bio"]["blockTypes"]] ?? b.type}</strong>
              <div className="block__tools">
                <button type="button" className="icon-btn" aria-label={t.bio.moveUp} disabled={i === 0} onClick={() => move(i, -1)}>
                  ↑
                </button>
                <button type="button" className="icon-btn" aria-label={t.bio.moveDown} disabled={i === blocks.length - 1} onClick={() => move(i, 1)}>
                  ↓
                </button>
                <button type="button" className="icon-btn" aria-label={t.bio.remove} onClick={() => setBlocks((bs) => bs.filter((_, j) => j !== i))}>
                  <Icon name="trash" size={16} />
                </button>
              </div>
            </div>
            {b.type === "social" ? (
              <div className="block__fields block__fields--grid">
                {NETWORKS.map((n) => (
                  <TextField
                    key={n}
                    label={NETWORK_LABELS[n]}
                    value={b.config[n] ?? ""}
                    onChange={(e) => update(i, n, e.target.value)}
                    inputMode={n === "email" ? "email" : "url"}
                    placeholder={n === "email" ? "hello@…" : "https://…"}
                  />
                ))}
              </div>
            ) : (
              <div className="block__fields">
                {BLOCK_FIELDS[b.type].map((f) =>
                  b.type === "text" ? (
                    <div className="bp-field" key={f}>
                      <label className="bp-field__label" htmlFor={`${b.key}-${f}`}>
                        {t.bio.blockFields[f]}
                      </label>
                      <textarea id={`${b.key}-${f}`} className="bp-input bp-textarea" rows={3} maxLength={1000} value={b.config[f] ?? ""} onChange={(e) => update(i, f, e.target.value)} />
                    </div>
                  ) : (
                    <TextField key={f} label={t.bio.blockFields[f] ?? f} value={b.config[f] ?? ""} onChange={(e) => update(i, f, e.target.value)} />
                  ),
                )}
              </div>
            )}
          </li>
        ))}
      </ol>
      <div className="add-block">
        <div className="bp-field">
          <label className="bp-field__label" htmlFor="new-block">
            {t.bio.addBlock}
          </label>
          <select id="new-block" className="bp-input" value={newType} onChange={(e) => setNewType(e.target.value)}>
            {Object.entries(t.bio.blockTypes).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </div>
        <Button type="button" variant="secondary" onClick={() => setBlocks((bs) => [...bs, { key: `new-${Date.now()}`, type: newType, config: {} }])}>
          <Icon name="plus" size={18} /> {t.bio.addBlock}
        </Button>
      </div>
      <Button
        loading={a.busy}
        onClick={() => {
          setSaved(false);
          const payload = blocks.map((b) => ({ id: b.id, type: b.type, config: Object.fromEntries(Object.entries(b.config).filter(([, v]) => v.trim() !== "")) }));
          a.run<BioView>(() => send<BioView>("PUT", `/bio-pages/${page.id}/blocks`, { blocks: payload }), (p) => {
            setSaved(true);
            if (p) setBlocks(p.blocks.map((x) => ({ key: x.id, id: x.id, type: x.type, config: { ...x.config } })));
          });
        }}
      >
        {t.bio.saveBlocks}
      </Button>
    </div>
  );
}
