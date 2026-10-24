import { useId } from "react";
import type { InputHTMLAttributes } from "react";

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, "id"> & {
  label: string;
  hint?: string;
  error?: string;
};

/** Champ avec label visible, aide persistante et erreur reliée par aria-describedby. */
export function TextField({ label, hint, error, ...input }: Props) {
  const id = useId();
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(" ") || undefined;
  return (
    <div className="bp-field">
      <label className="bp-field__label" htmlFor={id}>{label}</label>
      <input id={id} className="bp-input" aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...input} />
      {hint && <p id={`${id}-hint`} className="bp-field__hint">{hint}</p>}
      {error && <p id={`${id}-error`} className="bp-field__error">{error}</p>}
    </div>
  );
}
