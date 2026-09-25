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
    fields: {start, end, startTime: '09:00', endTime: '09:00', city: 'Lahore', service: 'Self-drive'},
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
const cart = [], toasts = [], navigations = [];
class FormData {
  constructor(element) {this.element = element;}
  *[Symbol.iterator]() {yield* Object.entries(this.element.fields);}
}
const context = vm.createContext({
  document: {getElementById: id => elements[id] || null,
    querySelector: () => ({textContent: ''})},
  FormData, config: {driverRate: 4500}, cart, trip: form.fields,
  carBy: () => ({id: 1, status: 'Active', name: 'Test car', image: 'test-car', rate: 7000}),
  validTrip: fields => fields.end > fields.start,
  available: () => true,
  v3Online: () => true,
  v3Hourly: () => 700,
  money: value => 'PKR ' + value,
  quote: () => ({}),
  priceLines: () => '',
  photo: value => value, date: value => value, esc: value => value,
  persist: () => {}, modal: () => {}, toast: message => toasts.push(message),
  route: 'cart', account: {id: 'C1'}, checkoutStep: 2,
  go: target => navigations.push(target), auth: () => {throw new Error('Unexpected login')},
  fetch: url => new Promise((resolve, reject) => requests.push({url, resolve, reject}))
});
vm.runInContext(source.slice(begin, end), context);
const addBegin = source.indexOf('function addToCart(e,id){');
const addEnd = source.indexOf('function setGallery(i){', addBegin);
const checkoutBegin = source.indexOf('async function beginCheckout(){');
const checkoutEnd = source.indexOf('function auth(', checkoutBegin);
const windowBegin = source.indexOf('function apexTripWindow(t=trip){');
const windowEnd = source.indexOf('async function apexPollAvailability(){', windowBegin);
assert.ok(addEnd > addBegin && checkoutEnd > checkoutBegin && windowEnd > windowBegin);
vm.runInContext('let __apexCheckoutChecking=false;\n' +
  source.slice(addBegin, addEnd) + source.slice(checkoutBegin, checkoutEnd) +
  source.slice(windowBegin, windowEnd), context);
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

  // A stale local booking may have been cancelled remotely. The server's
  // current answer, not that cached booking, decides whether a car can be added.
  elements['reservation-form'] = form;
  context.available = () => false;
  context.refreshQuote(1);
  const submit = {target: form, preventDefault() {}};
  context.addToCart(submit, 1);
  assert.equal(cart.length, 0, 'No add while the authoritative check is pending');
  assert.doesNotMatch(elements['quote-breakdown'].innerHTML, /Unavailable for this date/);
  respond(6, {available: true});
  await settle();
  assert.equal(form.button.disabled, false);
  assert.equal(form.dataset.availabilityResult, 'available');
  assert.equal(form.dataset.availabilityWindow, '2026-10-03T09:00|2026-10-04T09:00');
  form.fields.startTime = '10:00';
  context.addToCart(submit, 1);
  assert.equal(cart.length, 0, 'Confirmation for another time window must not be reused');
  form.fields.startTime = '09:00';
  context.addToCart(submit, 1);
  assert.equal(cart.length, 1, 'A server-confirmed available car can be added');

  // Recheck selected cars at checkout; local order caches are not authoritative.
  const checkoutButton = {disabled: false, textContent: 'Continue to booking', isConnected: true};
  elements['cart-checkout'] = checkoutButton;
  const paths = [];
  context.v3Api = async path => {paths.push(path); return {available: true};};
  await context.beginCheckout();
  assert.equal(navigations.at(-1), 'checkout');
  assert.match(paths[0], /start=2026-10-03T09%3A00&end=2026-10-04T09%3A00/);
  assert.equal(checkoutButton.disabled, false);

  context.v3Api = async () => ({available: false});
  await context.beginCheckout();
  assert.equal(navigations.length, 1, 'New server booking stops checkout');
  assert.match(toasts.at(-1), /unavailable/);
  context.v3Api = async () => {throw new Error('Network unavailable');};
  await context.beginCheckout();
  assert.equal(navigations.length, 1, 'Failed check stops checkout');
  assert.match(toasts.at(-1), /Could not check availability/);

  let finish;
  context.v3Api = () => new Promise(resolve => {finish = resolve;});
  const pending = context.beginCheckout();
  assert.equal(checkoutButton.disabled, true);
  cart[0].startTime = '11:00';
  finish({available: true});
  await pending;
  assert.equal(navigations.length, 1, 'A changed selection cannot reuse an old checkout check');
  assert.equal(checkoutButton.disabled, false);
  console.log('PASS: quote/checkout honor server availability, ignore stale data and fail closed');
})().catch(error => {console.error(error); process.exitCode = 1});
