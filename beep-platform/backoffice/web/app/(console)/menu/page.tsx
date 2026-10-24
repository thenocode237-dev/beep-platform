import { Icon } from "@beep/ui";
import { getMe } from "@/lib/api";
import { navItems } from "@/lib/nav";

/** Menu complet sur mobile (la barre inférieure ne montre que les modules les plus fréquents). */
export default async function Menu() {
  const me = await getMe();
  const allowed = navItems.filter((i) => me.data?.permissions.includes(i.permission));
  return (
    <div className="bp-stack bp-stack--lg page">
      <h1>Modules</h1>
      <ul className="menu-list">
        {allowed.map((i) => (
          <li key={i.href}>
            <a className="bp-card" href={i.href}>
              <Icon name={i.icon} size={22} />
              <span>{i.label}</span>
              <Icon name="arrow-right" size={18} />
            </a>
          </li>
        ))}
      </ul>
    </div>
  );
}
