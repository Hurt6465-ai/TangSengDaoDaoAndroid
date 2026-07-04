// ==UserScript==
// @name         唐僧自动朗读 AI 回复
// @namespace    tsdd-learning
// @version      1.0.0
// @description  朗读 DeepSeek / 千问最后一条 AI 回复，支持手动朗读和自动朗读开关
// @match        https://chat.deepseek.com/*
// @match        https://chat.qwen.ai/*
// @match        https://www.qianwen.com/*
// @match        https://qianwen.com/*
// @run-at       document-end
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_addStyle
// ==/UserScript==
(function(){
  'use strict';
  GM_addStyle('#tsdd-read-btn{position:fixed;right:16px;bottom:206px;z-index:2147483647;border:0;border-radius:999px;background:#7c3aed;color:#fff;font-weight:700;padding:10px 14px;box-shadow:0 8px 24px rgba(124,58,237,.25)}');
  function lastText(){const nodes=[...document.querySelectorAll('article,.markdown,[class*=answer],[class*=message],[class*=content],main div')];const texts=nodes.map(n=>(n.innerText||'').trim()).filter(t=>t.length>12&&t.length<6000);return texts.length?texts[texts.length-1]:'';}
  function speak(t){if(!t)return; if(!window.speechSynthesis){alert('当前 WebView 不支持网页朗读');return;} speechSynthesis.cancel(); const u=new SpeechSynthesisUtterance(t); u.lang=/[\u1000-\u109F]/.test(t)?'my-MM':(/[a-zA-Z]/.test(t)&&!/[\u4e00-\u9fff]/.test(t)?'en-US':'zh-CN'); u.rate=0.92; speechSynthesis.speak(u);}
  if(!document.getElementById('tsdd-read-btn')){const b=document.createElement('button');b.id='tsdd-read-btn';b.textContent=GM_getValue('autoRead',false)?'自动朗读':'朗读';b.onclick=function(){if(GM_getValue('autoRead',false)){GM_setValue('autoRead',false);b.textContent='朗读';speechSynthesis&&speechSynthesis.cancel();}else{speak(lastText());}};b.oncontextmenu=function(e){e.preventDefault();const v=!GM_getValue('autoRead',false);GM_setValue('autoRead',v);b.textContent=v?'自动朗读':'朗读';};document.documentElement.appendChild(b);}
  let old=''; setInterval(()=>{if(!GM_getValue('autoRead',false))return;const t=lastText();if(t&&t!==old&&t.length>20){old=t;speak(t);}},2500);
})();
