import assert from 'node:assert/strict';
import { validAndroidVersion, validDeviceModel } from '../functions/analytics-ingest/validation.ts';

for (const value of ['OnePlus CPH2655', 'Samsung SM-S938U', 'Google Pixel 10 Pro', 'Unknown']) {
  assert.equal(validDeviceModel(value), true, value);
}
for (const value of ['', ' OnePlus', 'OnePlus\nID', 'A'.repeat(81), 16, 'Device@123']) {
  assert.equal(validDeviceModel(value), false, String(value));
}
for (const value of ['16', '14', '8.1', 'Unknown']) {
  assert.equal(validAndroidVersion(value), true, value);
}
for (const value of ['', ' 16', '16\nID', '16;id', 16, 'A'.repeat(81)]) {
  assert.equal(validAndroidVersion(value), false, String(value));
}
console.log('Device analytics ingestion validation passed.');
