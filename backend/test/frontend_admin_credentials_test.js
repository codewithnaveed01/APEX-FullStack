// Settings credential form: validated current password, no browser plaintext cache,
// token rotation and canonical username survive a subsequent admin refresh.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const start = script.indexOf('function adminSettings(){');
const end = script.indexOf('function resetFleet(){', start);
assert.ok(start >= 0 && end > start);
const storage = new Map(), calls = [], toasts = [];
const message = {textContent:'',style:{}}, button = {disabled:false,textContent:'Save login details'};
const input = () => ({value:'something'});
class FormData {
  constructor(form) {this.values=form.values}
  *[Symbol.iterator]() {yield* Object.entries(this.values)}
}
const context = vm.createContext({
  FormData, console,
  config: {driverRate:4500,overtime:500},
  account: {id:'admin',username:'admin',name:'APEX Admin'},
  users: [{id:'UADMIN',username:'admin',role:'admin',password:'old-browser-cache'}],
  __apexToken:'old-token',__apexRole:'admin',
  withoutCachedPassword: user => {
    if (!user) return user;
    const safe={...user};delete safe.password;return safe;
  },
  isAdminAuthenticated: () => true, v3Online: () => true,
  esc: s => String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('"','&quot;'),
  read: (key, d) => storage.has(key) ? storage.get(key) : d,
  write: (key, value) => storage.set(key, value),
  toast: text => toasts.push(text),
  document: {getElementById: id => id==='admin-credentials-result'?message:null},
  v3RefreshBootstrap: async () => true,
  v3Api: async (url, opts) => {
    calls.push({url, opts});
    assert.equal(url,'/api/admin/credentials');
    assert.equal(opts.method,'PUT');
    return {token:'fresh-token',role:'admin',user:{id:'UADMIN',username:'new-chief',name:'APEX Admin',email:'admin@apex.local'}};
  }
});
vm.runInContext(script.slice(start,end), context);
const form = values => ({values, elements:{currentPassword:input(),newPassword:input(),confirmPassword:input()},
  querySelector: selector => selector==='button[type="submit"]'?button:message});
const event = target => ({preventDefault(){},target});

(async () => {
  const markup=context.adminSettings();
  assert.match(markup, /Admin login details/);
  assert.match(markup, /name="username"[^>]*value="admin"/);
  assert.match(markup, /name="currentPassword"[^>]*type="password"[^>]*required/);
  assert.match(markup, /name="newPassword"[^>]*type="password"/);
  assert.doesNotMatch(markup, /value="admin1234"|value="old-browser-cache"/);

  await context.saveAdminCredentials(event(form({username:'new-chief',currentPassword:'admin1234',
    newPassword:'LongNewPassword123!',confirmPassword:'not-the-same'})));
  assert.equal(calls.length,0,'mismatched confirmation must not be submitted');
  assert.match(message.textContent,/do not match/);

  const updatedForm=form({username:'new-chief',currentPassword:'admin1234',
    newPassword:'LongNewPassword123!',confirmPassword:'LongNewPassword123!'});
  await context.saveAdminCredentials(event(updatedForm));
  assert.equal(calls.length,1);
  assert.deepEqual(JSON.parse(JSON.stringify(JSON.parse(calls[0].opts.body))),{
    username:'new-chief',currentPassword:'admin1234',newPassword:'LongNewPassword123!'
  });
  assert.equal(storage.get('apiToken'),'fresh-token');
  assert.equal(storage.get('adminLoginName'),'new-chief');
  assert.equal(context.account.username,'new-chief');
  assert.equal(context.users[0].username,'new-chief');
  assert.equal('password' in context.users[0],false);
  assert.equal(updatedForm.elements.currentPassword.value,'');
  assert.equal(updatedForm.elements.newPassword.value,'');
  assert.equal(updatedForm.elements.confirmPassword.value,'');
  assert.equal(button.disabled,false);
  assert.match(toasts.at(-1),/updated/);

  context.isAdminAuthenticated=()=>false;
  await context.saveAdminCredentials(event(form({username:'hacker',currentPassword:'a',newPassword:'',confirmPassword:''})));
  assert.equal(calls.length,1,'non-admin cannot submit the credentials form');
  console.log('PASS: admin settings login editor, session rotation, no plaintext in cached users');
})().catch(error=>{console.error(error);process.exitCode=1});
