import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const [sharpModulePath, ...imagePaths] = process.argv.slice(2);

if (!sharpModulePath || imagePaths.length === 0) {
  throw new Error(
    "Usage: node inspect-image-assets.mjs <sharp-module> <image> [image ...]",
  );
}

const sharp = require(sharpModulePath);
const report = [];

for (const imagePath of imagePaths) {
  const { data, info } = await sharp(imagePath)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  let left = info.width;
  let top = info.height;
  let right = -1;
  let bottom = -1;
  let visiblePixels = 0;
  const colorCounts = new Map();

  for (let y = 0; y < info.height; y += 1) {
    for (let x = 0; x < info.width; x += 1) {
      const alpha = data[(y * info.width + x) * 4 + 3];
      if (alpha === 0) continue;
      const offset = (y * info.width + x) * 4;
      const colorKey = `${data[offset]},${data[offset + 1]},${data[offset + 2]},${alpha}`;
      colorCounts.set(colorKey, (colorCounts.get(colorKey) ?? 0) + 1);
      visiblePixels += 1;
      left = Math.min(left, x);
      top = Math.min(top, y);
      right = Math.max(right, x);
      bottom = Math.max(bottom, y);
    }
  }

  report.push({
    file: path.normalize(imagePath),
    width: info.width,
    height: info.height,
    alphaBounds: visiblePixels === 0 ? null : { left, top, right, bottom },
    visiblePixels,
    commonColors: [...colorCounts.entries()]
      .sort((leftEntry, rightEntry) => rightEntry[1] - leftEntry[1])
      .slice(0, 8)
      .map(([rgba, count]) => ({ rgba, count })),
  });
}

console.log(JSON.stringify(report, null, 2));
