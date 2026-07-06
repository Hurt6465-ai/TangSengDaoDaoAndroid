package com.chat.userscript;

import android.text.TextUtils;
import android.webkit.WebView;

/**
 * Web Speech API polyfill backed by AiScriptWebActivity.TsddNativeSpeech.
 *
 * Purpose:
 * - keep UserScriptController focused on WebView/userscript lifecycle;
 * - make speech injection reusable and easy to change;
 * - force AI pages to use the app native speech bridge instead of unstable WebView/browser speech;
 * - never default to zh-CN. If JS does not set recognition.lang, pass an empty language to Android
 *   so Android does not receive a hard-coded Chinese language tag.
 */
public final class TsddNativeSpeechPolyfill {
    private TsddNativeSpeechPolyfill() {
    }

    public static void inject(WebView webView) {
        if (webView == null) return;
        try {
            webView.evaluateJavascript(buildScript(), null);
        } catch (Throwable ignored) {
        }
    }

    private static String buildScript() {
        String js = "(function(){" +
                "if(window.__TS_DD_NATIVE_SPEECH_POLYFILL_INSTALLED__)return;" +
                "if(!window.TsddNativeSpeech)return;" +
                "window.__TS_DD_NATIVE_SPEECH_POLYFILL_INSTALLED__=true;" +
                "var oldSR=window.SpeechRecognition||null;" +
                "var oldWebkitSR=window.webkitSpeechRecognition||null;" +
                "window.__TS_DD_ORIGINAL_SPEECH_RECOGNITION__=oldSR;" +
                "window.__TS_DD_ORIGINAL_WEBKIT_SPEECH_RECOGNITION__=oldWebkitSR;" +
                "var active=null;" +
                "function safeString(v){return v==null?'':String(v);}" +
                "function fire(target,fn,ev){try{if(typeof fn==='function')fn.call(target,ev||{});}catch(e){try{console.error(e);}catch(_){}}}" +
                "function dispatch(name,detail){try{window.dispatchEvent(new CustomEvent(name,{detail:detail||{}}));}catch(e){try{var ev=document.createEvent('CustomEvent');ev.initCustomEvent(name,false,false,detail||{});window.dispatchEvent(ev);}catch(_){}}}" +
                "function resultEvent(text,finalResult){" +
                "text=safeString(text);" +
                "var alt={transcript:text,confidence:finalResult?1:0.6};" +
                "var one=[alt];one.isFinal=!!finalResult;one.length=1;one.item=function(i){return this[i];};" +
                "var results=[one];results.length=1;results.item=function(i){return this[i];};" +
                "return {type:'result',resultIndex:0,results:results};" +
                "}" +
                "function NativeSpeechRecognition(){" +
                "this.lang='';" +
                "this.interimResults=false;" +
                "this.continuous=false;" +
                "this.maxAlternatives=1;" +
                "this.onstart=null;this.onresult=null;this.onerror=null;this.onend=null;this.onnomatch=null;" +
                "this.onaudiostart=null;this.onaudioend=null;this.onsoundstart=null;this.onsoundend=null;this.onspeechstart=null;this.onspeechend=null;" +
                "this._active=false;" +
                "}" +
                "NativeSpeechRecognition.prototype.start=function(){" +
                "if(active&&active!==this){try{active.abort();}catch(e){}}" +
                "active=this;this._active=true;" +
                "var lang=safeString(this.lang).trim();" +
                "try{window.TsddNativeSpeech.startSpeech(lang);}" +
                "catch(e){" +
                "var msg=safeString(e&&e.message||e);" +
                "fire(this,this.onerror,{type:'error',error:'client',message:msg});" +
                "dispatch('TsddNativeSpeechError',{error:'client',message:msg});" +
                "this._active=false;if(active===this)active=null;fire(this,this.onend,{type:'end'});" +
                "}" +
                "};" +
                "NativeSpeechRecognition.prototype.stop=function(){try{window.TsddNativeSpeech.stopSpeech();}catch(e){}};" +
                "NativeSpeechRecognition.prototype.abort=function(){try{window.TsddNativeSpeech.cancelSpeech();}catch(e){}};" +
                "window.__TS_DD_NATIVE_SPEECH_DISPATCH__=function(type,text,error){" +
                "var r=active;if(!r)return;type=safeString(type);text=safeString(text);error=safeString(error);" +
                "if(type==='start'){r._active=true;fire(r,r.onstart,{type:'start'});fire(r,r.onaudiostart,{type:'audiostart'});dispatch('TsddNativeSpeechStart',{});return;}" +
                "if(type==='partial'){dispatch('TsddNativeSpeechPartial',{text:text});if(r.interimResults)fire(r,r.onresult,resultEvent(text,false));return;}" +
                "if(type==='final'){dispatch('TsddNativeSpeechResult',{text:text});if(text){fire(r,r.onresult,resultEvent(text,true));}else{fire(r,r.onnomatch,{type:'nomatch'});}return;}" +
                "if(type==='error'){dispatch('TsddNativeSpeechError',{error:error||'error',message:text||error||''});fire(r,r.onerror,{type:'error',error:error||'error',message:text||error||''});return;}" +
                "if(type==='end'){r._active=false;fire(r,r.onaudioend,{type:'audioend'});fire(r,r.onend,{type:'end'});dispatch('TsddNativeSpeechEnd',{});if(active===r)active=null;return;}" +
                "};" +
                "NativeSpeechRecognition.prototype.constructor=NativeSpeechRecognition;" +
                "NativeSpeechRecognition.isTsddNativeSpeech=true;" +
                "window.TsddNativeSpeechRecognition=NativeSpeechRecognition;" +
                "window.TsddSpeechRecognition=NativeSpeechRecognition;" +
                "window.SpeechRecognition=NativeSpeechRecognition;" +
                "window.webkitSpeechRecognition=NativeSpeechRecognition;" +
                "})();";
        return TextUtils.isEmpty(js) ? "" : js;
    }
}
