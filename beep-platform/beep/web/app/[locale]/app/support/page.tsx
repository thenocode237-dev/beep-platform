import {notFound} from "next/navigation";
import {href,isLocale} from "@/lib/i18n";
export const dynamic="force-dynamic";
export default async function Page({params}:{params:Promise<{locale:string}>}) {
 const {locale}=await params;if(!isLocale(locale))notFound();const en=locale==="en";
 const number=process.env.SUPPORT_WHATSAPP?.replace(/[ +()-]/g,"")??"";
 const valid=/^[1-9][0-9]{7,14}$/.test(number);
 const email=process.env.SUPPORT_EMAIL??"";
 return <div className="app-page bp-stack bp-stack--lg"><h1>{en?"Help from the Beepclick team.":"L’équipe Beepclick vous accompagne."}</h1><section className="bp-card bp-card--lg bp-stack"><h2>WhatsApp</h2><p>{en?"Explain your issue and include the link involved. Never send your password, payment PIN or authenticator code.":"Décrivez votre problème et indiquez le lien concerné. Ne transmettez jamais votre mot de passe, code de paiement ou code de sécurité."}</p>{valid?<a className="bp-btn" href={`https://wa.me/${number}?text=${encodeURIComponent(en?"Hello Beepclick, I need help.":"Bonjour Beepclick, j’ai besoin d’aide.")}`} target="_blank" rel="noopener noreferrer">{en?"Contact support on WhatsApp":"Contacter le support sur WhatsApp"}</a>:<p className="muted">{en?"WhatsApp support is not yet available.":"Le support WhatsApp n’est pas encore disponible."}</p>}{/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)&&<a className="text-link" href={`mailto:${email}`}>{en?"Contact by email":"Contacter par e-mail"}</a>}</section><a className="text-link" href={href(locale,"/report")}>{en?"Report an unsafe link":"Signaler un lien dangereux"}</a><a className="text-link" href={href(locale,"/status")}>{en?"Check service status":"Consulter l’état des services"}</a></div>;
}
