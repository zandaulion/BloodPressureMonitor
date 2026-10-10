import test from 'node:test';
import assert from 'node:assert/strict';
import {assemble, isPlausibleReading} from './reference/reading.js';

const detection = (className, box, score = 0.99) => ({class: className, box, score});
// All values in this file are synthetic and are not sourced from a real reading.

test('recovers a narrow digit clipped by the row proposal edge', () => {
  const detections = [
    detection('row', [20, 10, 82, 30]),
    detection('1', [14, 11, 23, 29]), detection('2', [34, 11, 47, 29]), detection('3', [57, 11, 68, 29]),
    detection('row', [20, 40, 82, 60]),
    detection('7', [30, 41, 43, 59]), detection('8', [53, 41, 66, 59]),
    detection('row', [20, 70, 82, 90]),
    detection('6', [30, 71, 43, 89]), detection('4', [53, 71, 66, 89]),
  ];

  assert.deepEqual(assemble(detections).reading, {sys: 123, dia: 78, pulse: 64});
});

test('does not sweep a distant digit into a wide row', () => {
  const detections = [
    detection('row', [20, 10, 220, 30]),
    detection('1', [25, 11, 34, 29]), detection('2', [44, 11, 57, 29]), detection('3', [67, 11, 78, 29]),
    detection('8', [6, 11, 12, 29]),
    detection('row', [20, 40, 220, 60]),
    detection('7', [30, 41, 43, 59]), detection('8', [53, 41, 66, 59]),
    detection('row', [20, 70, 220, 90]),
    detection('6', [30, 71, 43, 89]), detection('4', [53, 71, 66, 89]),
  ];

  assert.deepEqual(assemble(detections).reading, {sys: 123, dia: 78, pulse: 64});
});

test('identifies plausible complete readings', () => {
  assert.equal(isPlausibleReading({sys: 123, dia: 78, pulse: 64}), true);
  assert.equal(isPlausibleReading({sys: 12, dia: 78, pulse: 64}), false);
  assert.equal(isPlausibleReading({sys: 78, dia: 123, pulse: 64}), false);
  assert.equal(isPlausibleReading(null), false);
});
