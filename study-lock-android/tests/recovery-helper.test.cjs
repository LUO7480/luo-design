const fs=require('node:fs');
const vm=require('node:vm');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const html=fs.readFileSync('recovery-helper.html','utf8');
const source=html.match(/<script id="recovery-crypto">([\s\S]*?)<\/script>/)[1];
const context={module:{exports:{}}};
vm.runInNewContext(source,context);
const helper=context.module.exports;
for(const length of [0,1,55,56,63,64,65,1000]) {
  const data=crypto.randomBytes(length);
  assert.equal(Buffer.from(helper.sha256(data)).toString('hex'),crypto.createHash('sha256').update(data).digest('hex'));
}
for(const keyLength of [1,20,64,65,131]) {
  const key=crypto.randomBytes(keyLength),message=crypto.randomBytes(233);
  assert.equal(Buffer.from(helper.hmacSha256(key,message)).toString('hex'),crypto.createHmac('sha256',key).update(message).digest('hex'));
}
for(let i=0;i<200;i++) {
  const digits=length=>Array.from({length},()=>crypto.randomInt(10)).join('');
  const master=digits(64),challenge=digits(12);
  const digest=crypto.createHmac('sha256',master).update('StudyLockRecovery:v2:'+challenge).digest();
  const expected=String((digest.readUInt32BE(digest[31]&15)&0x7fffffff)%100000000).padStart(8,'0');
  assert.equal(helper.response(master,challenge),expected);
  assert.equal(helper.response(master.match(/.{4}/g).join(' '),challenge.match(/.{4}/g).join('-')),expected);
}
assert.throws(()=>helper.response('0'.repeat(63),'0'.repeat(12)));
assert.throws(()=>helper.response('0'.repeat(64),'0'.repeat(11)));
assert.ok(html.includes("connect-src 'none'"));
assert.ok(!html.includes('localStorage'));
const elements={};
const document={getElementById(id){return elements[id]??=( {value:'',textContent:'',listeners:{},addEventListener(name,callback){this.listeners[name]=callback;}} );}};
context.document=document;
const uiSource=html.match(/<script>\s*const master=([\s\S]*?)<\/script>/)[0].replace(/^<script>/,'').replace(/<\/script>$/,'');
vm.runInNewContext(uiSource,context);
elements.master.value='0123456789'.repeat(6)+'0123';
elements.challenge.value='1234 5678 9012';
elements.generate.listeners.click();
assert.equal(elements.answer.textContent,'11359506');
elements.clear.listeners.click();
assert.equal(elements.master.value,'');
assert.equal(elements.answer.textContent,'');
console.log('PASS: offline helper SHA-256, HMAC, 200 independent responses, grouping, and input validation');
