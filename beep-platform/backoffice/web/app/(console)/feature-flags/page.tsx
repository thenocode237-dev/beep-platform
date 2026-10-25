import {getFlags,getMe} from "@/lib/api";
import {FlagForm} from "@/components/TrustForms";
import {Unavailable} from "@/components/Unavailable";
export default async function Page(){const [flags,me]=await Promise.all([getFlags(),getMe()]);return <div className="page bp-stack bp-stack--lg"><h1>Feature flags.</h1><p>Activer progressivement les règles de lien et le simulateur. Une publication prend effet sur les prochains appels.</p>{!flags.data?<Unavailable status={flags.status} what="les feature flags"/>:flags.data.items.map(flag=><section className="bp-card" key={`${flag.key}-${flag.version}`}><FlagForm flag={flag} canWrite={me.data?.permissions.includes("FLAGS_WRITE")??false}/></section>)}</div>;}
