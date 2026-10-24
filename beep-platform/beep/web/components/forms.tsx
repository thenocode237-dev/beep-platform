"use client";

import { useEffect, useState, type FormEvent } from "react";
import { Alert, Button, Icon, TextField } from "@beep/ui";
import { post } from "@/lib/api";
import type { Dict } from "@/lib/dictionaries/fr";
import { href, type Locale } from "@/lib/i18n";

type Props = { dict: Dict; locale: Locale };

const errorText = (dict: Dict, code?: string) =>
  dict.auth.errors[(code ?? "generic") as keyof Dict["auth"]["errors"]] ?? dict.auth.errors.generic;

/** Mot de passe avec bascule afficher/masquer (cible tactile 44 px). */
function PasswordField({ dict, label, hint, error, autoComplete }: { dict: Dict; label: string; hint?: string; error?: string; autoComplete: string }) {
  const [shown, setShown] = useState(false);
  return (
    <div className="pw-wrap">
      <TextField
        name="password"
        label={label}
        hint={hint}
        error={error}
        type={shown ? "text" : "password"}
        autoComplete={autoComplete}
        required
        minLength={autoComplete === "new-password" ? 12 : undefined}
      />
      <button
        type="button"
        className="pw-toggle"
        aria-pressed={shown}
        aria-label={shown ? dict.auth.hidePassword : dict.auth.showPassword}
        onClick={() => setShown((s) => !s)}
      >
        <Icon name={shown ? "eye-off" : "eye"} size={20} />
      </button>
    </div>
  );
}

function useSubmit(dict: Dict, run: (data: FormData) => Promise<{ ok: boolean; code?: string }>) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [done, setDone] = useState(false);
  const [code, setCode] = useState<string>();
  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLoading(true);
    setError(undefined);
    const res = await run(new FormData(e.currentTarget));
    setLoading(false);
    setCode(res.code);
    if (res.ok) setDone(true);
    else setError(errorText(dict, res.code));
  }
  return { loading, error, done, code, onSubmit };
}

export function SignupForm({ dict, locale }: Props) {
  const s = useSubmit(dict, async (f) => post("/auth/signup", { email: f.get("email"), password: f.get("password"), locale }));
  if (s.done)
    return (
      <div className="bp-stack" role="status">
        <h2>{dict.auth.signup.doneTitle}</h2>
        <p>{dict.auth.signup.done}</p>
      </div>
    );
  return (
    <form className="bp-stack" onSubmit={s.onSubmit} noValidate={false}>
      {s.error && <Alert tone="danger">{s.error}</Alert>}
      <TextField name="email" type="email" inputMode="email" autoComplete="email" label={dict.auth.email} required />
      <PasswordField dict={dict} label={dict.auth.password} hint={dict.auth.signup.passwordHint} autoComplete="new-password" />
      <Button type="submit" block loading={s.loading}>{dict.auth.signup.submit}</Button>
      <p className="auth-alt">
        {dict.auth.signup.hasAccount} <a href={href(locale, "/login")}>{dict.auth.signup.loginLink}</a>
      </p>
    </form>
  );
}

export function LoginForm({ dict, locale }: Props) {
  const [needTotp, setNeedTotp] = useState(false);
  const s = useSubmit(dict, async (f) => {
    const res = await post("/auth/login", { email: f.get("email"), password: f.get("password"), totp: f.get("totp") || undefined });
    if (res.ok) window.location.assign(href(locale, "/app"));
    if (res.code === "totp_required") setNeedTotp(true);
    return res;
  });
  return (
    <form className="bp-stack" onSubmit={s.onSubmit}>
      {s.error && <Alert tone={s.code === "totp_required" ? "info" : "danger"}>{s.error}</Alert>}
      <TextField name="email" type="email" inputMode="email" autoComplete="username" label={dict.auth.email} required />
      <PasswordField dict={dict} label={dict.auth.password} autoComplete="current-password" />
      {needTotp && (
        <TextField
          name="totp"
          label={dict.auth.login.totp}
          hint={dict.auth.login.totpHint}
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          maxLength={6}
          required
        />
      )}
      <Button type="submit" block loading={s.loading}>{dict.auth.login.submit}</Button>
      <p className="auth-alt"><a href={href(locale, "/forgot-password")}>{dict.auth.login.forgot}</a></p>
      <p className="auth-alt">
        {dict.auth.login.noAccount} <a href={href(locale, "/signup")}>{dict.auth.login.signupLink}</a>
      </p>
    </form>
  );
}

export function ForgotForm({ dict, locale }: Props) {
  const s = useSubmit(dict, async (f) => post("/auth/password/forgot", { email: f.get("email"), locale }));
  if (s.done)
    return (
      <div className="bp-stack">
        <Alert tone="success">{dict.auth.forgot.done}</Alert>
        <a href={href(locale, "/login")}>{dict.auth.forgot.back}</a>
      </div>
    );
  return (
    <form className="bp-stack" onSubmit={s.onSubmit}>
      {s.error && <Alert tone="danger">{s.error}</Alert>}
      <TextField name="email" type="email" inputMode="email" autoComplete="email" label={dict.auth.email} required />
      <Button type="submit" block loading={s.loading}>{dict.auth.forgot.submit}</Button>
    </form>
  );
}

export function ResetForm({ dict, locale, token }: Props & { token?: string }) {
  const s = useSubmit(dict, async (f) => post("/auth/password/reset", { token, password: f.get("password") }));
  if (!token) return <Alert tone="danger">{dict.auth.reset.missing}</Alert>;
  if (s.done)
    return (
      <div className="bp-stack">
        <Alert tone="success">{dict.auth.reset.done}</Alert>
        <a className="bp-btn" href={href(locale, "/login")}>{dict.auth.verify.toLogin}</a>
      </div>
    );
  return (
    <form className="bp-stack" onSubmit={s.onSubmit}>
      {s.error && <Alert tone="danger">{s.error}</Alert>}
      <PasswordField dict={dict} label={dict.auth.password} hint={dict.auth.signup.passwordHint} autoComplete="new-password" />
      <Button type="submit" block loading={s.loading}>{dict.auth.reset.submit}</Button>
    </form>
  );
}

export function VerifyEmail({ dict, locale, token }: Props & { token?: string }) {
  const [state, setState] = useState<"pending" | "ok" | "fail">(token ? "pending" : "fail");
  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    post("/auth/verify-email", { token }).then((r) => !cancelled && setState(r.ok ? "ok" : "fail"));
    return () => {
      cancelled = true;
    };
  }, [token]);
  return (
    <div className="bp-stack" aria-live="polite">
      {state === "pending" && <p>{dict.auth.verify.pending}</p>}
      {state === "ok" && <Alert tone="success">{dict.auth.verify.ok}</Alert>}
      {state === "fail" && <Alert tone="danger">{dict.auth.verify.fail}</Alert>}
      {state !== "pending" && <a className="bp-btn" href={href(locale, "/login")}>{dict.auth.verify.toLogin}</a>}
    </div>
  );
}

export function LogoutButton({ dict, locale }: Props) {
  const [loading, setLoading] = useState(false);
  return (
    <Button
      variant="secondary"
      loading={loading}
      onClick={async () => {
        setLoading(true);
        await post("/auth/logout");
        window.location.assign(href(locale, "/login"));
      }}
    >
      <Icon name="logout" size={18} /> {dict.app.logout}
    </Button>
  );
}

export function TotpSetup({ dict, enabled }: { dict: Dict; enabled: boolean }) {
  const [secret, setSecret] = useState<string>();
  const [on, setOn] = useState(enabled);
  const start = useSubmit(dict, async () => {
    const r = await post<{ secret: string }>("/auth/totp/setup");
    if (r.ok && r.data) setSecret(r.data.secret);
    return r;
  });
  const confirm = useSubmit(dict, async (f) => {
    const r = await post("/auth/totp/enable", { code: f.get("code") });
    if (r.ok) setOn(true);
    return r;
  });
  if (on)
    return <Alert tone="success">{confirm.done ? dict.app.totpDone : dict.app.totpOn}</Alert>;
  if (!secret)
    return (
      <form className="bp-stack" onSubmit={start.onSubmit}>
        <p>{dict.app.totpOff}</p>
        {start.error && <Alert tone="danger">{start.error}</Alert>}
        <Button type="submit" loading={start.loading}>{dict.app.totpStart}</Button>
      </form>
    );
  return (
    <form className="bp-stack" onSubmit={confirm.onSubmit}>
      <p>{dict.app.totpSecret}</p>
      <code className="secret">{secret}</code>
      {confirm.error && <Alert tone="danger">{confirm.error}</Alert>}
      <TextField name="code" label={dict.auth.login.totp} inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required />
      <Button type="submit" loading={confirm.loading}>{dict.app.totpConfirm}</Button>
    </form>
  );
}
