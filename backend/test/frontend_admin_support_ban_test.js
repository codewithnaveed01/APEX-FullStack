// Admin chat stays available on the customer site; CNIC bans survive a server refresh.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '../seed.json'), 'utf8'));
const copy = value => JSON.parse(JSON.stringify(value));
const tick = () => new Promise(resolve => setImmediate(resolve));
const cnic = '3520212345678', other = '1112223334445';

function node() {
  const classes = new Set(), attrs = {};
  return {
    innerHTML: '', value: '', attrs, classList: {
      add(name) {classes.add(name)},
      contains(name) {return classes.has(name)},
      toggle(name, on) {
        const next = on === undefined ? !classes.has(name) : on;
        if (next) classes.add(name); else classes.delete(name);
      }
    },
    setAttribute(name, value) {attrs[name] = value},
    querySelector() {return null},
    scrollIntoView() {}, focus() {this.focused = true}
  };
}

function loadPage(adminPage, initialBans = [], role = 'admin') {
  const storage = new Map([
    ['v2_apiToken', JSON.stringify('valid-' + role + '-token')],
    ...(role === 'admin' ? [['v2_adminSession', JSON.stringify({user: 'admin'})]] : []),
    ['v2_session', JSON.stringify(role === 'admin'
      ? {id: 'admin', name: 'Administrator'} : {id: 'CUSTOMER', name: 'Customer', username: 'customer'})],
    ['v2_apexPendingSyncKeys', JSON.stringify(['bannedCNICs'])]
  ]);
  const bans = [...initialBans], threads = [], calls = [];
  const elements = new Map(['app', 'toast', 'admin-inbox', 'admin-site-inbox',
    'admin-site-chat-panel', 'admin-message-btn', 'admin-message-fab',
    'admin-inbox-shell'].map(id => [id, node()]));
  const app = elements.get('app');
  const document = {
    hidden: false,
    body: {style: {}, classList: node().classList, appendChild: el => elements.set(el.id, el)},
    createElement: () => node(),
    getElementById(id) {
      if (adminPage && id === 'admin-site-inbox') return null;
      if (!adminPage && id === 'admin-inbox') return null;
      return elements.get(id) || null;
    },
    querySelector(selector) {
      if (selector === '.admin-inbox-shell') return adminPage ? elements.get('admin-inbox-shell') : null;
      if (selector === '.admin-message-fab') return !adminPage ? elements.get('admin-message-fab') : null;
      return null;
    },
    querySelectorAll(selector) {
      if (selector === '.admin-message-btn,.admin-message-fab')
        return [elements.get(adminPage ? 'admin-message-btn' : 'admin-message-fab')];
      return [];
    },
    addEventListener() {}
  };
  const server = {...copy(seed), users: [], notifications: [], chats: [],
    bannedCNICs: bans.map(value => ({cnic: value, created: '2026-09-25'}))};
  const response = (status, data) => ({ok: status < 400, status, json: async () => copy(data)});
  const context = vm.createContext({
    document, console,
    localStorage: {
      getItem: key => storage.get(key) || null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key)
    },
    location: {pathname: adminPage ? '/admin.html' : '/', hash: '', protocol: 'https:'},
    window: {ADMIN_MODE: adminPage, location: {protocol: 'https:'}, addEventListener() {}},
    setTimeout: () => 1, clearTimeout() {}, setInterval: () => 1, clearInterval() {},
    fetch: async (url, options = {}) => {
      calls.push({url, options});
      if (url === '/api/auth/me') return response(200, {role, user: role === 'admin'
        ? {id: 'admin', name: 'Administrator'} : {id: 'CUSTOMER', name: 'Customer', username: 'customer'}});
      if (url === '/api/bootstrap') {
        server.bannedCNICs = bans.map(value => ({cnic: value, created: '2026-09-25'}));
        server.chats = copy(threads);
        return response(200, server);
      }
      if (url === '/api/live') return response(200, {notifications: [], chats: threads});
      if (url.startsWith('/api/availability/fleet')) return response(200, {rentedIds: []});
      if (url === '/api/banned-cnic' && options.method === 'POST') {
        const value = JSON.parse(options.body).cnic;
        if (!bans.includes(value)) bans.push(value);
        return response(201, {status: 'ok'});
      }
      if (url.startsWith('/api/banned-cnic/') && options.method === 'DELETE') {
        const index = bans.indexOf(decodeURIComponent(url.slice('/api/banned-cnic/'.length)));
        if (index >= 0) bans.splice(index, 1);
        return response(200, {status: 'ok'});
      }
      if (url === '/api/banned-cnic') return response(200, bans.map(value => ({cnic: value})));
      if (url === '/api/chats/send') {
        const {userId, text} = JSON.parse(options.body);
        const thread = threads.find(c => c.userId === userId);
        thread.messages.push({sender: 'admin', text});
        thread.unreadUser++;
        return response(201, thread);
      }
      if (url === '/api/chats/read') {
        const {userId} = JSON.parse(options.body);
        const thread = threads.find(c => c.userId === userId);
        thread.unreadAdmin = 0;
        return response(200, thread);
      }
      throw new Error('Unexpected request ' + url);
    }
  });
  vm.runInContext(script, context, {timeout: 2000});
  return {context, app, elements, bans, threads, calls, storage};
}

(async () => {
  const admin = loadPage(true, [cnic]);
  await tick();
  assert.match(admin.app.innerHTML, /id="admin-message-btn"[^>]*onclick="openAdminMessages\(\)"/);
  assert.match(admin.app.innerHTML, /<aside class="admin-inbox-shell/);
  assert.deepEqual(JSON.parse(vm.runInContext('JSON.stringify(bannedCNICs)', admin.context)), [cnic]);
  assert.deepEqual(JSON.parse(admin.storage.get('v2_apexPendingSyncKeys')), [],
    'old full-list sync must not overwrite the server ban');

  admin.threads.push({userId: 'C1', userName: 'Customer', unreadAdmin: 1, unreadUser: 0,
    messages: [{sender: 'user', text: 'Hello admin'}], lastTime: '2026-09-25T10:00:00Z'});
  await vm.runInContext('apexPollLive()', admin.context);
  assert.match(admin.elements.get('admin-inbox').innerHTML, /Hello admin/);
  assert.equal(admin.elements.get('admin-message-btn').classList.contains('has-unread'), true);
  vm.runInContext('openAdminMessages()', admin.context);
  assert.equal(admin.elements.get('admin-inbox-shell').classList.contains('expanded'), true);

  await vm.runInContext(`banCNIC('${other}')`, admin.context);
  assert.deepEqual(admin.bans, [cnic, other]);
  assert.deepEqual(JSON.parse(vm.runInContext('JSON.stringify(bannedCNICs)', admin.context)), [cnic, other]);
  assert.ok(admin.calls.some(call => call.url === '/api/banned-cnic' && call.options.method === 'POST'));
  assert.equal(admin.calls.some(call => call.url === '/api/sync'), false);
  await vm.runInContext('v3RefreshBootstrap()', admin.context);
  assert.deepEqual(JSON.parse(vm.runInContext('JSON.stringify(bannedCNICs)', admin.context)), [cnic, other],
    'both bans must survive an admin refresh');
  await vm.runInContext(`unbanCNIC('${other}')`, admin.context);
  await vm.runInContext('v3RefreshBootstrap()', admin.context);
  assert.deepEqual(admin.bans, [cnic]);
  assert.deepEqual(JSON.parse(vm.runInContext('JSON.stringify(bannedCNICs)', admin.context)), [cnic]);
  assert.ok(admin.calls.some(call => call.url === '/api/banned-cnic/' + other && call.options.method === 'DELETE'));

  const website = loadPage(false, [cnic]);
  await tick();
  assert.match(website.app.innerHTML, /admin-site-chat-panel/);
  assert.match(website.app.innerHTML, /admin-message-fab[^>]*toggleAdminSiteInbox\(\)/);
  assert.doesNotMatch(website.app.innerHTML, /onsubmit="submitUserChat\(event\)"/,
    'a verified admin must not get a customer message composer');
  assert.match(website.app.innerHTML, /<svg viewBox="0 0 24 24"[^>]*stroke="currentColor"/);
  assert.equal(vm.runInContext(`bannedCNICs.includes('${cnic}')`, website.context), true,
    'the website must render normalized server bans');
  website.threads.push({userId: 'C1', userName: 'Customer', unreadAdmin: 1, unreadUser: 0,
    messages: [{sender: 'user', text: 'Message from customer'}], lastTime: '2026-09-25T10:00:00Z'});
  await vm.runInContext('apexPollLive()', website.context);
  assert.match(website.elements.get('admin-site-inbox').innerHTML, /Message from customer/);
  assert.equal(website.elements.get('admin-message-fab').classList.contains('has-unread'), true);
  vm.runInContext('toggleAdminSiteInbox()', website.context);
  assert.equal(website.elements.get('admin-site-chat-panel').classList.contains('open'), true);
  await vm.runInContext(`selectChat('C1')`, website.context);
  assert.equal(website.threads[0].unreadAdmin, 0);
  await vm.runInContext(`sendAdminChat('C1', 'Thanks')`, website.context);
  assert.equal(website.threads[0].messages.at(-1).text, 'Thanks');
  assert.equal(website.calls.some(call => call.url === '/api/sync'), false);

  const customer = loadPage(false, [cnic], 'customer');
  await tick();
  assert.equal(vm.runInContext(`bannedCNICs.includes('${cnic}')`, customer.context), true);
  await vm.runInContext(`banCNIC('${other}')`, customer.context);
  assert.equal(customer.calls.some(call => call.url === '/api/banned-cnic' && call.options.method === 'POST'), false);
  assert.match(customer.app.innerHTML, /class="chat-fab/);
  assert.doesNotMatch(customer.app.innerHTML, /admin-site-chat-panel/);
  console.log('PASS: admin messages on dashboard and website; CNIC ban/unban survives refresh without stale sync');
})().catch(error => {console.error(error); process.exitCode = 1});
