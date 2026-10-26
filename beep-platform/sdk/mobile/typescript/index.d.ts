export interface ResolveOptions {baseUrl:string;appId:string;token:string;platform:"android"|"ios";scheme:string;fetchImpl?:typeof fetch;}
export function resolveDeferred(options:ResolveOptions):Promise<string>;
export function tokenFromInstallReferrer(referrer:string):string|null;
