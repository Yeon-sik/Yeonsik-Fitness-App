import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const [sharpModulePath, inputPath, outputPath, mode = "auto"] = process.argv.slice(2);

if (!sharpModulePath || !inputPath || !outputPath) {
  throw new Error(
    "Usage: node extract-connected-light-background.mjs <sharp-module> <input> <output>",
  );
}

const sharp = require(sharpModulePath);
const { data, info } = await sharp(inputPath)
  .ensureAlpha()
  .raw()
  .toBuffer({ resolveWithObject: true });

const pixelCount = info.width * info.height;
const visited = new Uint8Array(pixelCount);
const queue = new Int32Array(pixelCount);
let head = 0;
let tail = 0;

function isLightNeutral(index) {
  const offset = index * 4;
  const red = data[offset];
  const green = data[offset + 1];
  const blue = data[offset + 2];
  const min = Math.min(red, green, blue);
  const max = Math.max(red, green, blue);
  return min >= 232 && max - min <= 12;
}

function luminanceAt(x, y) {
  const offset = (y * info.width + x) * 4;
  return (
    data[offset] * 0.2126
    + data[offset + 1] * 0.7152
    + data[offset + 2] * 0.0722
  );
}

function hasTwoDimensionalCheckerSignal(x, y, size, componentMember) {
  if (x + size * 2 >= info.width || y + size * 2 >= info.height) return false;
  const indexes = [
    y * info.width + x,
    y * info.width + x + size,
    (y + size) * info.width + x,
    (y + size) * info.width + x + size,
    y * info.width + x + size * 2,
  ];
  if (indexes.some((index) => !componentMember[index])) return false;

  const center = luminanceAt(x, y);
  const nextX = luminanceAt(x + size, y);
  const nextY = luminanceAt(x, y + size);
  const diagonal = luminanceAt(x + size, y + size);
  const repeatX = luminanceAt(x + size * 2, y);
  return Math.abs(center - nextX) >= 4
    && Math.abs(center - nextY) >= 4
    && Math.abs(nextX - nextY) <= 2
    && Math.abs(center - diagonal) <= 2
    && Math.abs(center - repeatX) <= 2;
}

function enqueue(index) {
  if (visited[index] || !isLightNeutral(index)) return;
  visited[index] = 1;
  queue[tail++] = index;
}

for (let x = 0; x < info.width; x += 1) {
  enqueue(x);
  enqueue((info.height - 1) * info.width + x);
}
for (let y = 0; y < info.height; y += 1) {
  enqueue(y * info.width);
  enqueue(y * info.width + info.width - 1);
}

while (head < tail) {
  const index = queue[head++];
  const x = index % info.width;
  const y = Math.floor(index / info.width);
  if (x > 0) enqueue(index - 1);
  if (x + 1 < info.width) enqueue(index + 1);
  if (y > 0) enqueue(index - info.width);
  if (y + 1 < info.height) enqueue(index + info.width);
}

// A generated checkerboard can be enclosed by an object's outline. Remove only
// large light-neutral components that contain a balanced mix of the checker
// board's light and dark tiles. Uniform white illustration fills are preserved.
const componentSeen = new Uint8Array(pixelCount);
const componentQueue = new Int32Array(pixelCount);
const componentMember = new Uint8Array(pixelCount);
for (let start = 0; mode !== "edge-only" && start < pixelCount; start += 1) {
  if (visited[start] || componentSeen[start] || !isLightNeutral(start)) continue;

  let componentHead = 0;
  let componentTail = 0;
  let checkerDarkPixels = 0;
  let checkerLightPixels = 0;
  let componentLeft = info.width;
  let componentTop = info.height;
  let componentRight = -1;
  let componentBottom = -1;
  componentSeen[start] = 1;
  componentMember[start] = 1;
  componentQueue[componentTail++] = start;

  while (componentHead < componentTail) {
    const index = componentQueue[componentHead++];
    const x = index % info.width;
    const y = Math.floor(index / info.width);
    componentLeft = Math.min(componentLeft, x);
    componentTop = Math.min(componentTop, y);
    componentRight = Math.max(componentRight, x);
    componentBottom = Math.max(componentBottom, y);
    const luminance = luminanceAt(x, y);
    if (luminance <= 249) checkerDarkPixels += 1;
    if (luminance >= 252) checkerLightPixels += 1;

    const neighbors = [];
    if (x > 0) neighbors.push(index - 1);
    if (x + 1 < info.width) neighbors.push(index + 1);
    if (y > 0) neighbors.push(index - info.width);
    if (y + 1 < info.height) neighbors.push(index + info.width);
    for (const neighbor of neighbors) {
      if (visited[neighbor] || componentSeen[neighbor] || !isLightNeutral(neighbor)) {
        continue;
      }
      componentSeen[neighbor] = 1;
      componentMember[neighbor] = 1;
      componentQueue[componentTail++] = neighbor;
    }
  }

  let checkerSignals = 0;
  for (let offset = 0; offset < componentTail; offset += 4) {
    const index = componentQueue[offset];
    const x = index % info.width;
    const y = Math.floor(index / info.width);
    if (hasTwoDimensionalCheckerSignal(x, y, 16, componentMember)
        || hasTwoDimensionalCheckerSignal(x, y, 32, componentMember)) {
      checkerSignals += 1;
    }
  }

  const darkRatio = checkerDarkPixels / componentTail;
  const lightRatio = checkerLightPixels / componentTail;
  const isEnclosedCheckerboard = componentTail >= 128
    && checkerSignals >= 20;
  if (isEnclosedCheckerboard) {
    if (mode === "debug") {
      console.error(JSON.stringify({
        pixels: componentTail,
        darkRatio,
        lightRatio,
        checkerSignals,
        bounds: {
          left: componentLeft,
          top: componentTop,
          right: componentRight,
          bottom: componentBottom,
        },
      }));
    }
    for (let offset = 0; offset < componentTail; offset += 1) {
      visited[componentQueue[offset]] = 1;
    }
  }
  for (let offset = 0; offset < componentTail; offset += 1) {
    componentMember[componentQueue[offset]] = 0;
  }
}

for (let index = 0; index < pixelCount; index += 1) {
  if (!visited[index]) continue;
  const offset = index * 4;
  data[offset] = 255;
  data[offset + 1] = 255;
  data[offset + 2] = 255;
  data[offset + 3] = 0;
}

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await sharp(data, {
  raw: {
    width: info.width,
    height: info.height,
    channels: 4,
  },
})
  .png({ compressionLevel: 9, adaptiveFiltering: true })
  .toFile(outputPath);
