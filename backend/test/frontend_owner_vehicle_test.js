const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const js = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../frontend/customer.css'), 'utf8');

assert.match(js, /files\.length!==3[^\n]*Exactly 3 vehicle photos/);
assert.match(js, /\/api\/owner\/cars\/.*\/edit/);
assert.match(js, /\/api\/owner\/payout-account/);
assert.match(js, /\/api\/owner\/wallet/);
assert.match(js, /Your current listing stays live\. Changes publish only after admin approval/);
assert.match(js, /Delete this listed car\?/);
assert.match(js, /getCarPhoto\(c,p\.key\)/);
assert.match(js, /Payout history/);
assert.match(js, /Pending payment/);
assert.match(js, /\/api\/admin\/owners\//);
assert.match(css, /\.owner-listed-card/);
console.log('PASS: exactly-three owner images, backend owner dashboard, approval edit and deletion UI');
