// Regression tests for admin refresh/sync without a browser or a live server.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const script = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '../seed.json'), 'utf8'));
const copy = value => JSON.parse(JSON.stringify(value));

function loadPage(saved = {}, options = {}) {
  const storage = new Map(Object.entries({
    adminSession: {user:'admin'}, apiToken: 'valid-admin-token', ...saved
  }).map(([k,v]) => ['v2_' + k, JSON.stringify(v)]));
  const elements = new Map([['app', {innerHTML: ''}]]);
  const calls = [], timers = [], errors = [], waitingBootstraps = [];
  const server = {
    fleet: copy(seed.fleet), drivers: copy(seed.drivers),
    users: [{id:'UADMIN', username:'admin', role:'admin'}],
    orders: [], applications: [], notifications: [], chats: [], bannedCNICs: [],
    config: copy(seed.config), adminWallet: {balance: 100}, ownerWallets: {}
  };
  const document = {
    body: {style: {}, appendChild: el => elements.set(el.id, el)},
    createElement: () => ({id: '', style: {}, textContent: ''}),
    getElementById: key => elements.get(key) || null,
    addEventListener() {}
  };
  const context = vm.createContext({
    console: {...console, error: (...args) => errors.push(args)}, document,
    localStorage: {
      getItem: k => storage.get(k) || null,
      setItem: (k,v) => storage.set(k,v),
      removeItem: k => storage.delete(k)
    },
    location: {pathname: '/admin.html', hash: '', protocol: 'https:'},
    window: {ADMIN_MODE: true, location: {protocol: 'https:'}, addEventListener() {}},
    setTimeout: fn => {timers.push(fn);return timers.length},
    clearTimeout: id => {timers[id - 1] = null},
    fetch: async (url, opts = {}) => {
      calls.push({url, opts});
      if (url === '/api/auth/me') return options.expired
        ? {ok:false, status:401, json:async()=>({error:'Invalid or expired session'})}
        : {ok:true, status:200, json:async()=>({role:'admin'})};
      if (url === '/api/bootstrap') {
        if (options.offline) throw new Error('Failed to fetch');
        if (options.deferBootstrap) await new Promise(resolve => waitingBootstraps.push(resolve));
        return {ok:true, status:200, json:async()=>copy(server)};
      }
      if (url === '/api/sync') {
        const body = JSON.parse(opts.body);
        const bad = body.users?.some(u => !u.id || !u.username);
        if (bad) return {ok:false, status:422, json:async()=>({error:'Sync users[] entries need id and username'})};
        if (body.config) server.config = copy(body.config);
        if (body.fleet) server.fleet = copy(body.fleet);
        return {ok:true, status:200, json:async()=>({status:'synced'})};
      }
      throw new Error('Unexpected request ' + url);
    }
  });
  vm.runInContext(script, context, {timeout: 2000});
  return {context, calls, storage, elements, server, timers, errors, waitingBootstraps};
}
const tick = () => new Promise(resolve => setImmediate(resolve));

(async () => {
  // A legacy localStorage entry lacking a username used to trigger HTTP 422
  // whenever a full-state sync was attempted. Refresh must discard it, not PUT.
  const p = loadPage({users:[{id:'legacy-broken'}], fleet:[{id:7}]});
  assert.match(p.elements.get('app').innerHTML, /Loading server data/);
  await tick();
  assert.equal(vm.runInContext('__apexServerReady', p.context), true);
  assert.equal(p.calls.filter(c => c.url === '/api/sync').length, 0);
  assert.equal(JSON.parse(p.storage.get('v2_users')).length, 1);
  assert.equal(JSON.parse(p.storage.get('v2_fleet')).length, seed.fleet.length);

  // A settings edit sends ONLY config; stale users/other collections are not
  // included in the payload, and a successful save clears the pending marker.
  await vm.runInContext('config.driverRate=7300;persist();apexPushNow()', p.context);
  const pushes = p.calls.filter(c => c.url === '/api/sync');
  assert.equal(pushes.length, 1);
  assert.deepEqual(Object.keys(JSON.parse(pushes[0].opts.body)), ['config']);
  assert.equal(p.server.config.driverRate, 7300);
  assert.deepEqual(JSON.parse(p.storage.get('v2_apexPendingSyncKeys')), []);

  // A real validation failure displays the backend's reason (not "HTTP 422")
  // and keeps the unsaved edit available for retry after refresh.
  await vm.runInContext("users.push({id:'bad-user'});persist();apexPushNow()", p.context);
  assert.match(p.elements.get('apex-sync-banner').textContent,
    /HTTP 422: Sync users\[\] entries need id and username/);
  assert.ok(JSON.parse(p.storage.get('v2_apexPendingSyncKeys')).includes('users'));

  // New edits marked pending survive refresh; an unrelated stale cache from
  // the old version (no marker) was discarded in the first assertion above.
  const reloaded = loadPage({
    config: {...seed.config, driverRate: 8200},
    apexPendingSyncKeys: ['config']
  });
  await tick();
  assert.equal(JSON.parse(reloaded.storage.get('v2_config')).driverRate, 8200);
  await vm.runInContext('apexPushNow()', reloaded.context);
  assert.equal(reloaded.server.config.driverRate, 8200);
  assert.deepEqual(JSON.parse(reloaded.storage.get('v2_apexPendingSyncKeys')), []);

  // Intentional deletion of all cars is not silently undone by default seeding
  // in browser storage; fleet replacement can be synced as an empty array.
  const empty = loadPage({fleet:[], apexPendingSyncKeys:['fleet']});
  await tick();
  assert.equal(vm.runInContext('fleet.length', empty.context), 0);
  await vm.runInContext('apexPushNow()', empty.context);
  assert.deepEqual(empty.server.fleet, []);

  // The catalog sync is a replace operation: do not delete a car another
  // admin added while this page was open, or undo our own intentional delete.
  const concurrent = loadPage();
  await tick();
  concurrent.server.fleet.push({...seed.fleet[0], id: 999, name: 'Other admin car'});
  await vm.runInContext('fleet=fleet.filter(c=>c.id!==1);persist();apexPushNow()', concurrent.context);
  assert.equal(concurrent.server.fleet.some(c => c.id === 999), true);
  assert.equal(concurrent.server.fleet.some(c => c.id === 1), false);

  // Bad authentication and an unreachable backend must not enable editing or
  // attempt to upload a stale cache on refresh.
  const expired = loadPage({}, {expired:true});
  await tick();
  assert.equal(vm.runInContext('__apexServerReady', expired.context), false);
  assert.equal(vm.runInContext('isAdminAuthenticated()', expired.context), false);
  assert.equal(expired.calls.some(c => c.url === '/api/sync'), false);
  const offline = loadPage({}, {offline:true});
  await tick();
  assert.equal(vm.runInContext('__apexServerReady', offline.context), false);
  assert.match(offline.elements.get('app').innerHTML, /Loading server data/);
  assert.match(offline.elements.get('apex-sync-banner').textContent, /retry loading/i);

  // A slow first bootstrap must not replace an admin edit made after a retry
  // finished: only the latest in-flight response is allowed to update state.
  const delayed = loadPage({}, {deferBootstrap:true});
  await tick();
  assert.equal(delayed.waitingBootstraps.length, 1);
  const retry = vm.runInContext('v3RefreshBootstrap()', delayed.context);
  await tick();
  assert.equal(delayed.waitingBootstraps.length, 2);
  delayed.waitingBootstraps[1]();
  await retry;
  vm.runInContext('config.driverRate=9700;persist()', delayed.context);
  delayed.waitingBootstraps[0]();
  await tick();
  assert.equal(vm.runInContext('config.driverRate', delayed.context), 9700);
  assert.ok(JSON.parse(delayed.storage.get('v2_apexPendingSyncKeys')).includes('config'));
  console.log('PASS: refresh ignores stale cache, syncs only edited fields, shows 422 reason, retains pending edits');
})().catch(error => {console.error(error);process.exitCode=1});
