if(!self.define){let e,i={};const n=(n,t)=>(n=new URL(n+".js",t).href,i[n]||new Promise((i=>{if("document"in self){const e=document.createElement("script");e.src=n,e.onload=i,document.head.appendChild(e)}else e=n,importScripts(n),i()})).then((()=>{let e=i[n];if(!e)throw new Error(`Module ${n} didn’t register its module`);return e})));self.define=(t,r)=>{const s=e||("document"in self?document.currentScript.src:"")||location.href;if(i[s])return;let d={};const o=e=>n(e,s),l={module:{uri:s},exports:d,require:o};i[s]=Promise.all(t.map((e=>l[e]||o(e)))).then((e=>(r(...e),d)))}}define(["./workbox-2dda3721"],(function(e){"use strict";e.setCacheNameDetails({prefix:"pokedex"}),self.addEventListener("message",(e=>{e.data&&"SKIP_WAITING"===e.data.type&&self.skipWaiting()})),e.precacheAndRoute([{url:"index.html",revision:"d57abc64288739a3cc863c42f08854ca"},{url:"main.bundle.js",revision:"1bed015a4457b599ad30bdbd415a0114"},{url:"main.bundle.js.LICENSE.txt",revision:"d554918a3829d7ab1d94633bed517d88"}],{})}));
