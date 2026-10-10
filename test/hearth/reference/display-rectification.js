// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 zandaulion
// Experimental, last-resort display rectification. Candidate geometry comes
// only from image edges and detector row locations, never expected readings.
import {isPlausibleReading} from './reading.js';

const ANALYSIS_MAX_EDGE = 768;
const WARP_MAX_EDGE = 1024;

export function orderQuad(points) {
  if (!Array.isArray(points) || points.length !== 4) throw new Error('A quadrilateral needs four points');
  const remaining = points.map((point) => [Number(point[0]), Number(point[1])]);
  if (remaining.some((point) => !point.every(Number.isFinite))) throw new Error('Invalid quadrilateral point');
  const bySum = [...remaining].sort((a, b) => (a[0] + a[1]) - (b[0] + b[1]));
  const topLeft = bySum[0], bottomRight = bySum[3];
  const middle = remaining.filter((point) => point !== topLeft && point !== bottomRight);
  const [topRight, bottomLeft] = middle.sort((a, b) => (b[0] - b[1]) - (a[0] - a[1]));
  return [topLeft, topRight, bottomRight, bottomLeft];
}

const distance = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1]);

function polygonArea(points) {
  let sum = 0;
  for (let i = 0; i < points.length; i++) {
    const next = points[(i + 1) % points.length];
    sum += points[i][0] * next[1] - next[0] * points[i][1];
  }
  return Math.abs(sum) / 2;
}

function bounds(points) {
  const xs = points.map((point) => point[0]), ys = points.map((point) => point[1]);
  return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)];
}

function boundsIou(a, b) {
  const left = Math.max(a[0], b[0]), top = Math.max(a[1], b[1]);
  const right = Math.min(a[2], b[2]), bottom = Math.min(a[3], b[3]);
  const intersection = Math.max(0, right - left) * Math.max(0, bottom - top);
  const areaA = Math.max(0, a[2] - a[0]) * Math.max(0, a[3] - a[1]);
  const areaB = Math.max(0, b[2] - b[0]) * Math.max(0, b[3] - b[1]);
  return intersection / Math.max(1, areaA + areaB - intersection);
}

function pointInQuad(point, quad) {
  let direction = 0;
  for (let i = 0; i < 4; i++) {
    const a = quad[i], b = quad[(i + 1) % 4];
    const cross = (b[0] - a[0]) * (point[1] - a[1]) - (b[1] - a[1]) * (point[0] - a[0]);
    if (Math.abs(cross) < 1e-6) continue;
    if (!direction) direction = Math.sign(cross);
    else if (Math.sign(cross) !== direction) return false;
  }
  return true;
}

function grayscale(rgba) {
  const gray = new Uint8Array(rgba.length / 4);
  for (let i = 0; i < gray.length; i++) {
    gray[i] = Math.round(.299 * rgba[i * 4] + .587 * rgba[i * 4 + 1] + .114 * rgba[i * 4 + 2]);
  }
  return gray;
}

function equalize(input) {
  const histogram = new Uint32Array(256);
  for (const value of input) histogram[value]++;
  const map = new Uint8Array(256);
  let cumulative = 0, first = 0;
  for (let i = 0; i < 256; i++) {
    cumulative += histogram[i];
    if (!first && cumulative) first = cumulative;
    map[i] = Math.round(255 * Math.max(0, cumulative - first) / Math.max(1, input.length - first));
  }
  return Uint8Array.from(input, (value) => map[value]);
}

function gaussian5(input, width, height) {
  const kernel = [1, 4, 6, 4, 1], temp = new Float32Array(input.length), output = new Uint8Array(input.length);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    let sum = 0;
    for (let k = -2; k <= 2; k++) sum += kernel[k + 2] * input[y * width + Math.max(0, Math.min(width - 1, x + k))];
    temp[y * width + x] = sum / 16;
  }
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    let sum = 0;
    for (let k = -2; k <= 2; k++) sum += kernel[k + 2] * temp[Math.max(0, Math.min(height - 1, y + k)) * width + x];
    output[y * width + x] = Math.round(sum / 16);
  }
  return output;
}

function edgeMask(input, width, height) {
  const gradient = new Float32Array(input.length), direction = new Uint8Array(input.length);
  for (let y = 1; y < height - 1; y++) for (let x = 1; x < width - 1; x++) {
    const i = y * width + x;
    const gx = -input[i - width - 1] + input[i - width + 1]
      - 2 * input[i - 1] + 2 * input[i + 1]
      - input[i + width - 1] + input[i + width + 1];
    const gy = -input[i - width - 1] - 2 * input[i - width] - input[i - width + 1]
      + input[i + width - 1] + 2 * input[i + width] + input[i + width + 1];
    gradient[i] = Math.hypot(gx, gy);
    const angle = (Math.atan2(gy, gx) * 180 / Math.PI + 180) % 180;
    direction[i] = angle < 22.5 || angle >= 157.5 ? 0 : angle < 67.5 ? 1 : angle < 112.5 ? 2 : 3;
  }
  const thinned = new Float32Array(input.length);
  for (let y = 1; y < height - 1; y++) for (let x = 1; x < width - 1; x++) {
    const i = y * width + x, value = gradient[i];
    let before, after;
    if (direction[i] === 0) { before = gradient[i - 1]; after = gradient[i + 1]; }
    else if (direction[i] === 1) { before = gradient[i - width + 1]; after = gradient[i + width - 1]; }
    else if (direction[i] === 2) { before = gradient[i - width]; after = gradient[i + width]; }
    else { before = gradient[i - width - 1]; after = gradient[i + width + 1]; }
    if (value >= before && value >= after) thinned[i] = value;
  }
  const mask = new Uint8Array(input.length), stack = new Int32Array(input.length);
  let tail = 0;
  for (let i = 0; i < thinned.length; i++) if (thinned[i] >= 110) {
    mask[i] = 1; stack[tail++] = i;
  }
  let head = 0;
  while (head < tail) {
    const index = stack[head++], y = Math.floor(index / width), x = index - y * width;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
      if (!dx && !dy) continue;
      const nx = x + dx, ny = y + dy;
      if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
      const next = ny * width + nx;
      if (!mask[next] && thinned[next] >= 35) { mask[next] = 1; stack[tail++] = next; }
    }
  }
  return closeMask(mask, width, height, 3);
}

function cross(origin, a, b) {
  return (a[0] - origin[0]) * (b[1] - origin[1]) - (a[1] - origin[1]) * (b[0] - origin[0]);
}

function convexHull(points) {
  const unique = [...new Map(points.map((point) => [point.join(','), point])).values()]
    .sort((a, b) => a[0] - b[0] || a[1] - b[1]);
  if (unique.length <= 4) return unique;
  const lower = [];
  for (const point of unique) {
    while (lower.length >= 2 && cross(lower.at(-2), lower.at(-1), point) <= 0) lower.pop();
    lower.push(point);
  }
  const upper = [];
  for (let i = unique.length - 1; i >= 0; i--) {
    const point = unique[i];
    while (upper.length >= 2 && cross(upper.at(-2), upper.at(-1), point) <= 0) upper.pop();
    upper.push(point);
  }
  return lower.slice(0, -1).concat(upper.slice(0, -1));
}

function largestQuad(hull) {
  if (hull.length < 4) return null;
  const points = hull.length <= 24 ? hull : [...Array(24)].map((_, index) =>
    hull[Math.floor(index * hull.length / 24)]);
  let best = null, bestArea = 0;
  for (let a = 0; a < points.length - 3; a++) for (let b = a + 1; b < points.length - 2; b++) {
    for (let c = b + 1; c < points.length - 1; c++) for (let d = c + 1; d < points.length; d++) {
      const quad = [points[a], points[b], points[c], points[d]], area = polygonArea(quad);
      if (area > bestArea) { best = quad; bestArea = area; }
    }
  }
  return best ? orderQuad(best) : null;
}

function closeMask(mask, width, height, radius) {
  const horizontal = new Uint8Array(mask.length), dilated = new Uint8Array(mask.length);
  const vertical = new Uint8Array(mask.length), output = new Uint8Array(mask.length);
  for (let y = 0; y < height; y++) {
    let count = 0;
    for (let x = -radius; x <= radius; x++) count += mask[y * width + Math.max(0, Math.min(width - 1, x))];
    for (let x = 0; x < width; x++) {
      horizontal[y * width + x] = count ? 1 : 0;
      count += mask[y * width + Math.min(width - 1, x + radius + 1)]
        - mask[y * width + Math.max(0, x - radius)];
    }
  }
  for (let x = 0; x < width; x++) {
    let count = 0;
    for (let y = -radius; y <= radius; y++) count += horizontal[Math.max(0, Math.min(height - 1, y)) * width + x];
    for (let y = 0; y < height; y++) {
      dilated[y * width + x] = count ? 1 : 0;
      count += horizontal[Math.min(height - 1, y + radius + 1) * width + x]
        - horizontal[Math.max(0, y - radius) * width + x];
    }
  }
  const window = radius * 2 + 1;
  for (let y = 0; y < height; y++) {
    let count = 0;
    for (let x = -radius; x <= radius; x++) count += dilated[y * width + Math.max(0, Math.min(width - 1, x))];
    for (let x = 0; x < width; x++) {
      vertical[y * width + x] = count === window ? 1 : 0;
      count += dilated[y * width + Math.min(width - 1, x + radius + 1)]
        - dilated[y * width + Math.max(0, x - radius)];
    }
  }
  for (let x = 0; x < width; x++) {
    let count = 0;
    for (let y = -radius; y <= radius; y++) count += vertical[Math.max(0, Math.min(height - 1, y)) * width + x];
    for (let y = 0; y < height; y++) {
      output[y * width + x] = count === window ? 1 : 0;
      count += vertical[Math.min(height - 1, y + radius + 1) * width + x]
        - vertical[Math.max(0, y - radius) * width + x];
    }
  }
  return output;
}

export function detectDisplayQuads(rgba, width, height, detections = [], limit = 3) {
  if (!rgba || width < 80 || height < 80) return [];
  const mask = edgeMask(gaussian5(equalize(grayscale(rgba)), width, height), width, height);
  const visited = new Uint8Array(mask.length), stack = new Int32Array(mask.length);
  const rowCenters = detections.filter((item) => item.class === 'row' && item.score >= .2)
    .map((item) => [(item.box[0] + item.box[2]) / 2, (item.box[1] + item.box[3]) / 2]);
  const imageArea = width * height, scored = [];
  for (let start = 0; start < mask.length; start++) {
    if (!mask[start] || visited[start]) continue;
    let head = 0, tail = 0, pixels = 0;
    const boundary = [];
    stack[tail++] = start; visited[start] = 1;
    while (head < tail) {
      const index = stack[head++], y = Math.floor(index / width), x = index - y * width;
      pixels++;
      let edge = false;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        if (!dx && !dy) continue;
        const nx = x + dx, ny = y + dy;
        if (nx < 0 || nx >= width || ny < 0 || ny >= height) { edge = true; continue; }
        const next = ny * width + nx;
        if (!mask[next]) edge = true;
        else if (!visited[next]) { visited[next] = 1; stack[tail++] = next; }
      }
      if (edge) boundary.push([x, y]);
    }
    if (pixels < 40) continue;
    const quad = largestQuad(convexHull(boundary));
    if (!quad) continue;
    if (new Set(quad.map((point) => point.join(','))).size !== 4) continue;
    const area = polygonArea(quad), fraction = area / imageArea;
    if (fraction < .025 || fraction > .9) continue;
    const quadWidth = Math.max(distance(quad[0], quad[1]), distance(quad[3], quad[2]));
    const quadHeight = Math.max(distance(quad[0], quad[3]), distance(quad[1], quad[2]));
    const aspect = quadWidth / Math.max(1, quadHeight);
    if (aspect < .45 || aspect > 3.2 || quadWidth < 60 || quadHeight < 60) continue;
    const contained = rowCenters.filter((point) => pointInQuad(point, quad)).length;
    const center = quad.reduce((sum, point) => [sum[0] + point[0] / 4, sum[1] + point[1] / 4], [0, 0]);
    const centerDistance = Math.hypot(center[0] - width / 2, center[1] - height / 2) / Math.max(width, height);
    scored.push({score: 3 * contained + 1.5 * fraction - .4 * centerDistance, points: quad, bounds: bounds(quad)});
  }
  scored.sort((a, b) => b.score - a.score);
  const selected = [];
  for (const candidate of scored) {
    if (selected.some((old) => boundsIou(candidate.bounds, old.bounds) > .9)) continue;
    selected.push(candidate);
    if (selected.length === limit) break;
  }
  return selected.map(({score, points}) => ({score, points}));
}

function solve(matrix) {
  const n = matrix.length;
  for (let column = 0; column < n; column++) {
    let pivot = column;
    for (let row = column + 1; row < n; row++) if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) pivot = row;
    if (Math.abs(matrix[pivot][column]) < 1e-9) return null;
    [matrix[column], matrix[pivot]] = [matrix[pivot], matrix[column]];
    const divisor = matrix[column][column];
    for (let j = column; j <= n; j++) matrix[column][j] /= divisor;
    for (let row = 0; row < n; row++) {
      if (row === column) continue;
      const factor = matrix[row][column];
      for (let j = column; j <= n; j++) matrix[row][j] -= factor * matrix[column][j];
    }
  }
  return matrix.map((row) => row[n]);
}

function homography(destination, source) {
  const matrix = [];
  for (let i = 0; i < 4; i++) {
    const [x, y] = destination[i], [u, v] = source[i];
    matrix.push([x, y, 1, 0, 0, 0, -u * x, -u * y, u]);
    matrix.push([0, 0, 0, x, y, 1, -v * x, -v * y, v]);
  }
  return solve(matrix);
}

export function warpQuadRgba(rgba, sourceWidth, sourceHeight, rawPoints, maxEdge = WARP_MAX_EDGE) {
  const points = orderQuad(rawPoints);
  let width = Math.round(Math.max(distance(points[0], points[1]), distance(points[3], points[2])));
  let height = Math.round(Math.max(distance(points[0], points[3]), distance(points[1], points[2])));
  if (width < 80 || height < 80) return null;
  const scale = Math.min(1, maxEdge / Math.max(width, height));
  width = Math.max(80, Math.round(width * scale)); height = Math.max(80, Math.round(height * scale));
  const transform = homography([[0, 0], [width - 1, 0], [width - 1, height - 1], [0, height - 1]], points);
  if (!transform) return null;
  const output = new Uint8ClampedArray(width * height * 4);
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const denominator = transform[6] * x + transform[7] * y + 1;
    const sx = (transform[0] * x + transform[1] * y + transform[2]) / denominator;
    const sy = (transform[3] * x + transform[4] * y + transform[5]) / denominator;
    const left = Math.max(0, Math.min(sourceWidth - 1, Math.floor(sx)));
    const top = Math.max(0, Math.min(sourceHeight - 1, Math.floor(sy)));
    const right = Math.min(sourceWidth - 1, left + 1), bottom = Math.min(sourceHeight - 1, top + 1);
    const fx = Math.max(0, Math.min(1, sx - left)), fy = Math.max(0, Math.min(1, sy - top));
    const target = (y * width + x) * 4;
    for (let channel = 0; channel < 4; channel++) {
      const topValue = rgba[(top * sourceWidth + left) * 4 + channel] * (1 - fx)
        + rgba[(top * sourceWidth + right) * 4 + channel] * fx;
      const bottomValue = rgba[(bottom * sourceWidth + left) * 4 + channel] * (1 - fx)
        + rgba[(bottom * sourceWidth + right) * 4 + channel] * fx;
      output[target + channel] = Math.round(topValue * (1 - fy) + bottomValue * fy);
    }
  }
  return {rgba: output, width, height};
}

export function createRectifiedViews(bitmap, detections, limit = 3) {
  const analysisScale = Math.min(1, ANALYSIS_MAX_EDGE / Math.max(bitmap.width, bitmap.height));
  const analysisWidth = Math.max(1, Math.round(bitmap.width * analysisScale));
  const analysisHeight = Math.max(1, Math.round(bitmap.height * analysisScale));
  const analysisCanvas = new OffscreenCanvas(analysisWidth, analysisHeight);
  const analysisContext = analysisCanvas.getContext('2d', {willReadFrequently: true});
  analysisContext.drawImage(bitmap, 0, 0, analysisWidth, analysisHeight);
  const scaledDetections = detections.map((item) => ({
    ...item, box: item.box.map((value) => value * analysisScale),
  }));
  const candidates = detectDisplayQuads(
    analysisContext.getImageData(0, 0, analysisWidth, analysisHeight).data,
    analysisWidth, analysisHeight, scaledDetections, limit,
  ).map((candidate) => ({
    ...candidate, points: candidate.points.map((point) => point.map((value) => value / analysisScale)),
  }));
  if (!candidates.length) return [];
  const sourceCanvas = new OffscreenCanvas(bitmap.width, bitmap.height);
  const sourceContext = sourceCanvas.getContext('2d', {willReadFrequently: true});
  sourceContext.drawImage(bitmap, 0, 0);
  const source = sourceContext.getImageData(0, 0, bitmap.width, bitmap.height).data;
  return candidates.flatMap((candidate, index) => {
    const warped = warpQuadRgba(source, bitmap.width, bitmap.height, candidate.points);
    if (!warped) return [];
    const canvas = new OffscreenCanvas(warped.width, warped.height);
    canvas.getContext('2d', {willReadFrequently: true})
      .putImageData(new ImageData(warped.rgba, warped.width, warped.height), 0, 0);
    return [{name: `quad-${index + 1}`, points: candidate.points, canvas}];
  });
}

const readingKey = (result) => isPlausibleReading(result?.reading)
  ? ['sys', 'dia', 'pulse'].map((field) => result.reading[field]).join('/') : null;

export function selectRectifiedFallback(full, replays) {
  if (isPlausibleReading(full.reading)) return full;
  const plausible = replays.filter(({result}) => readingKey(result));
  if (plausible.length < 2) return full;
  const keys = new Set(plausible.map(({result}) => readingKey(result)));
  if (keys.size !== 1) return full;
  const selected = {...plausible[0].result};
  selected.status = 'review';
  selected.score = Math.min(...plausible.map(({result}) => result.score));
  selected.reasons = ['Experimental perspective-corrected views agree; check every value against the monitor.'];
  selected.cropFallback = {
    method: 'display-rectification-consensus', experimental: true,
    views: plausible.map(({name, points}) => ({name, points})),
  };
  return selected;
}
