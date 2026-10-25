import "server-only";
import {readFile} from "node:fs/promises";
import path from "node:path";
import type {Locale} from "@/lib/i18n";
export const posts=[{slug:"premier-lien",date:"2026-10-25",fr:"Un lien qui reste utile après le partage",en:"A link that stays useful after sharing"}];
export async function readPost(locale:Locale,slug:string){if(!posts.some(p=>p.slug===slug))return null;return readFile(path.join(process.cwd(),"content","blog",locale,`${slug}.md`),"utf8");}
