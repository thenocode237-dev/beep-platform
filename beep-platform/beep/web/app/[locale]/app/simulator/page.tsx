import {notFound} from "next/navigation";
import {Simulator} from "@/components/Simulator";
import {coreGet} from "@/lib/core";
import {isLocale,href} from "@/lib/i18n";
export const dynamic="force-dynamic";
export default async function Page({params}:{params:Promise<{locale:string}>}) {
 const {locale}=await params;if(!isLocale(locale))notFound();const en=locale==="en";
 const profiles=await coreGet<{items:string[]}>("/deeplinks/profiles");
 return <div className="app-page bp-stack bp-stack--lg"><a className="text-link" href={href(locale,"/app/links")}>{en?"Back to links":"Retour aux liens"}</a><h1>{en?"App simulator":"Simulateur d’applications"}</h1><p className="muted">{en?"Preview the destination across visitor profiles before sharing.":"Vérifiez la destination selon le profil du visiteur avant de partager."}</p><section className="bp-card bp-card--lg">{profiles.data?<Simulator locale={locale} profiles={profiles.data.items}/>:<p role="alert">{en?"Simulator unavailable.":"Simulateur indisponible."}</p>}</section></div>;
}
