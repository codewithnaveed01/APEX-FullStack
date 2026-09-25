// Walk-in form: require CNIC photos and live availability before private uploads and booking.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const js = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../frontend/customer.css'), 'utf8');
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, '../seed.json'), 'utf8'));
const start = js.indexOf('function adminManualBooking(){');
const end = js.indexOf('function openWithdrawModal(){', start);
assert.ok(start >= 0 && end > start);

const toasts = [], uploads = [], calls = [];
let modalHtml = '', closed = 0, refreshed = 0, availabilityChecks = 0, available = true;
let nextDoc = 71;
class FormData {
  constructor(form) { this.values = form.values; }
  *[Symbol.iterator]() { yield* Object.entries(this.values); }
}
const window = {location: {protocol: 'https:'}};
const ctx = vm.createContext({
  window, FormData, Date, Math, Number,
  document: {},
  __apexToken: 'admin-token', __apexServerReady: true,
  fleet: [{id: 4, name: 'City Car', rate: 4500, status: 'Active'}],
  config: {officeAddress: 'APEX office'}, bannedCNICs: [], orders: [],
  v3Today: () => '2026-10-01', v3Online: () => true,
  isAdminAuthenticated: () => true,
  cities: () => '<option>Lahore</option>',
  esc: s => s, money: n => 'PKR ' + n,
  modal: (title, html) => { modalHtml = html; },
  closeModal: () => { closed++; },
  toast: message => { toasts.push(message); },
  carBy: id => id == 4 ? {id: 4, status: 'Active'} : null,
  v3ReadFile: file => Promise.resolve('data:' + file.type + ';base64,TEST'),
  v3Upload: async (dataUrl, kind, ownerType, ownerId) => {
    uploads.push({dataUrl, kind, ownerType, ownerId});
    return {id: nextDoc++};
  },
  v3Api: async (url, opts) => {
    if (url.startsWith('/api/availability?')) {
      availabilityChecks++;
      assert.match(url, /&start=2026-10-\d\dT\d\d%3A\d\d&end=2026-10-\d\dT\d\d%3A\d\d/);
      return {available};
    }
    const body = opts?.body ? JSON.parse(opts.body) : null;
    calls.push({url, body});
    if (url === '/api/orders') return {...body, id: 'BK-WALKIN-1'};
    if (url === '/api/payments/record') return {status: 'recorded'};
    throw new Error('unexpected API call: ' + url);
  },
  v3RefreshBootstrap: async () => { refreshed++; return true; }
});
vm.runInContext(js.slice(start, end), ctx);

const png = {type: 'image/png', size: 500};
const inputs = {cnicFront: {files: [png]}, cnicBack: {files: []}};
const button = {disabled: false, textContent: 'Create walk-in booking', isConnected: true};
const form = {
  values: {userId: '', name: 'Walk In', phone: '03001234567', email: 'walkin@example.test',
    identity: '3520212345678', carId: '4', start: '2026-10-05', end: '2026-10-06',
    city: 'Lahore', service: 'Self-drive', pickupMode: 'Office pickup',
    payment: 'Cash on pickup', paid: '800'},
  elements: {namedItem: name => inputs[name]},
  querySelector: () => button
};
const event = {preventDefault() {}, target: form};

(async () => {
  ctx.adminManualBooking();
  assert.match(modalHtml, /name="cnicFront"[^>]*required/);
  assert.match(modalHtml, /name="cnicBack"[^>]*required/);
  assert.match(modalHtml, /image\/png,image\/jpeg,image\/webp,image\/gif/);

  await ctx.submitManualBooking(event);
  assert.equal(uploads.length, 0, 'missing back photo must stop before uploading');
  assert.equal(calls.length, 0);
  assert.equal(availabilityChecks, 0);
  assert.match(toasts.at(-1), /Both CNIC front and back/);

  inputs.cnicBack.files = [png];
  available = false;
  await ctx.submitManualBooking(event);
  assert.equal(availabilityChecks, 1);
  assert.equal(uploads.length, 0, 'unavailable car must stop before uploading');
  assert.equal(calls.length, 0);
  assert.match(toasts.at(-1), /not available/);

  available = true;
  ctx.orders = [{status: 'Confirmed', items: [{carId: 4, startDt: '2026-10-05T09:00', endDt: '2026-10-06T09:00'}]}];
  await ctx.submitManualBooking(event);
  assert.equal(availabilityChecks, 2, 'stale local bookings must not override live availability');
  assert.equal(uploads.length, 2);
  assert.deepEqual(uploads.map(x => x.kind), ['cnic_front', 'cnic_back']);
  assert.equal(uploads[0].ownerType, 'user');
  assert.equal(uploads[0].ownerId, uploads[1].ownerId);
  assert.match(uploads[0].ownerId, /^guest-/);
  assert.equal(calls[0].url, '/api/orders');
  assert.equal(calls[0].body.manual, true);
  assert.deepEqual(calls[0].body.identityDocs, [71, 72]);
  assert.equal(calls[0].body.userId, uploads[0].ownerId);
  assert.equal(calls[0].body.identityStatus, 'Pending');
  assert.equal(calls[0].body.items[0].carId, 4);
  assert.equal(calls[1].url, '/api/payments/record');
  assert.equal(calls[1].body.amount, 800);
  assert.equal(closed, 1);
  assert.equal(refreshed, 1);
  assert.equal(button.disabled, false);

  form.values.payment = 'JazzCash';
  form.values.paid = '0';
  form.values.start = '2026-10-09';
  form.values.end = '2026-10-09';
  form.values.startTime = '10:00';
  form.values.endTime = '17:00';
  await ctx.submitManualBooking(event);
  assert.equal(calls.filter(c => c.url === '/api/orders').length, 2);
  assert.equal(calls.at(-1).body.items[0].startDt, '2026-10-09T10:00');
  assert.equal(calls.at(-1).body.items[0].endDt, '2026-10-09T17:00');
  assert.equal(calls.filter(c => c.url === '/api/payments/record').length, 1,
    'unverified online payment must not credit wallet');

  assert.deepEqual(seed.drivers.map(d => d.id), [1, 2, 3, 4, 5, 6, 7]);
  assert.match(css, /\.trust-track\{[^}]*animation:apexMarquee/);
  assert.match(css, /body:not\(\.page-admin\) \.cars \.car:hover \.car-photo img[^}]*scale\(1\.045\)/);
  assert.match(css, /\.car-photo img\{[^}]*object-fit:contain/);
  assert.match(js, /class="car-hourly"/);
  console.log('PASS: walk-in CNIC front/back required, privately uploaded and linked; seven seeds, ticker and cards');
})().catch(error => { console.error(error); process.exitCode = 1; });
