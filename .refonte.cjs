const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, 'beep-platform');
const file = path.join(root, 'packages/design-tokens/tokens.json');
const t = JSON.parse(fs.readFileSync(file, 'utf8'));
t.$description = 'Beep — direction Atelier, inspirée de Vendeer (24 octobre 2026). Ivoire, orange terre cuite, forêt, Manrope et DM Sans. Source unique des tokens des deux applications.';
t.primitive.color.ink = {50:'#F8F7F2',100:'#EFECE3',200:'#DCDED5',300:'#B7C2B4',400:'#879683',500:'#65715B',600:'#58634F',700:'#3E5143',800:'#2D3E32',900:'#222B25',950:'#18231C'};
delete t.primitive.color.violet;
delete t.primitive.color.signal;
t.primitive.color.terracotta = {50:'#FCF1E9',100:'#F5E8DD',200:'#F9B99B',300:'#FFAD86',400:'#FF815B',500:'#E94B23',600:'#BD3516',700:'#9F2D12',800:'#822710',900:'#4B271E'};
t.primitive.color.forest = {100:'#E8EDE3',300:'#BCD0BA',500:'#44654F',700:'#243D30',800:'#1C3026',900:'#14251C'};
t.primitive.font['family-display'] = "var(--font-display), 'Manrope', ui-sans-serif, system-ui, sans-serif";
t.primitive.font['family-body'] = "var(--font-body), 'DM Sans', ui-sans-serif, system-ui, sans-serif";
t.primitive.radius = {sm:'0.25rem',md:'0.375rem',lg:'0.5rem',xl:'0.75rem',pill:'999px'};
const l=t.semantic.light, d=t.semantic.dark;
Object.assign(l, {'color-bg-inverse':'{color.forest.700}','color-text-primary':'{color.ink.900}','color-text-inverse':'{color.ink.50}','color-text-link':'{color.terracotta.700}','color-action-primary-bg':'{color.terracotta.600}','color-action-primary-bg-hover':'{color.terracotta.700}','color-action-primary-bg-active':'{color.terracotta.800}','color-action-primary-text':'{color.white}','color-action-accent-bg':'{color.forest.700}','color-action-accent-bg-hover':'{color.forest.800}','color-action-accent-text':'{color.white}','color-brand-tint':'{color.terracotta.50}','color-brand-tint-strong':'{color.terracotta.100}','color-focus-ring':'{color.terracotta.600}','color-surface-sage':'{color.forest.100}','color-surface-warm':'{color.terracotta.100}','color-brand-signature':'{color.terracotta.600}','color-on-sage':'{color.forest.700}'});
Object.assign(d, {'color-text-link':'{color.terracotta.300}','color-action-primary-bg':'{color.terracotta.300}','color-action-primary-bg-hover':'{color.terracotta.200}','color-action-primary-bg-active':'{color.terracotta.100}','color-action-accent-bg':'{color.forest.300}','color-action-accent-bg-hover':'{color.forest.100}','color-action-accent-text':'{color.forest.900}','color-brand-tint':'{color.terracotta.900}','color-brand-tint-strong':'{color.terracotta.800}','color-focus-ring':'{color.terracotta.300}','color-surface-sage':'{color.forest.800}','color-surface-warm':'{color.terracotta.900}','color-brand-signature':'{color.terracotta.300}','color-on-sage':'{color.forest.300}'});
for (const theme of [l,d]) {
  theme['color-scene-forest']='{color.forest.700}'; theme['color-scene-deep']='{color.forest.900}'; theme['color-scene-paper']='{color.ink.50}'; theme['color-scene-sage']='{color.forest.100}'; theme['color-scene-ink']='{color.ink.900}'; theme['color-scene-muted']='{color.ink.600}'; theme['color-scene-orange']='{color.terracotta.500}'; theme['color-scene-peach']='{color.terracotta.200}';
}
Object.assign(t.semantic.static, {'text-display':'clamp(2.75rem, 5.1vw, 4.5rem)','text-h2':'clamp(2rem, 3.4vw, 3rem)','tracking-display':'-0.055em','gutter-page':'1.375rem','gutter-page-md':'2.5rem','container-wide':'82.5rem','container-narrow':'32rem'});
t.component.button.radius='{radius.sm}'; t.component.button.height='3.25rem'; t.component.input.height='3.25rem'; t.component.nav.height='5.5rem';
fs.writeFileSync(file,JSON.stringify(t,null,2)+'\n');
const source='C:/Users/nmogogabiapsi.ext/Desktop/Vendeer';
const fonts=path.join(root,'packages/design-tokens/assets/fonts'); fs.mkdirSync(fonts,{recursive:true});
for(const f of ['manrope-latin.woff2','dm-sans-latin.woff2'])fs.copyFileSync(path.join(source,'packages/design-tokens/assets/fonts',f),path.join(fonts,f));
fs.mkdirSync(path.join(fonts,'licenses'),{recursive:true});
for(const f of ['Manrope-OFL.txt','DM-Sans-OFL.txt'])fs.copyFileSync(path.join(source,'packages/design-tokens/assets/fonts/licenses',f),path.join(fonts,'licenses',f));
for(const [f,prefix] of [['beep/web/app/[locale]/layout.tsx','../../../../'],['backoffice/web/app/layout.tsx','../../../']]){
  const target=path.join(root,f); let s=fs.readFileSync(target,'utf8').replace('import { Bricolage_Grotesque, Inter } from "next/font/google";','import localFont from "next/font/local";');
  s=s.replace('const body = Inter({ subsets: ["latin"], variable: "--font-body", display: "swap" });',`const body = localFont({ src: "${prefix}packages/design-tokens/assets/fonts/dm-sans-latin.woff2", variable: "--font-body", display: "swap", weight: "100 1000" });`);
  s=s.replace('const display = Bricolage_Grotesque({ subsets: ["latin"], variable: "--font-display", display: "swap" });',`const display = localFont({ src: "${prefix}packages/design-tokens/assets/fonts/manrope-latin.woff2", variable: "--font-display", display: "swap", weight: "200 800" });`);
  s=s.replace('#F6F7FC','#F8F7F2').replace('#0A0B1E','#18231C'); fs.writeFileSync(target,s);
}
for(const f of ['infra/caddy/Caddyfile','infra/caddy/Caddyfile.dev']){ const target=path.join(root,f); let s=fs.readFileSync(target,'utf8'); s=s.replace('/icon.svg /favicon.ico','/assets/* /icon.svg /favicon.ico'); fs.writeFileSync(target,s); }
const docker=path.join(root,'beep/web/Dockerfile'); fs.writeFileSync(docker,fs.readFileSync(docker,'utf8').replace('USER app','COPY --from=build --chown=app:app /repo/beep/web/public ./beep/web/public\nUSER app'));
