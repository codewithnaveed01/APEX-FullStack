// An older availability response must never replace the current date/time quote.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const begin = source.lastIndexOf('function refreshQuote(id){');
const end = source.indexOf('/* A quiet, uniform card', begin);
assert.ok(begin > 0 && end > begin, 'Could not find the active reservation quote');

function fakeForm(start, end) {
  const button = {disabled: false};
  const serverStatus = {innerHTML: '', textContent: ''};
  return {
    dataset: {}, button, serverStatus,
    fields: {start, end, startTime: '09:00', endTime: '09:00', service: 'Self-drive'},
    querySelector(selector) {
      return {'#add-car': button, '#v3-server-avail': serverStatus}[selector] || null;
    }
  };
}
const form = fakeForm('2026-10-01', '2026-10-02');
const elements = {
  'reservation-form': form,
  'quote-breakdown': {innerHTML: ''}
};
const requests = [];
class FormData {
  constructor(element) {this.element = element;}
  *[Symbol.iterator]() {yield* Object.entries(this.element.fields);}
}
const context = vm.createContext({
  document: {getElementById: id => elements[id] || null},
  FormData, config: {driverRate: 4500},
  carBy: () => ({id: 1, status: 'Active', rate: 7000}),
  validTrip: fields => fields.end > fields.start,
  available: () => true,
  v3Online: () => true,
  v3Hourly: () => 700,
  money: value => 'PKR ' + value,
  quote: () => ({}),
  priceLines: () => '',
  fetch: url => new Promise((resolve, reject) => requests.push({url, resolve, reject}))
});
vm.runInContext(source.slice(begin, end), context);
const settle = () => new Promise(resolve => setImmediate(resolve));
const respond = (n, data) => requests[n].resolve({ok: true, json: async () => data});

(async () => {
  context.refreshQuote(1);
  assert.equal(form.button.disabled, true, 'Server confirmation is required before adding a car');
  assert.match(form.serverStatus.textContent, /Checking availability/);
  assert.match(requests[0].url, /start=2026-10-01/);

  form.fields.start = '2026-10-03';
  form.fields.end = '2026-10-04';
  context.refreshQuote(1);
  assert.equal(form.button.disabled, true);
  respond(1, {available: true});
  await settle();
  assert.equal(form.button.disabled, false);
  assert.match(form.serverStatus.innerHTML, /confirms availability/);

  // First request returns after the second: it must not disable the new quote.
  respond(0, {available: false});
  await settle();
  assert.equal(form.button.disabled, false);
  assert.match(form.serverStatus.innerHTML, /confirms availability/);

  context.refreshQuote(1);
  respond(2, {available: false});
  await settle();
  assert.equal(form.button.disabled, true, 'Current rented dates must be disabled');
  assert.match(form.serverStatus.innerHTML, /already booked/);

  context.refreshQuote(1);
  respond(3, {available: 'invalid'});
  await settle();
  assert.equal(form.button.disabled, true, 'Invalid API responses must fail closed');
  assert.match(form.serverStatus.textContent, /unavailable/);

  context.refreshQuote(1);
  requests[4].reject(new Error('Network unavailable'));
  await settle();
  assert.equal(form.button.disabled, true, 'Offline checks must fail closed');
  assert.match(form.serverStatus.textContent, /unavailable/);

  // A request from an unmounted vehicle page cannot write into the new form.
  context.refreshQuote(1);
  const nextForm = fakeForm('2026-10-05', '2026-10-06');
  elements['reservation-form'] = nextForm;
  respond(5, {available: false});
  await settle();
  assert.equal(nextForm.serverStatus.innerHTML, '');
  assert.equal(nextForm.button.disabled, false);
  console.log('PASS: quote ignores stale/out-of-order responses and fails closed until server confirms');
})().catch(error => {console.error(error); process.exitCode = 1});
