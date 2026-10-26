"use client";
export function SsoLoginForm({ en }: { en: boolean }) {
  return (
    <form
      className="bp-stack"
      onSubmit={(e) => {
        e.preventDefault();
        const id = String(new FormData(e.currentTarget).get("workspace"));
        window.location.assign(
          "/v1/auth/sso/" + encodeURIComponent(id) + "/start",
        );
      }}
    >
      <label className="bp-field">
        <span>
          {en
            ? "Workspace ID provided by your administrator"
            : "Identifiant du workspace fourni par votre administrateur"}
        </span>
        <input
          name="workspace"
          required
          pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
          autoComplete="off"
        />
      </label>
      <button className="bp-btn bp-btn--secondary">
        {en ? "Continue with SSO" : "Continuer avec SSO"}
      </button>
    </form>
  );
}
