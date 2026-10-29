import {notFound} from "next/navigation";
import {PublicPage} from "@/components/PublicPage";
import {ReportForm} from "@/components/ReportForm";
import {isLocale} from "@/lib/i18n";
export const metadata={title:"Signaler un abus · BeepClick"};
export default async function Page({params}:{params:Promise<{locale:string}>}){const {locale}=await params;if(!isLocale(locale))notFound();const en=locale==="en";return <PublicPage locale={locale} path="/report" title={en?"Report an unsafe link.":"Signaler un lien dangereux."} intro={en?"No account required. Our review target is 24 hours.":"Sans compte. Notre objectif est d’examiner les signalements sous 24 heures."}><ReportForm en={en}/></PublicPage>;}
