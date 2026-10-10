import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';

const root = new URL('../../app/src/main/assets/hearth/', import.meta.url);
test('bundled model sizes and hashes match the manifest and runtime config', async () => {
  const manifest = JSON.parse(await readFile(new URL('models/manifest.json', root)));
  const config = JSON.parse(await readFile(new URL('models/config.json', root)));
  for (const [file, expected] of Object.entries(manifest.files)) {
    const bytes = await readFile(new URL(`models/${file}`, root));
    assert.equal(bytes.length, expected.bytes);
    const hash = createHash('sha256').update(bytes).digest('hex');
    assert.equal(hash, expected.sha256);
    assert.equal(hash, config[file.includes('detector') ? 'detectorSha256' : 'digitsSha256']);
  }
  assert.equal(config.size, 512);
  assert.deepEqual(config.classNames, ['0','1','row','2','3','4','5','6','7','8','9']);
});

test('bundled native OCR assets include notices and no web execution code', async () => {
  for (const path of ['vendor/ONNX-RUNTIME-LICENSE.txt', 'vendor/ONNX-RUNTIME-NOTICE.txt', 'LICENSE', 'NOTICE']) {
    assert.ok((await readFile(new URL(path, root))).length > 0, path);
  }
  const {readdir} = await import('node:fs/promises');
  const files = await readdir(root, {recursive: true});
  assert.equal(files.some(path => /\.(?:js|mjs|wasm|html)$/.test(path)), false);
});
