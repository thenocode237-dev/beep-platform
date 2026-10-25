"use client";

import { useState, type FormEvent } from "react";
import { Alert, Button, TextField } from "@beep/ui";
import { errorMessage, send } from "@/lib/client";
import { entitlementLabels } from "@/lib/format";
import type { Plan, PlanDraft } from "@/lib/api";

function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [done, setDone] = useState<string>();
  async function run(fn: () => ReturnType<typeof send>, success: string) {
    setBusy(true);
    setError(undefined);
    setDone(undefined);
    const r = await fn();
    setBusy(false);
    if (!r.ok) return setError(errorMessage(r.code));
    setDone(success);
    window.setTimeout(() => window.location.reload(), 600);
  }
  return { busy, error, done, run };
}

function Feedback({ error, done }: { error?: string; done?: string }) {
  return (
    <>
      {error && <Alert tone="danger">{error}</Alert>}
      {done && <Alert tone="success">{done}</Alert>}
    </>
  );
}

const TotpField = () => (
  <TextField
    name="totp"
    label="Code de vérification"
    hint="Action sensible : saisissez le code actuel de votre application d'authentification."
    inputMode="numeric"
    autoComplete="one-time-code"
    pattern="[0-9]{6}"
    maxLength={6}
    required
  />
);

const field = (e: FormEvent<HTMLFormElement>, name: string) => String(new FormData(e.currentTarget).get(name) ?? "");

function Select({ id, name, label, options, defaultValue }: { id: string; name: string; label: string; options: [string, string][]; defaultValue?: string }) {
  return (
    <div className="bp-field">
      <label className="bp-field__label" htmlFor={id}>
        {label}
      </label>
      <select id={id} name={name} className="bp-input" defaultValue={defaultValue}>
        {options.map(([v, l]) => (
          <option key={v} value={v}>
            {l}
          </option>
        ))}
      </select>
    </div>
  );
}

/** Paiement hors ligne (virement, espèces) : prolonge l'abonnement et émet la facture. */
export function ManualPaymentForm({ workspaceId, plans }: { workspaceId: string; plans: Plan[] }) {
  const a = useAction();
  const paid = plans.filter((p) => p.priceXaf > 0);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const body = {
          plan: field(e, "plan"),
          cycle: field(e, "cycle"),
          amountXaf: Number(field(e, "amount")),
          reference: field(e, "reference"),
          country: field(e, "country") || undefined,
          totp: field(e, "totp"),
        };
        a.run(() => send("POST", `/workspaces/${workspaceId}/payments`, body), "Paiement enregistré, facture émise et envoyée.");
      }}
    >
      <p className="muted">Enregistre un virement ou des espèces reçus hors ligne. L'abonnement est prolongé et la facture est émise comme pour un paiement en ligne.</p>
      <Feedback {...a} />
      <Select id="mp-plan" name="plan" label="Plan" options={paid.map((p) => [p.code, p.name])} />
      <Select id="mp-cycle" name="cycle" label="Période" options={[["monthly", "Mensuel"], ["quarterly", "Trimestriel"], ["annual", "Annuel"]]} />
      <TextField name="amount" label="Montant TTC encaissé (FCFA)" inputMode="numeric" pattern="[0-9]+" required />
      <TextField name="reference" label="Référence du virement / quittance (journalisée)" required minLength={3} maxLength={500} />
      <TextField name="country" label="Pays de facturation" hint="Code à 2 lettres (CM, GA). Vide : pays par défaut." maxLength={2} />
      <TotpField />
      <Button type="submit" loading={a.busy}>
        Enregistrer le paiement
      </Button>
    </form>
  );
}

/** Période offerte sur un plan, ou retour immédiat au plan Starter. */
export function PlanChangeForm({ workspaceId, plans, current }: { workspaceId: string; plans: Plan[]; current: string }) {
  const a = useAction();
  const [plan, setPlan] = useState(current === "starter" ? (plans.find((p) => p.priceXaf > 0)?.code ?? "starter") : "starter");
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const until = field(e, "until");
        const body = { plan, until: plan === "starter" ? null : new Date(`${until}T23:59:59Z`).toISOString(), reason: field(e, "reason"), totp: field(e, "totp") };
        a.run(() => send("PUT", `/workspaces/${workspaceId}/plan`, body), "Plan modifié.");
      }}
    >
      <p className="muted">Plan actuel : {current}. Offre une période sans paiement (partenaire, geste commercial) ou rétablit Starter immédiatement.</p>
      <Feedback {...a} />
      <div className="bp-field">
        <label className="bp-field__label" htmlFor="pc-plan">
          Nouveau plan
        </label>
        <select id="pc-plan" className="bp-input" value={plan} onChange={(ev) => setPlan(ev.target.value)}>
          {plans.map((p) => (
            <option key={p.code} value={p.code}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      {plan !== "starter" && <TextField name="until" type="date" label="Offert jusqu'au (inclus)" required />}
      <TextField name="reason" label="Motif (journalisé)" required minLength={3} maxLength={500} />
      <TotpField />
      <Button type="submit" variant="secondary" loading={a.busy}>
        Appliquer le changement
      </Button>
    </form>
  );
}

export function CouponForm({ plans }: { plans: Plan[] }) {
  const a = useAction();
  const [kind, setKind] = useState<"percent" | "amount">("percent");
  const [chosen, setChosen] = useState<string[]>([]);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const value = Number(field(e, "value"));
        const max = field(e, "max");
        const until = field(e, "until");
        const body = {
          code: field(e, "code"),
          percentOff: kind === "percent" ? value : undefined,
          amountOffXaf: kind === "amount" ? value : undefined,
          maxRedemptions: max ? Number(max) : null,
          validUntil: until ? new Date(`${until}T23:59:59Z`).toISOString() : null,
          plans: chosen,
          totp: field(e, "totp"),
        };
        a.run(() => send("POST", "/coupons", body), "Coupon créé.");
      }}
    >
      <Feedback {...a} />
      <TextField name="code" label="Code" hint="3 à 32 caractères : lettres, chiffres, - et _." required minLength={3} maxLength={32} autoComplete="off" />
      <div className="bp-field">
        <label className="bp-field__label" htmlFor="cp-kind">
          Type de remise
        </label>
        <select id="cp-kind" className="bp-input" value={kind} onChange={(ev) => setKind(ev.target.value as "percent" | "amount")}>
          <option value="percent">Pourcentage</option>
          <option value="amount">Montant fixe (FCFA)</option>
        </select>
      </div>
      <TextField name="value" label={kind === "percent" ? "Pourcentage (1–100)" : "Montant (FCFA)"} inputMode="numeric" pattern="[0-9]+" required />
      <TextField name="max" label="Utilisations maximum" hint="Vide : illimité." inputMode="numeric" pattern="[0-9]*" />
      <TextField name="until" type="date" label="Valable jusqu'au (inclus)" hint="Vide : sans échéance." />
      <fieldset className="bp-field">
        <legend className="bp-field__label">Plans concernés (aucun coché : tous les plans payants)</legend>
        {plans
          .filter((p) => p.priceXaf > 0)
          .map((p) => (
            <label key={p.code} className="check">
              <input
                type="checkbox"
                checked={chosen.includes(p.code)}
                onChange={(ev) => setChosen(ev.target.checked ? [...chosen, p.code] : chosen.filter((c) => c !== p.code))}
              />{" "}
              {p.name}
            </label>
          ))}
      </fieldset>
      <TotpField />
      <Button type="submit" loading={a.busy}>
        Créer le coupon
      </Button>
    </form>
  );
}

export function CouponToggle({ code, active }: { code: string; active: boolean }) {
  const a = useAction();
  return (
    <form
      className="bp-row"
      onSubmit={(e) => {
        e.preventDefault();
        a.run(() => send("PUT", `/coupons/${encodeURIComponent(code)}/active`, { active: !active, totp: field(e, "totp") }), active ? "Coupon désactivé." : "Coupon réactivé.");
      }}
    >
      <TextField name="totp" label="Code TOTP" inputMode="numeric" pattern="[0-9]{6}" maxLength={6} required autoComplete="one-time-code" />
      <Button type="submit" variant="secondary" loading={a.busy}>
        {active ? "Désactiver" : "Réactiver"}
      </Button>
      <Feedback {...a} />
    </form>
  );
}

const rightKeys = Object.keys(entitlementLabels);

/** Éditeur de brouillon d'un plan : prix, visibilité, droits. Rien n'atteint le core avant « Publier ». */
export function PlanDraftEditor({ plan, draft, canPublish }: { plan: Plan | null; draft: PlanDraft | null; canPublish: boolean }) {
  const a = useAction();
  const source = draft ?? plan;
  const code = source ? ("code" in source ? source.code : "") : "";
  const [rights, setRights] = useState<Record<string, number>>(Object.fromEntries(rightKeys.map((k) => [k, source?.entitlements[k] ?? (k.startsWith("features.") ? 0 : 0)])));
  const baseVersion = draft ? draft.baseVersion : (plan?.version ?? 0);
  const outdated = Boolean(draft && plan && draft.baseVersion !== plan.version);
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const submitter = (e.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
        const planCode = code || field(e, "code");
        const q = field(e, "quarterly");
        const y = field(e, "annual");
        const body = {
          name: field(e, "name"),
          priceXaf: Number(field(e, "price")),
          priceQuarterlyXaf: q ? Number(q) : null,
          priceAnnualXaf: y ? Number(y) : null,
          isPublic: new FormData(e.currentTarget).get("public") === "on",
          entitlements: rights,
          baseVersion,
          note: field(e, "note") || null,
        };
        if (submitter?.value === "publish") {
          a.run(async () => {
            const saved = await send("PUT", `/plan-drafts/${planCode}`, body);
            return saved.ok ? send("POST", `/plan-drafts/${planCode}/publish`, { totp: field(e, "totp") }) : saved;
          }, "Plan publié : la nouvelle version est en ligne.");
        } else if (submitter?.value === "discard") {
          a.run(() => send("DELETE", `/plan-drafts/${planCode}`), "Brouillon supprimé.");
        } else {
          a.run(() => send("PUT", `/plan-drafts/${planCode}`, body), "Brouillon enregistré (non publié).");
        }
      }}
    >
      {outdated && <Alert tone="danger">Ce brouillon part de la version {draft?.baseVersion} ; la version en ligne est la {plan?.version}. Enregistrez-le de nouveau pour le reprendre sur la version en ligne.</Alert>}
      <Feedback {...a} />
      {!code && <TextField name="code" label="Code du plan" hint="Minuscules, chiffres, - et _ (ex. business)." required pattern="[a-z][a-z0-9_-]{1,31}" />}
      <TextField name="name" label="Nom" defaultValue={source?.name} required maxLength={60} />
      <TextField name="price" label="Prix mensuel TTC (FCFA)" inputMode="numeric" pattern="[0-9]+" defaultValue={source ? String(source.priceXaf) : "0"} required />
      <TextField
        name="quarterly"
        label="Prix trimestriel (FCFA)"
        hint="Vide : −10 % calculé."
        inputMode="numeric"
        pattern="[0-9]*"
        defaultValue={draft ? (draft.priceQuarterlyXaf?.toString() ?? "") : plan ? String(plan.priceQuarterlyXaf) : ""}
      />
      <TextField
        name="annual"
        label="Prix annuel (FCFA)"
        hint="Vide : −30 % calculé."
        inputMode="numeric"
        pattern="[0-9]*"
        defaultValue={draft ? (draft.priceAnnualXaf?.toString() ?? "") : plan ? String(plan.priceAnnualXaf) : ""}
      />
      <label className="check">
        <input type="checkbox" name="public" defaultChecked={source ? ("isPublic" in source ? source.isPublic : false) : false} /> Commercialisé (sinon affiché « bientôt »)
      </label>
      <fieldset className="bp-stack">
        <legend className="bp-field__label">Droits (-1 = illimité ; fonctionnalités : 0 ou 1)</legend>
        {rightKeys.map((k) => (
          <label key={k} className="bp-field">
            <span className="bp-field__label">{entitlementLabels[k]}</span>
            <input
              className="bp-input"
              inputMode="numeric"
              pattern="-?[0-9]+"
              value={rights[k]}
              onChange={(ev) => setRights({ ...rights, [k]: Number(ev.target.value.replace(/[^0-9-]/g, "")) || 0 })}
            />
          </label>
        ))}
      </fieldset>
      <TextField name="note" label="Note interne" defaultValue={draft?.note ?? ""} maxLength={500} />
      {canPublish && <TotpField />}
      <div className="bp-row">
        <Button type="submit" name="intent" value="save" variant="secondary" loading={a.busy}>
          Enregistrer le brouillon
        </Button>
        {canPublish && (
          <Button type="submit" name="intent" value="publish" loading={a.busy}>
            Publier vers le core
          </Button>
        )}
        {draft && (
          <Button type="submit" name="intent" value="discard" variant="secondary" loading={a.busy}>
            Supprimer le brouillon
          </Button>
        )}
      </div>
    </form>
  );
}
