const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('C:/Users/nmogogabiapsi.ext/Desktop/Vendeer/node_modules/playwright');
const out = path.join(__dirname,'beep-platform/docs/previews');
fs.mkdirSync(out,{recursive:true});
const results=[];
const check=(name,ok,detail)=>{results.push({name,ok,...(detail?{detail}: {})});console.log(`${ok?'OK':'FAIL'} ${name}${detail?' '+detail:''}`);};
(async()=>{
  const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',args:['--disable-gpu','--no-proxy-server']});
  const ctx=await browser.newContext({viewport:{width:1440,height:1000},deviceScaleFactor:1,colorScheme:'light'});
  const page=await ctx.newPage();
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  async function open(url){const r=await page.goto(url,{waitUntil:'networkidle'});await page.evaluate(()=>document.fonts.ready);return r;}
  const base='http://127.0.0.1:3100';
  await open(base+'/');
  check('Accueil FR : nouveau discours',await page.locator('h1').innerText().then(t=>t.includes('Un petit lien')));
  check('Bénéfices Beep présents',await page.locator('.benefit-grid li').count()===3);
  check('Fonctionnalités à venir explicites',await page.locator('.feature .bp-badge').count()===6);
  await page.waitForFunction(()=>{const i=document.querySelector('.preview-phone__photo img');return i.complete&&i.naturalWidth>0;});
  check('Photo de l’aperçu chargée',await page.locator('.preview-phone__photo img').first().evaluate(i=>i.naturalWidth>0));
  check('Légende lisible sur fond dédié',await page.locator('.product-scene figcaption').first().evaluate(e=>getComputedStyle(e).backgroundColor==='rgb(36, 61, 48)'));
  for(const asset of ['/assets/atelier.webp','/assets/workspace.webp','/icon.svg'])check(`Ressource locale ${asset}`,(await ctx.request.get(base+asset)).status()===200);
  await page.locator('#how-it-works').scrollIntoViewIfNeeded();await page.waitForTimeout(300);await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:path.join(out,'accueil-desktop.png'),fullPage:true});
  for(const width of [320,375,390,768,1024,1440]){
    await page.setViewportSize({width,height:900});
    for(const route of ['/','/login','/signup','/forgot-password','/reset-password','/verify-email','/en','/en/login']){
      const r=await open(base+route);
      check(`${width}px ${route} : HTTP 200`,r.status()===200);
      const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth);
      check(`${width}px ${route} : pas de débordement`,!overflow);
      check(`${width}px ${route} : un titre principal`,await page.locator('h1').count()===1);
    }
  }
  await page.setViewportSize({width:390,height:844});await open(base+'/');
  await page.locator('#how-it-works').scrollIntoViewIfNeeded();await page.waitForTimeout(300);await page.evaluate(()=>window.scrollTo(0,0));
  await page.screenshot({path:path.join(out,'accueil-mobile.png'),fullPage:true});
  await page.screenshot({path:path.join(out,'accueil-mobile-premier-ecran.png')});
  const cta=page.locator('.sticky-cta a');check('Mobile : action fixe >= 44px',await cta.boundingBox().then(b=>b.height>=44));
  await page.locator('.faq summary').first().click();check('FAQ : ouverture native',await page.locator('.faq').first().getAttribute('open')!==null);
  await cta.click();check('CTA mobile : accès inscription',new URL(page.url()).pathname==='/signup');
  await page.getByLabel('Mot de passe',{exact:true}).fill('une-phrase-de-passe');
  await page.getByRole('button',{name:'Afficher le mot de passe'}).click();check('Mot de passe visible',await page.locator('input[name=password]').getAttribute('type')==='text');
  await page.getByRole('button',{name:'Masquer le mot de passe'}).click();
  await page.screenshot({path:path.join(out,'inscription-mobile.png'),fullPage:true});
  await page.route('**/v1/auth/signup',r=>r.fulfill({status:202,contentType:'application/json',body:'{}'}));
  await page.getByLabel('Adresse e-mail').fill('preview@example.test');await page.getByRole('button',{name:'Créer mon compte',exact:true}).click();
  check('Inscription : retour de confirmation',await page.getByRole('heading',{name:'Vérifiez votre boîte mail'}).isVisible());
  await open(base+'/login');
  await page.route('**/v1/auth/login',r=>r.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:'totp_required'})}));
  await page.getByLabel('Adresse e-mail').fill('preview@example.test');await page.getByLabel('Mot de passe',{exact:true}).fill('une-phrase-de-passe');
  await page.getByRole('button',{name:'Se connecter',exact:true}).click();
  await page.getByLabel('Code de vérification').waitFor();check('Connexion : étape TOTP affichée',await page.getByLabel('Code de vérification').isVisible());
  await page.setViewportSize({width:1440,height:1000});await open(base+'/login');await page.screenshot({path:path.join(out,'connexion-desktop.png'),fullPage:true});
  await open(base+'/app');check('Espace sans session : retour connexion',new URL(page.url()).pathname==='/login');
  await ctx.addCookies([{name:'preview_user',value:'1',url:base}]);
  await open(base+'/app');check('Espace client : session rendue côté serveur',await page.locator('body').innerText().then(t=>t.includes('camille@example.test')));
  if(new URL(page.url()).pathname==='/app')await page.screenshot({path:path.join(out,'espace-client-desktop.png'),fullPage:true});
  for(const width of [320,390,1440]){
    await page.setViewportSize({width,height:900});await open(base+'/app');check(`${width}px espace client : pas de débordement`,await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  }
  const bo='http://127.0.0.1:3101';
  await ctx.clearCookies();await page.setViewportSize({width:1440,height:1000});await open(bo+'/login');await page.screenshot({path:path.join(out,'equipe-connexion-desktop.png'),fullPage:true});
  await ctx.addCookies([{name:'preview_staff',value:'1',url:bo}]);
  for(const width of [320,390,768,1440]){
    await page.setViewportSize({width,height:900});await open(bo+'/users');check(`${width}px équipe : données serveur`,await page.locator('body').innerText().then(t=>t.includes('camille@example.test')));check(`${width}px équipe : pas de débordement`,await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
    if(width===390||width===1440)await page.screenshot({path:path.join(out,`equipe-utilisateurs-${width}.png`),fullPage:true});
  }
  await ctx.clearCookies();await page.setViewportSize({width:390,height:844});await open(bo+'/login');check('Connexion équipe mobile : pas de débordement',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.emulateMedia({colorScheme:'dark'});await open(base+'/');check('Thème sombre actif',await page.evaluate(()=>getComputedStyle(document.documentElement).colorScheme==='dark'));await page.screenshot({path:path.join(out,'accueil-sombre-mobile.png'),fullPage:true});
  await page.emulateMedia({colorScheme:'light',reducedMotion:'reduce'});await open(base+'/login');
  check('Mouvement réduit : scroll instantané',await page.evaluate(()=>getComputedStyle(document.documentElement).scrollBehavior==='auto'));
  await page.keyboard.press('Tab');check('Lien d’évitement : focus clavier',await page.locator('.bp-skip-link').evaluate(e=>e===document.activeElement));
  const broken=await page.locator('img').evaluateAll(images=>images.filter(i=>i.complete&&i.naturalWidth===0).map(i=>i.src));check('Images locales chargées',broken.length===0,JSON.stringify(broken));
  check('Aucune erreur JavaScript',errors.length===0,JSON.stringify(errors));
  await browser.close();
  fs.writeFileSync(path.join(out,'verification.json'),JSON.stringify({date:'2026-10-24',environment:'Chromium, builds Next.js de production, API de recette locale pour les sessions et réponses de formulaires',results},null,2)+'\n');
  console.log(`${results.filter(r=>r.ok).length}/${results.length} contrôles réussis`);
  process.exitCode=results.some(r=>!r.ok)?1:0;
})().catch(e=>{console.error(e);process.exitCode=1;});
