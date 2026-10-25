import {notFound} from "next/navigation";
import {PublicPage} from "@/components/PublicPage";
import {posts} from "@/lib/blog";
import {href,isLocale} from "@/lib/i18n";
export const metadata={title:"Journal · Beep"};
export default async function Page({params}:{params:Promise<{locale:string}>}){const {locale}=await params;if(!isLocale(locale))notFound();return <PublicPage locale={locale} path="/blog" title={locale==="en"?"Ideas for better sharing.":"Des idées pour mieux partager."}>{posts.map(p=><article className="bp-card bp-card--lg bp-stack" key={p.slug}><time dateTime={p.date}>{p.date}</time><h2><a href={href(locale,`/blog/${p.slug}`)}>{p[locale]}</a></h2><a className="text-link" href={href(locale,`/blog/${p.slug}`)}>{locale==="en"?"Read article":"Lire l’article"}</a></article>)}</PublicPage>;}
