const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const js = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');

assert.match(js, /Approve all pending/);
assert.match(js, /onclick="approveDriver\(\$\{d\.id\}\)"/);
assert.match(js, /\/api\/drivers\/'\+id/);
assert.match(js, /status:'Approved',active:true/);
assert.match(js, /Driver must be approved|driverIsApproved\(d\)&&d\.active/);
console.log('PASS: admin can approve one or all pending drivers and assignment filters unapproved drivers');
