/** Explicit deferred-link handoff. Never reads device identifiers or the clipboard. */
export async function resolveDeferred({baseUrl,appId,token,platform,scheme,fetchImpl=globalThis.fetch}) {
 const url=new URL(baseUrl);
 if(url.protocol!=="https:"||url.username||url.password||url.search||url.hash||url.pathname!=="/"||!/^[-0-9a-f]{36}$/i.test(appId)||!/^[A-Za-z0-9_-]{32,128}$/.test(token)||!["android","ios"].includes(platform)||!/^[a-z][a-z0-9+.-]{1,39}$/.test(scheme)||["http","https","javascript","file","data","intent"].includes(scheme))throw new Error("invalid_configuration");
 const response=await fetchImpl(new URL("/v1/mobile/resolve",url),{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({appId,token,platform}),signal:AbortSignal.timeout(10000),redirect:"error",credentials:"omit",cache:"no-store"});
 if(!response.ok)throw new Error(response.status===400?"invalid_or_consumed_token":"resolution_failed");
 const result=await response.json();if(typeof result.destination!=="string"||new URL(result.destination).protocol!==scheme+":")throw new Error("invalid_destination");return result.destination;
}
export function tokenFromInstallReferrer(referrer){const value=new URLSearchParams(referrer).get("beep_token");return value&&/^[A-Za-z0-9_-]{32,128}$/.test(value)?value:null;}
