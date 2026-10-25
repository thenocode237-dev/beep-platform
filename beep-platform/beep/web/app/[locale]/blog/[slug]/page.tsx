import {notFound} from "next/navigation";
import {PublicPage} from "@/components/PublicPage";
import {posts,readPost} from "@/lib/blog";
import {href,isLocale} from "@/lib/i18n";
export default async function Page({params}:{params:Promise<{locale:string;slug:string}>}){const {locale,slug}=await params;if(!isLocale(locale))notFound();const post=posts.find(p=>p.slug===slug);if(!post)notFound();const markdown=await readPost(locale,slug);if(!markdown)notFound();return <PublicPage locale={locale} path={`/blog/${slug}`} title={post[locale]}><a className="text-link" href={href(locale,"/blog")}>{locale==="en"?"Back to journal":"Retour au journal"}</a><article className="public-prose bp-stack">{markdown.split(/\n\s*\n/).slice(1).map((block,i)=>block.startsWith("## ")?<h2 key={i}>{block.slice(3)}</h2>:<p key={i}>{block}</p>)}</article></PublicPage>;}
