import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
const dir = path.join(import.meta.dirname, 'metaticket-home-master/metaticket-home-master/src/assets/demo-events');
const manifest = JSON.parse(await fs.readFile(path.join(dir, 'manifest.json'), 'utf8'));
const queue = Object.values(manifest).flat();
const hashes = new Set();
async function worker() {
  while (queue.length) {
    const photo = queue.shift();
    const file = path.join(dir, photo.file);
    let buffer;
    try { buffer = await fs.readFile(file); } catch {}
    if (!buffer) {
      const url = `https://images.pexels.com/photos/${photo.id}/pexels-photo-${photo.id}.jpeg?auto=compress&cs=tinysrgb&w=1200&h=750&fit=crop`;
      const response = await fetch(url, {signal:AbortSignal.timeout(60000)});
      if (!response.ok || !response.headers.get('content-type')?.startsWith('image/')) {
        throw new Error(`${photo.file}: HTTP ${response.status}`);
      }
      buffer = Buffer.from(await response.arrayBuffer());
      if (buffer.length < 5000) throw new Error(`Image too small: ${photo.file}`);
      await fs.writeFile(file, buffer);
    }
    const hash = crypto.createHash('sha256').update(buffer).digest('hex');
    if (hashes.has(hash)) throw new Error(`Duplicate image: ${photo.file}`);
    hashes.add(hash);
    console.log(`Ready ${photo.file} (${Math.round(buffer.length/1024)} KB)`);
  }
}
await Promise.all(Array.from({length:5}, worker));
console.log(`Verified ${hashes.size} distinct event images.`);
