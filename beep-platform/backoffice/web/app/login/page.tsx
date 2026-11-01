import { redirect } from "next/navigation";
import { Icon, Logo } from "@beep/ui";
import { LoginForm } from "@/components/LoginForm";
import { getMe } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function Login() {
  const me = await getMe();
  if (me.data) redirect("/users");
  return (
    <main id="main" className="staff-login">
      <aside className="staff-login__visual">
        <Logo href="/login" label="Beepclick back office" />
        <div className="staff-login__message">
          <h2>
            Les bonnes connexions
            <br />
            <span>commencent ici.</span>
          </h2>
          <p>Un espace pour accompagner celles et ceux qui font vivre Beepclick.</p>
        </div>
        <div className="staff-art" aria-hidden="true">
          <span />
          <span />
          <span />
          <div>
            <Icon name="link" size={52} />
          </div>
        </div>
        <p className="staff-login__caption">Beepclick · Espace équipe</p>
      </aside>
      <div className="staff-login__content">
        <div className="staff-login__mobile-logo">
          <Logo href="/login" label="Beepclick back office" />
        </div>
        <div className="staff-login__form bp-stack bp-stack--lg">
          <div className="bp-stack">
            <h1>
              Bienvenue dans
              <br />
              les coulisses.
            </h1>
            <p className="muted">
              Connectez-vous à votre espace équipe. L’accès est réservé au
              personnel Beepclick.
            </p>
          </div>
          <LoginForm />
          <p className="staff-login__security">
            <Icon name="shield" size={16} />
            Double authentification obligatoire.
          </p>
        </div>
      </div>
    </main>
  );
}
