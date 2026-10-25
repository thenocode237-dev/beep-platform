export const dynamic="force-dynamic";
import {LegalPage} from "@/components/LegalPage";
export default async function Page({params}:{params:Promise<{locale:string}>}){return <LegalPage locale={(await params).locale} kind="notice"/>;}
