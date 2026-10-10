/* Compatibility for older Android WebViews. All assets and messages remain local. */
window.FlowEditorErrors=[];
window.addEventListener('error',function(e){window.FlowEditorErrors.push(e.message);var status=document.getElementById('status');if(status)status.textContent='Editor error: '+e.message;});
window.addEventListener('unhandledrejection',function(e){window.FlowEditorErrors.push(String(e.reason));});
if(!window.globalThis)window.globalThis=window;
if(!Object.hasOwn)Object.hasOwn=function(object,key){return Object.prototype.hasOwnProperty.call(object,key);};
if(!Array.prototype.at)Object.defineProperty(Array.prototype,'at',{value:function(index){index=Math.trunc(Number(index)||0);return this[index<0?this.length+index:index];},configurable:true,writable:true});
if(!String.prototype.replaceAll)Object.defineProperty(String.prototype,'replaceAll',{value:function(search,replacement){if(search instanceof RegExp){if(!search.global)throw new TypeError('Global regexp required');return this.replace(search,replacement);}return this.split(String(search)).join(String(replacement));},configurable:true,writable:true});
if(!window.structuredClone)window.structuredClone=function(value){return JSON.parse(JSON.stringify(value));};
if(!window.WeakRef)window.WeakRef=function(value){this.deref=function(){return value;};};
if(!window.FinalizationRegistry)window.FinalizationRegistry=function(){this.register=function(){};this.unregister=function(){return true;};};
if(!Promise.allSettled)Promise.allSettled=function(values){return Promise.all(Array.from(values).map(function(value){return Promise.resolve(value).then(function(v){return {status:'fulfilled',value:v};},function(reason){return {status:'rejected',reason:reason};});}));};
window.FlowEditorMessages=[];
if(!window.FlowBridge)window.FlowBridge={postMessage:function(data){
 if(typeof data!=='string'||data.length>2000000)return;
 var queue=window.FlowEditorMessages;
 if(data.indexOf('"action":"dirty"')!==-1&&queue.length&&queue[queue.length-1].indexOf('"action":"dirty"')!==-1)queue[queue.length-1]=data;
 else if(queue.length<20)queue.push(data);
}};
