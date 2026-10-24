"use client";

import { useState, type FormEvent } from "react";
import { Alert, Button, TextField } from "@beep/ui";
import { post } from "@/lib/client";

const messages: Record<string, string> = {
  invalid_credentials: "Identifiants ou code de vérification incorrects.",
  rate_limited: "Trop de tentatives. Patientez quelques minutes.",
  network: "Connexion impossible. Vérifiez le réseau.",
  generic: "Une erreur est survenue. Réessayez.",
};

export function LoginForm() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setLoading(true);
    setError(undefined);
    const f = new FormData(e.currentTarget);
    const res = await post("/auth/login", { email: f.get("email"), password: f.get("password"), totp: f.get("totp") });
    if (res.ok) {
      window.location.assign("/users");
      return;
    }
    setLoading(false);
    setError(messages[res.code ?? "generic"] ?? messages.generic);
  }

  return (
    <form className="bp-stack" onSubmit={onSubmit}>
      {error && <Alert tone="danger">{error}</Alert>}
      <TextField name="email" type="email" inputMode="email" autoComplete="username" label="E-mail professionnel" required />
      <TextField name="password" type="password" autoComplete="current-password" label="Mot de passe" required />
      <TextField
        name="totp"
        label="Code de vérification"
        hint="Code à 6 chiffres de votre application d'authentification (obligatoire)."
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        required
      />
      <Button type="submit" block loading={loading}>Se connecter</Button>
    </form>
  );
}
