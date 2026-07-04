// ==UserScript==
// @name         唐僧一键复制最后回答
// @namespace    tsdd-learning
// @version      1.0.0
// @description  复制 DeepSeek / 千问最后一条 AI 回复
// @match        https://chat.deepseek.com/*
// @match        https://chat.qwen.ai/*
// @match        https://www.qianwen.com/*
// @match        https://qianwen.com/*
// @run-at       document-end
// @grant        GM_addStyle
// @grant        GM_setClipboard
// @grant        GM_notification
// ==/UserScript==
(function(){
  'use strict';
  GM_addStyle('#tsdd-copy-btn{position:fixed;right:16px;bottom:264px;z-index:2147483647;border:0;border-radius:999px;background:#0f766e;color:#fff;font-weight:700;padding:10px 14px;box-shadow:0 8px 24px rgba(15,118,110,.25)}');
  function lastText(){const nodes=[...document.querySelectorAll('article,.markdown,[class*=answer],[class*=message],[class*=content],main div')];const texts=nodes.map(n=>(n.innerText||'').trim()).filter(t=>t.length>12&&t.length<8000);return texts.length?texts[texts.length-1]:'';}
  if(!document.getElementById('tsdd-copy-btn')){const b=document.createElement('button');b.id='tsdd-copy-btn';b.textContent='复制回答';b.onclick=function(){const t=lastText();if(!t){alert('没找到回答');return;}if(typeof GM_setClipboard==='function'){GM_setClipboard(t);GM_notification&&GM_notification('已复制最后回答');}else{navigator.clipboard&&navigator.clipboard.writeText(t);}};document.documentElement.appendChild(b);}
})();
