const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const js = fs.readFileSync(path.join(__dirname, '../../frontend/customer.js'), 'utf8');
const css = fs.readFileSync(path.join(__dirname, '../../frontend/customer.css'), 'utf8');

assert.match(js, /v3Upload\(dataUrl,'photo','car',String\(c\.id\)\)/);
assert.match(js, /delete c\.customImage;delete c\.customImages;delete c\.images/);
assert.match(js, /\/api\/vehicle-images\/'\+imageId/);
assert.match(js, /await v3Api\('\/api\/fleet\/'\+id,\{method:'PUT'/);
assert.doesNotMatch(js, /c\.customImage=window\.fleetEditTemp\.main/);
assert.match(js, /fleet\.forEach\(compactFleetImageRefs\)/);
assert.match(css, /@media\(max-width:480px\)\{[^}]*\.wrap\{padding:0 18px\}/);
console.log('PASS: fleet photos use compact server references and mobile layout rules remain enabled');
