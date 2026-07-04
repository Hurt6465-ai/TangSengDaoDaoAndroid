// ==UserScript==
// @name         唐僧学习提示词面板
// @namespace    tsdd-learning
// @version      1.0.0
// @description  在 DeepSeek / 千问网页里加入高频生活、口语、题目解析快捷 prompt 面板
// @match        https://chat.deepseek.com/*
// @match        https://chat.qwen.ai/*
// @match        https://www.qianwen.com/*
// @match        https://qianwen.com/*
// @run-at       document-end
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_addStyle
// ==/UserScript==
(function () {
  'use strict';
  const prompts = [
    ['日常打招呼', '你现在是我的口语陪练老师。请和我练习日常打招呼。每次只问我一句，我回答后请纠正并给出更自然的中文、英文、缅语表达。'],
    ['餐厅点餐', '你现在扮演餐厅服务员，我扮演顾客。请用简单自然的口语和我练习点餐，每次只说一句，等我回答后再继续，并纠正我的表达。'],
    ['求职面试', '你现在是面试官，我是求职者。请模拟真实求职面试，每次只问一个问题。我的回答后，请纠正并给出更好的中文、英文、缅语回答模板。'],
    ['医院看病', '你现在扮演医生或护士，我扮演病人。请用简单口语和我练习看病场景，每次只问一句，并纠正我的表达。'],
    ['机场过关', '你现在扮演机场入境官，我扮演旅客。请模拟机场过关问答，每次只问一个问题，回答后请纠正表达。'],
    ['解析题目', '请帮我解析这道题。先判断题型，再提取关键条件，一步一步解释思路，最后给出答案。题目如下：'],
    ['口语跟读', '请做我的口语跟读教练。每次给我一句高频生活短句，包含中文、英文、缅语、发音提示和使用场景，等我跟读后再继续。']
  ];
  GM_addStyle(`
    #tsdd-learn-btn{position:fixed;right:16px;bottom:92px;z-index:2147483647;border:0;border-radius:999px;background:#1877f2;color:#fff;font-weight:700;padding:10px 14px;box-shadow:0 8px 24px rgba(24,119,242,.25)}
    #tsdd-learn-panel{position:fixed;right:16px;bottom:146px;z-index:2147483647;width:min(340px,calc(100vw - 32px));max-height:58vh;overflow:auto;background:#fff;border:1px solid #e5e7eb;border-radius:18px;box-shadow:0 18px 50px rgba(15,23,42,.18);padding:10px;display:none}
    .tsdd-prompt-item{display:block;width:100%;text-align:left;border:0;background:#f8fafc;border-radius:14px;margin:6px 0;padding:11px 12px;color:#111827;font-size:14px;font-weight:600}
  `);
  function inputEl(){return document.querySelector('textarea, input[type=text], [contenteditable=true], [role=textbox]');}
  function fill(text){const el=inputEl(); if(!el){navigator.clipboard&&navigator.clipboard.writeText(text); alert('已复制 prompt，请粘贴到输入框'); return;} el.focus(); if('value' in el){el.value=text;}else{el.textContent=text;} el.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:text})); el.dispatchEvent(new Event('change',{bubbles:true}));}
  if (window.__TS_DD_START_PROMPT__) prompts.unshift(['当前场景', window.__TS_DD_START_PROMPT__]);
  if (!document.getElementById('tsdd-learn-btn')) {
    const btn=document.createElement('button');btn.id='tsdd-learn-btn';btn.textContent='学习';
    const panel=document.createElement('div');panel.id='tsdd-learn-panel';
    prompts.forEach(([title,text])=>{const b=document.createElement('button');b.className='tsdd-prompt-item';b.textContent=title;b.onclick=()=>{fill(text);panel.style.display='none';};panel.appendChild(b);});
    btn.onclick=()=>{panel.style.display=panel.style.display==='none'?'block':'none';};
    document.documentElement.appendChild(panel);document.documentElement.appendChild(btn);
  }
})();
