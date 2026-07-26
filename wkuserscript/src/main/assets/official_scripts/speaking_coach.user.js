// ==UserScript==
// @name         唐僧 AI 口语教练
// @namespace    tsdd-learning
// @version      1.1.0
// @description  围绕当前短句自动提交场景提示词，朗读 AI 回复并调用原生语音识别继续对话
// @match        https://chat.deepseek.com/*
// @match        https://chat.qwen.ai/*
// @match        https://www.qianwen.com/*
// @match        https://qianwen.com/*
// @run-at       document-end
// @tsdd-mode    speaking-coach
// ==/UserScript==
(function () {
  'use strict';
  if (window.__TS_DD_SPEAKING_COACH_RUNNING__) return;
  window.__TS_DD_SPEAKING_COACH_RUNNING__ = true;

  const state = {
    started: false,
    auto: true,
    listening: false,
    lastReply: '',
    candidateReply: '',
    stableTicks: 0,
    lastSpoken: '',
    startAttempts: 0,
    recognition: null,
    recognitionTimer: 0,
    lastSubmittedText: ''
  };

  function detectUiLanguage() {
    try {
      if (window.TsddSpeech && typeof window.TsddSpeech.uiLanguage === 'function') {
        const value = String(window.TsddSpeech.uiLanguage() || '').toLowerCase();
        if (value === 'my' || value === 'en' || value === 'zh') return value;
      }
    } catch (_) {}
    const value = String((navigator.languages && navigator.languages[0]) || navigator.language || '').toLowerCase();
    if (value.startsWith('my')) return 'my';
    if (value.startsWith('en')) return 'en';
    return 'zh';
  }

  const I18N = {
    zh: {
      title: 'AI 口语教练', preparing: '正在准备当前场景…', autoOn: '自动：开', autoOff: '自动：关',
      repeat: '再听', speak: '说话', start: '开始练习', reading: '教练正在朗读…',
      noRecognition: '当前设备没有可用的语音识别', listening: '正在听', answerChinese: '请用中文回答…',
      recognized: '识别到：{text}', listeningText: '正在听：{text}', continueSpeak: '请继续说…',
      sentWaiting: '已发送，等待教练回复…', recognitionFailed: '识别失败，点击“说话”重试',
      cannotStart: '无法启动语音识别', noPrompt: '没有收到当前句子的练习内容',
      loginFirst: '请先登录，登录后会自动开始', restart: '重新开始',
      sceneSubmitted: '已提交当前场景，等待教练回复…',
      paused: '已暂停自动接话，可手动点击“说话”', autoEnabled: '自动朗读和语音回答已开启',
      noReplay: '暂时没有可以重播的教练回复', coachReplying: '教练正在回复…'
    },
    en: {
      title: 'AI Speaking Coach', preparing: 'Preparing the current scene…', autoOn: 'Auto: On', autoOff: 'Auto: Off',
      repeat: 'Replay', speak: 'Speak', start: 'Start', reading: 'The coach is speaking…',
      noRecognition: 'No speech recognition service is available', listening: 'Listening', answerChinese: 'Answer in Chinese…',
      recognized: 'Recognized: {text}', listeningText: 'Listening: {text}', continueSpeak: 'Keep speaking…',
      sentWaiting: 'Sent. Waiting for the coach…', recognitionFailed: 'Recognition failed. Tap “Speak” to try again.',
      cannotStart: 'Unable to start speech recognition', noPrompt: 'No practice content was received for this phrase',
      loginFirst: 'Sign in first. Practice will start automatically afterward.', restart: 'Restart',
      sceneSubmitted: 'Scene submitted. Waiting for the coach…',
      paused: 'Automatic replies are paused. Tap “Speak” when ready.', autoEnabled: 'Automatic reading and voice replies are on',
      noReplay: 'There is no coach reply to replay yet', coachReplying: 'The coach is replying…'
    },
    my: {
      title: 'AI စကားပြောနည်းပြ', preparing: 'လက်ရှိအခြေအနေကို ပြင်ဆင်နေသည်…', autoOn: 'အလိုအလျောက်：ဖွင့်', autoOff: 'အလိုအလျောက်：ပိတ်',
      repeat: 'ပြန်နားထောင်', speak: 'ပြောရန်', start: 'စတင်လေ့ကျင့်', reading: 'နည်းပြက ဖတ်ပြနေသည်…',
      noRecognition: 'အသုံးပြုနိုင်သော အသံမှတ်မိစနစ် မရှိပါ', listening: 'နားထောင်နေသည်', answerChinese: 'တရုတ်လို ဖြေပါ…',
      recognized: 'မှတ်မိသည်：{text}', listeningText: 'နားထောင်နေသည်：{text}', continueSpeak: 'ဆက်ပြောပါ…',
      sentWaiting: 'ပို့ပြီးပါပြီ၊ နည်းပြ၏ ပြန်စာကို စောင့်နေသည်…', recognitionFailed: 'အသံမှတ်မိမှု မအောင်မြင်ပါ။ “ပြောရန်” ကို နှိပ်ပြီး ထပ်စမ်းပါ',
      cannotStart: 'အသံမှတ်မိမှုကို မစတင်နိုင်ပါ', noPrompt: 'လက်ရှိဝါကျအတွက် လေ့ကျင့်ရန်အကြောင်းအရာ မရရှိပါ',
      loginFirst: 'အရင် အကောင့်ဝင်ပါ။ ဝင်ပြီးနောက် အလိုအလျောက် စတင်မည်', restart: 'ပြန်စတင်',
      sceneSubmitted: 'လက်ရှိအခြေအနေကို ပို့ပြီးပါပြီ၊ နည်းပြ၏ ပြန်စာကို စောင့်နေသည်…',
      paused: 'အလိုအလျောက် ဆက်ပြောမှုကို ရပ်ထားသည်။ “ပြောရန်” ကို ကိုယ်တိုင်နှိပ်နိုင်သည်',
      autoEnabled: 'အလိုအလျောက် ဖတ်ပြခြင်းနှင့် အသံဖြေခြင်းကို ဖွင့်ထားသည်',
      noReplay: 'ပြန်ဖွင့်နိုင်သော နည်းပြပြန်စာ မရှိသေးပါ', coachReplying: 'နည်းပြက ပြန်ဖြေနေသည်…'
    }
  };

  const UI = I18N[detectUiLanguage()] || I18N.zh;
  function t(key, value) {
    const text = UI[key] || I18N.zh[key] || key;
    return value == null ? text : text.replace('{text}', value);
  }

  function addStyle(css) {
    const style = document.createElement('style');
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);
  }

  addStyle(`
    #tsdd-speaking-coach{position:fixed;left:12px;right:12px;bottom:12px;z-index:2147483647;background:rgba(255,255,255,.97);border:1px solid #e5e7eb;border-radius:20px;box-shadow:0 16px 48px rgba(15,23,42,.22);padding:10px 12px;font-family:system-ui,-apple-system,sans-serif;color:#111827;backdrop-filter:blur(16px)}
    #tsdd-speaking-coach .tsdd-coach-head{display:flex;align-items:center;gap:8px;margin-bottom:8px}
    #tsdd-speaking-coach .tsdd-coach-title{font-weight:800;font-size:14px;flex:1}
    #tsdd-speaking-coach .tsdd-coach-status{font-size:12px;color:#6b7280;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:55vw}
    #tsdd-speaking-coach .tsdd-coach-actions{display:flex;gap:8px}
    #tsdd-speaking-coach button{border:0;border-radius:14px;padding:9px 11px;font-size:13px;font-weight:750;background:#f1f3f7;color:#1f2937}
    #tsdd-speaking-coach button.primary{background:#625fe7;color:#fff;flex:1}
    #tsdd-speaking-coach button.listening{background:#e4576b;color:#fff}
  `);

  const panel = document.createElement('div');
  panel.id = 'tsdd-speaking-coach';
  panel.innerHTML = `
    <div class="tsdd-coach-head">
      <div class="tsdd-coach-title">${t('title')}</div>
      <div class="tsdd-coach-status" id="tsdd-coach-status">${t('preparing')}</div>
    </div>
    <div class="tsdd-coach-actions">
      <button id="tsdd-coach-auto">${t('autoOn')}</button>
      <button id="tsdd-coach-repeat">${t('repeat')}</button>
      <button id="tsdd-coach-mic">${t('speak')}</button>
      <button class="primary" id="tsdd-coach-start">${t('start')}</button>
    </div>`;
  document.documentElement.appendChild(panel);

  const statusEl = panel.querySelector('#tsdd-coach-status');
  const autoBtn = panel.querySelector('#tsdd-coach-auto');
  const repeatBtn = panel.querySelector('#tsdd-coach-repeat');
  const micBtn = panel.querySelector('#tsdd-coach-mic');
  const startBtn = panel.querySelector('#tsdd-coach-start');

  function status(text) {
    statusEl.textContent = text || '';
  }

  function visible(el) {
    if (!el || !el.isConnected) return false;
    const style = getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 2 && rect.height > 2;
  }

  function findInput() {
    const selectors = [
      'textarea',
      '[contenteditable="true"][role="textbox"]',
      '[contenteditable="true"]',
      '[role="textbox"]',
      'input[type="text"]'
    ];
    for (const selector of selectors) {
      const list = Array.from(document.querySelectorAll(selector)).filter(visible);
      if (list.length) return list[list.length - 1];
    }
    return null;
  }

  function setInput(text) {
    const input = findInput();
    if (!input) return false;
    input.focus();
    if ('value' in input) {
      const proto = Object.getPrototypeOf(input);
      const descriptor = proto && Object.getOwnPropertyDescriptor(proto, 'value');
      if (descriptor && descriptor.set) descriptor.set.call(input, text);
      else input.value = text;
    } else {
      input.textContent = text;
    }
    input.dispatchEvent(new InputEvent('input', {bubbles: true, inputType: 'insertText', data: text}));
    input.dispatchEvent(new Event('change', {bubbles: true}));
    return true;
  }

  function clickSend() {
    const input = findInput();
    if (!input) return false;
    const scopes = [];
    let node = input;
    for (let i = 0; i < 5 && node; i++, node = node.parentElement) scopes.push(node);
    const candidates = [];
    scopes.forEach(scope => {
      scope.querySelectorAll('button,[role="button"]').forEach(button => {
        if (!visible(button) || button.disabled) return;
        const label = ((button.getAttribute('aria-label') || '') + ' ' +
          (button.getAttribute('title') || '') + ' ' + (button.innerText || '')).trim().toLowerCase();
        if (/发送|提交|send|submit|arrow-up|上箭头/.test(label)) candidates.push(button);
      });
    });
    if (candidates.length) {
      candidates[candidates.length - 1].click();
      return true;
    }
    const form = input.closest && input.closest('form');
    if (form) {
      const submitButton = Array.from(form.querySelectorAll('button[type="submit"],input[type="submit"]')).filter(visible).pop();
      if (submitButton && !submitButton.disabled) { submitButton.click(); return true; }
      if (typeof form.requestSubmit === 'function') { form.requestSubmit(); return true; }
    }
    input.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
    input.dispatchEvent(new KeyboardEvent('keyup', {key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true}));
    return true;
  }

  function submit(text) {
    const normalized = cleanText(text);
    if (!setInput(text)) return false;
    state.lastSubmittedText = normalized;
    setTimeout(clickSend, 180);
    return true;
  }

  function cleanText(text) {
    return String(text || '')
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/[#*_`>|]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim()
      .slice(0, 800);
  }

  function assistantCandidates() {
    const selectors = [
      '[data-message-author-role="assistant"]',
      '.ds-markdown',
      '.markdown-body',
      '[class*="assistant"][class*="message"]',
      '[class*="answer"] [class*="markdown"]',
      '[class*="message-content"]',
      'article [class*="markdown"]',
      'main [class*="markdown"]'
    ];
    const seen = new Set();
    const values = [];
    selectors.forEach(selector => {
      document.querySelectorAll(selector).forEach(el => {
        if (!visible(el) || seen.has(el)) return;
        seen.add(el);
        const text = cleanText(el.innerText || el.textContent || '');
        if (text.length >= 2 && text.length <= 800) values.push(text);
      });
    });
    return values;
  }

  function currentReply() {
    const values = assistantCandidates();
    return values.length ? values[values.length - 1] : '';
  }

  function stopSpeech() {
    try { if (window.TsddSpeech && window.TsddSpeech.stop) window.TsddSpeech.stop(); } catch (_) {}
    try { if (window.speechSynthesis) speechSynthesis.cancel(); } catch (_) {}
  }

  function speak(text) {
    text = cleanText(text);
    if (!text) return;
    state.lastSpoken = text;
    status(t('reading'));
    try {
      if (window.TsddSpeech && window.TsddSpeech.isAvailable && window.TsddSpeech.isAvailable()) {
        window.TsddSpeech.speak(text);
      } else if (window.speechSynthesis) {
        speechSynthesis.cancel();
        const utterance = new SpeechSynthesisUtterance(text);
        utterance.lang = 'zh-CN';
        utterance.rate = 0.92;
        speechSynthesis.speak(utterance);
      }
    } catch (_) {}
    if (state.auto) {
      clearTimeout(state.recognitionTimer);
      const delay = Math.min(12000, Math.max(2200, 1000 + text.length * 170));
      state.recognitionTimer = setTimeout(startRecognition, delay);
    }
  }

  function recognitionCtor() {
    return window.SpeechRecognition || window.webkitSpeechRecognition || window.TsddSpeechRecognition;
  }

  function startRecognition() {
    if (state.listening) return;
    const Recognition = recognitionCtor();
    if (!Recognition) {
      status(t('noRecognition'));
      return;
    }
    stopSpeech();
    try {
      const recognition = new Recognition();
      state.recognition = recognition;
      recognition.lang = 'zh-CN';
      recognition.interimResults = true;
      recognition.continuous = false;
      recognition.maxAlternatives = 3;
      recognition.onstart = function () {
        state.listening = true;
        micBtn.classList.add('listening');
        micBtn.textContent = t('listening');
        status(t('answerChinese'));
      };
      recognition.onresult = function (event) {
        let finalText = '';
        let partial = '';
        for (let i = event.resultIndex || 0; i < event.results.length; i++) {
          const value = event.results[i][0] && event.results[i][0].transcript || '';
          if (event.results[i].isFinal) finalText += value;
          else partial += value;
        }
        status(finalText ? t('recognized', finalText) : (partial ? t('listeningText', partial) : t('continueSpeak')));
        if (finalText.trim()) {
          setTimeout(function () {
            if (submit(finalText.trim())) status(t('sentWaiting'));
          }, 180);
        }
      };
      recognition.onerror = function (event) {
        const error = event && event.error || 'error';
        if (error !== 'aborted') status(t('recognitionFailed'));
      };
      recognition.onend = function () {
        state.listening = false;
        micBtn.classList.remove('listening');
        micBtn.textContent = t('speak');
        state.recognition = null;
      };
      recognition.start();
    } catch (_) {
      state.listening = false;
      status(t('cannotStart'));
    }
  }

  function stopRecognition() {
    try { if (state.recognition) state.recognition.abort(); } catch (_) {}
    state.listening = false;
    micBtn.classList.remove('listening');
    micBtn.textContent = t('speak');
  }

  function startCoach() {
    const prompt = String(window.__TS_DD_START_PROMPT__ || '').trim();
    if (!prompt) {
      status(t('noPrompt'));
      return false;
    }
    state.startAttempts++;
    state.lastReply = currentReply();
    if (!submit(prompt)) {
      status(t('loginFirst'));
      return false;
    }
    state.started = true;
    startBtn.textContent = t('restart');
    status(t('sceneSubmitted'));
    return true;
  }

  autoBtn.onclick = function () {
    state.auto = !state.auto;
    autoBtn.textContent = state.auto ? t('autoOn') : t('autoOff');
    if (!state.auto) {
      clearTimeout(state.recognitionTimer);
      stopRecognition();
      status(t('paused'));
    } else {
      status(t('autoEnabled'));
    }
  };
  repeatBtn.onclick = function () {
    if (state.lastSpoken) speak(state.lastSpoken);
    else status(t('noReplay'));
  };
  micBtn.onclick = function () {
    if (state.listening) stopRecognition(); else startRecognition();
  };
  startBtn.onclick = function () {
    stopRecognition();
    clearTimeout(state.recognitionTimer);
    startCoach();
  };

  setInterval(function () {
    if (!state.started) return;
    const reply = currentReply();
    if (!reply || reply === state.lastReply || reply === state.lastSpoken
        || reply === state.lastSubmittedText) return;
    if (reply === state.candidateReply) state.stableTicks++;
    else {
      state.candidateReply = reply;
      state.stableTicks = 0;
    }
    if (state.stableTicks < 2) {
      status(t('coachReplying'));
      return;
    }
    state.lastReply = reply;
    state.candidateReply = '';
    state.stableTicks = 0;
    speak(reply);
  }, 1100);

  let autoStartTimer = setInterval(function () {
    if (state.started || state.startAttempts > 40) {
      clearInterval(autoStartTimer);
      return;
    }
    if (findInput() && window.__TS_DD_START_PROMPT__) startCoach();
  }, 900);
})();
