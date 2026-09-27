// Recipient-scoped live updates must not turn into /api/sync writes.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '../seed.json'), 'utf8'));
const clone = x => JSON.parse(JSON.stringify(x));
const storage = new Map([
  ['v2_apiToken', JSON.stringify('admin-token')],
  ['v2_adminSession', JSON.stringify({user:'admin'})]
]);
const calls = [];
const live = {notifications: [], chats: []};
const makeClassList = () => {
  const names = new Set();
  return {toggle(name, add) {if (add) names.add(name); else names.delete(name)},
    contains: name => names.has(name)};
};
const elements = new Map();
for (const id of ['app', 'notif-btn', 'notif-dropdown', 'admin-inbox']) {
  elements.set(id, {innerHTML: '', classList: makeClassList(),
    setAttribute() {}, querySelector() {return null}});
}
const document = {
  hidden: false,
  body: {style: {}, appendChild: el => elements.set(el.id, el)},
  getElementById: id => elements.get(id) || null,
  querySelectorAll: () => [],
  createElement: () => ({id: '', style: {}, textContent: ''}),
  addEventListener() {}
};
const context = vm.createContext({
  console, document,
  localStorage: {
    getItem: k => storage.get(k) || null,
    setItem: (k, v) => storage.set(k, v),
    removeItem: k => storage.delete(k)
  },
  location: {pathname: '/admin.html', hash: '', protocol: 'https:'},
  window: {ADMIN_MODE: true, location: {protocol: 'https:'}, addEventListener() {}},
  setInterval() {return 1}, clearInterval() {},
  setTimeout() {return 1}, clearTimeout() {},
  fetch: async (url, opts = {}) => {
    calls.push({url, opts});
    if (url === '/api/auth/me') return {ok:true, status:200, json:async()=>({role:'admin'})};
    if (url === '/api/bootstrap') return {ok:true, status:200, json:async()=>({
      ...clone(seed), users: [], orders: [], applications: [], notifications: [], chats: []
    })};
    if (url === '/api/live') return {ok:true, status:200, json:async()=>clone(live)};
    if (url === '/api/notifications/read') {
      live.notifications.forEach(n => n.read = true);
      return {ok:true, status:200, json:async()=>({status:'ok'})};
    }
    if (url === '/api/notifications/mine' && opts.method === 'DELETE') {
      live.notifications = [];
      return {ok:true, status:200, json:async()=>({status:'ok'})};
    }
    throw new Error('Unexpected request ' + url);
  }
});
const tick = () => new Promise(resolve => setImmediate(resolve));
(async () => {
  vm.runInContext(script, context, {timeout: 2000});
  await tick(); // authenticated bootstrap plus first live poll
  assert.equal(vm.runInContext('__apexServerReady', context), true);
  assert.deepEqual(JSON.parse(storage.get('v2_apexPendingSyncKeys')), []);

  live.notifications.push({id:'N1',userId:'admin',title:'New booking',msg:'VR-123',
    link:'booking/VR-123',read:false,time:new Date().toISOString()});
  live.chats.push({userId:'C1',userName:'Customer',messages:[{text:'Hello',from:'user'}],
    unreadAdmin:1,unreadUser:0,lastTime:new Date().toISOString()});
  await vm.runInContext('apexPollLive()', context);
  assert.equal(elements.get('notif-btn').classList.contains('has-unread'), true);
  assert.match(elements.get('admin-inbox').innerHTML, /Hello/);
  assert.equal(vm.runInContext('apexChangedKeys().length', context), 0);
  assert.equal(calls.filter(c => c.url === '/api/sync').length, 0);

  await vm.runInContext('markNotificationsRead()', context);
  assert.equal(live.notifications[0].read, true);
  assert.equal(elements.get('notif-btn').classList.contains('has-unread'), false);
  assert.equal(vm.runInContext('apexChangedKeys().length', context), 0);

  live.notifications.push({id:'N2',userId:'admin',title:'New reply',msg:'A message',
    link:'chat/C1',read:false,time:new Date().toISOString()});
  await vm.runInContext('apexPollLive()', context);
  assert.equal(elements.get('notif-btn').classList.contains('has-unread'), true);
  assert.equal(vm.runInContext('apexChangedKeys().length', context), 0);
  assert.equal(calls.filter(c => c.url === '/api/sync').length, 0);
  console.log('PASS: live inbox/bell updates, read and new dots, without stale sync writes');
})().catch(error => {console.error(error);process.exitCode = 1});
