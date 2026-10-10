import test from 'node:test';
import assert from 'node:assert/strict';
import {
  detectDisplayQuads, orderQuad, selectRectifiedFallback, warpQuadRgba,
} from './reference/display-rectification.js';

// All pixels and readings in this file are synthetic.

const syntheticDisplay = () => {
  const width = 240, height = 180;
  const rgba = new Uint8ClampedArray(width * height * 4);
  for (let i = 0; i < width * height; i++) {
    rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = 25;
    rgba[i * 4 + 3] = 255;
  }
  const points = [[48, 32], [200, 44], [186, 146], [36, 134]];
  const line = (a, b, thickness = 4) => {
    const steps = Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1]));
    for (let step = 0; step <= steps; step++) {
      const x = Math.round(a[0] + (b[0] - a[0]) * step / steps);
      const y = Math.round(a[1] + (b[1] - a[1]) * step / steps);
      for (let dy = -thickness; dy <= thickness; dy++) for (let dx = -thickness; dx <= thickness; dx++) {
        if (x + dx < 0 || x + dx >= width || y + dy < 0 || y + dy >= height) continue;
        const index = ((y + dy) * width + x + dx) * 4;
        rgba[index] = rgba[index + 1] = rgba[index + 2] = 220;
      }
    }
  };
  for (let i = 0; i < 4; i++) line(points[i], points[(i + 1) % 4]);
  for (const y of [68, 92, 116]) line([65, y], [170, y], 2);
  const detections = [68, 92, 116].map((y) => ({
    class: 'row', score: .9, box: [65, y - 5, 170, y + 5],
  }));
  return {rgba, width, height, points, detections};
};

test('orders and rectifies a synthetic perspective display', () => {
  assert.deepEqual(orderQuad([[9, 9], [1, 1], [9, 1], [1, 9]]),
    [[1, 1], [9, 1], [9, 9], [1, 9]]);
  const image = syntheticDisplay();
  const candidates = detectDisplayQuads(
    image.rgba, image.width, image.height, image.detections,
  );
  assert.ok(candidates.length >= 1);
  const warped = warpQuadRgba(
    image.rgba, image.width, image.height, candidates[0].points, 200,
  );
  assert.ok(warped.width >= 140);
  assert.ok(warped.height >= 90);
  assert.equal(warped.rgba.length, warped.width * warped.height * 4);
});

test('rectified fallback requires unopposed agreement and remains review-only', () => {
  const full = {status: 'refused', reading: null, score: 0};
  const result = (dia) => ({
    status: 'candidate', reading: {sys: 122, dia, pulse: 66}, score: .8,
  });
  assert.equal(selectRectifiedFallback(full, [{name: 'one', result: result(78)}]), full);
  assert.equal(selectRectifiedFallback(full, [
    {name: 'one', result: result(78)}, {name: 'two', result: result(79)},
  ]), full);
  const selected = selectRectifiedFallback(full, [
    {name: 'one', points: [], result: result(78)},
    {name: 'two', points: [], result: result(78)},
  ]);
  assert.equal(selected.status, 'review');
  assert.equal(selected.cropFallback.method, 'display-rectification-consensus');
  assert.equal(selected.cropFallback.experimental, true);
});
