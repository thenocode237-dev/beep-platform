import { publicMetadata } from "@/lib/seo";
export const generateMetadata = publicMetadata("/status");
import {notFound} from "next/navigation";
import {PublicPage} from "@/components/PublicPage";
import {isLocale} from "@/lib/i18n";
export const dynamic="force-dynamic";
async function check(url:string){try{const r=await fetch(url,{cache:"no-store",signal:AbortSignal.timeout(3000)});return r.ok;}catch{return false;}}
export default async function Page({params}:{params:Promise<{locale:string}>}){
 const {locale}=await params;if(!isLocale(locale))notFound();const en=locale==="en";
 const [core,bo]=await Promise.all([check((process.env.CORE_INTERNAL_URL??"http://core:8080/v1").replace(/\/v1\/?$/,"/healthz")),check(process.env.BO_STATUS_URL??"http://backoffice-api:8081/healthz")]);
 return <PublicPage locale={locale} path="/status" title={en?"Service status.":"État des services."} intro={en?"Live checks when this page is loaded.":"Vérifications en direct au chargement de cette page."}><p className="bp-card" role="status">{core&&bo?(en?"Services respond normally.":"Les services répondent normalement."):(en?"Some services could not be reached.":"Certains services n’ont pas pu être joints.")}</p><dl className="bp-card public-plan-features">{[[en?"Website":"Site web",true],["API BeepClick",core],["Back office",bo]].map(([name,ok])=><div key={String(name)}><dt>{name}</dt><dd>{ok?(en?"Available":"Disponible"):(en?"Unreachable":"Injoignable")}</dd></div>)}</dl><p className="muted">{en?"Checked":"Vérifié le"} <time dateTime={new Date().toISOString()}>{new Date().toLocaleString(en?"en-GB":"fr-FR",{timeZone:"Europe/Paris"})}</time></p><p className="muted small">{en?"These checks cover HTTP availability. They do not verify payment providers, custom-domain certificates or end-to-end redirects.":"Ces contrôles portent sur la disponibilité HTTP. Ils ne vérifient pas les fournisseurs de paiement, les certificats des domaines personnels ni les redirections de bout en bout."}</p><a className="bp-btn bp-btn--secondary" href={locale==="en"?"/en/status":"/status"}>{en?"Refresh checks":"Actualiser les vérifications"}</a></PublicPage>;
}
