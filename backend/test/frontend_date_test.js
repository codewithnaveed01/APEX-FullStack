// No browser/framework needed: check the customer page's first-load trip dates.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

process.env.TZ = 'UTC';
const source = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const firstLoad = source.slice(0, source.indexOf('let route='));
assert.ok(firstLoad.includes("let trip=read('trip'"), 'Could not find the trip initializer');
const fixedNow = '2026-09-25T12:00:00Z';
class Clock extends Date {
  constructor(...args) { super(...(args.length ? args : [fixedNow])); }
  static now() { return Date.parse(fixedNow); }
}

function load(saved) {
  const data = new Map();
  if (saved !== undefined) data.set('v2_trip', JSON.stringify(saved));
  const context = {
    Date: Clock,
    localStorage: {
      getItem: key => data.get(key) || null,
      setItem: (key, value) => data.set(key, value),
      removeItem: key => data.delete(key)
    },
    window: {}
  };
  vm.runInNewContext(firstLoad + '\nglobalThis.firstLoad = {today:TODAY, trip};', context);
  return {today: context.firstLoad.today, trip: context.firstLoad.trip, data};
}

const fresh = load();
assert.equal(fresh.today, '2026-09-25');
assert.equal(fresh.trip.start, '2026-09-26');
assert.equal(fresh.trip.end, '2026-09-27');

const expired = load({city:'Karachi', start:'2026-09-22', end:'2026-09-24', service:'With driver'});
assert.equal(expired.trip.start, '2026-09-26');
assert.equal(expired.trip.end, '2026-09-27');
assert.equal(expired.trip.city, 'Karachi');
assert.equal(JSON.parse(expired.data.get('v2_trip')).start, '2026-09-26');

const future = load({city:'Lahore', start:'2026-10-03', end:'2026-10-04', service:'Self-drive'});
assert.equal(future.trip.start, '2026-10-03');
console.log('PASS: future defaults, expired saved trip reset, future trip retained');
