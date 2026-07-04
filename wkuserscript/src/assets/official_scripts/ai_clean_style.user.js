// ==UserScript==
// @name         唐僧学习样式美化
// @namespace    tsdd-learning
// @version      1.0.0
// @description  放大字体、优化行距，减少学习时的视觉干扰
// @match        https://chat.deepseek.com/*
// @match        https://chat.qwen.ai/*
// @match        https://www.qianwen.com/*
// @match        https://qianwen.com/*
// @run-at       document-end
// @grant        GM_addStyle
// ==/UserScript==
(function(){
  'use strict';
  GM_addStyle(`
    main, article, .markdown, [class*=message], [class*=content] { font-size: 16px !important; line-height: 1.72 !important; }
    textarea, [contenteditable=true], [role=textbox] { font-size: 16px !important; line-height: 1.55 !important; }
    pre, code { font-size: 14px !important; }
  `);
})();
