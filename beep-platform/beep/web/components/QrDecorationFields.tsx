"use client";
import { useState } from "react";
export function QrDecorationFields({
  en,
  allowed,
}: {
  en: boolean;
  allowed: boolean;
}) {
  const [enabled, setEnabled] = useState(false),
    [logo, setLogo] = useState(""),
    [error, setError] = useState("");
  return (
    <fieldset className="bp-stack" disabled={!allowed}>
      <legend>
        {en ? "Advanced appearance · Pro" : "Personnalisation avancée · Pro"}
      </legend>
      {!allowed && (
        <p className="muted small">
          {en
            ? "Upgrade to Pro to customise modules, gradients, logos and frames."
            : "Passez à Pro pour personnaliser les modules, dégradés, logos et cadres."}
        </p>
      )}
      <label className="bp-row">
        <input
          type="checkbox"
          name="advancedEnabled"
          checked={enabled}
          onChange={(e) => setEnabled(e.target.checked)}
        />
        {en ? "Customise this QR" : "Personnaliser ce QR"}
      </label>
      {enabled && (
        <>
          <label className="bp-field">
            {en ? "Module shape" : "Forme des modules"}
            <select name="shape">
              <option value="square">{en ? "Square" : "Carrés"}</option>
              <option value="rounded">{en ? "Rounded" : "Arrondis"}</option>
              <option value="dots">{en ? "Dots" : "Points"}</option>
            </select>
          </label>
          <label className="bp-row">
            <input name="gradientEnabled" type="checkbox" />
            {en ? "Add a gradient" : "Ajouter un dégradé"}
          </label>
          <label className="bp-field">
            {en ? "Second colour" : "Seconde couleur"}
            <input type="color" name="gradient" defaultValue="#304F36" />
          </label>
          <label className="bp-field">
            {en ? "Frame" : "Cadre"}
            <select name="frame">
              <option value="none">{en ? "None" : "Aucun"}</option>
              <option value="scan-fr">SCANNEZ</option>
              <option value="scan">SCAN ME</option>
            </select>
          </label>
          <label className="bp-field">
            {en
              ? "Centre logo (PNG/JPEG, 64 KB, up to 512 × 512)"
              : "Logo central (PNG/JPEG, 64 Ko, jusqu’à 512 × 512)"}
            <input
              type="file"
              accept="image/png,image/jpeg"
              onChange={(e) => {
                setError("");
                setLogo("");
                const file = e.target.files?.[0];
                if (!file) return;
                if (
                  file.size > 65536 ||
                  !["image/png", "image/jpeg"].includes(file.type)
                ) {
                  setError(
                    en
                      ? "Choose a PNG or JPEG up to 64 KB."
                      : "Choisissez un PNG ou JPEG de 64 Ko maximum.",
                  );
                  return;
                }
                const reader = new FileReader();
                reader.onload = () => setLogo(String(reader.result));
                reader.onerror = () =>
                  setError(
                    en
                      ? "File could not be read."
                      : "Lecture du fichier impossible.",
                  );
                reader.readAsDataURL(file);
              }}
            />
          </label>
          <input name="logo" type="hidden" value={logo} />
          {logo && (
            <button
              type="button"
              className="bp-btn bp-btn--ghost"
              onClick={() => setLogo("")}
            >
              {en ? "Remove logo" : "Retirer le logo"}
            </button>
          )}
          <p className="muted small">
            {en
              ? "High error correction and a safe margin are applied with a logo. Test a printed sample before distribution."
              : "Avec un logo, la correction élevée et une marge adaptée sont appliquées. Testez un exemplaire imprimé avant diffusion."}
          </p>
        </>
      )}
      {error && <p role="alert">{error}</p>}
    </fieldset>
  );
}
