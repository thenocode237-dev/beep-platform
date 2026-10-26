"use client";
import { useEffect, useId, useState } from "react";
import { send } from "@/lib/api";
type Workspace = { id: string; name: string; role: string };
export function WorkspacePicker({ en }: { en: boolean }) {
  const id = useId();
  const [items, setItems] = useState<Workspace[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  useEffect(() => {
    let active = true;
    void send<{ items: Workspace[] }>("GET", "/workspaces").then((r) => {
      if (active && r.ok) setItems(r.data?.items ?? []);
    });
    return () => {
      active = false;
    };
  }, []);
  return (
    <div className="bp-field eco-picker">
      <label className="bp-field__label" htmlFor={id}>
        Workspace
      </label>
      <select
        id={id}
        className="bp-input"
        value=""
        disabled={busy}
        onChange={async (e) => {
          setBusy(true);
          setError("");
          const r = await send("POST", "/workspaces/select", {
            id: e.target.value,
          });
          if (r.ok) window.location.assign(en ? "/en/app" : "/app");
          else {
            setError(
              en
                ? "Unable to switch workspace."
                : "Impossible de changer de workspace.",
            );
            setBusy(false);
          }
        }}
      >
        <option value="" disabled>
          {en ? "Switch workspace…" : "Changer de workspace…"}
        </option>
        {items.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name} · {w.role}
          </option>
        ))}
      </select>
      {error && <span role="alert">{error}</span>}
    </div>
  );
}
