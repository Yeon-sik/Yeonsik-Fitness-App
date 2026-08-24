import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const [sharpModulePath, inputPath, outputPath, scaleText = "0.9"] = process.argv.slice(2);

if (!sharpModulePath || !inputPath || !outputPath) {
  throw new Error(
    "Usage: node normalize-frame-canvas.mjs <sharp-module> <input> <output> [scale]",
  );
}

const sharp = require(sharpModulePath);
const scale = Number(scaleText);
if (!Number.isFinite(scale) || scale <= 0 || scale > 1) {
  throw new Error(`Scale must be greater than 0 and at most 1: ${scaleText}`);
}

const metadata = await sharp(inputPath).metadata();
if (!metadata.width || !metadata.height) {
  throw new Error(`Unable to read image dimensions: ${inputPath}`);
}

const resizedWidth = Math.round(metadata.width * scale);
const resizedHeight = Math.round(metadata.height * scale);
const left = Math.floor((metadata.width - resizedWidth) / 2);
const top = Math.floor((metadata.height - resizedHeight) / 2);
const resized = await sharp(inputPath)
  .resize(resizedWidth, resizedHeight, { fit: "fill" })
  .png()
  .toBuffer();

await fs.mkdir(path.dirname(outputPath), { recursive: true });
await sharp({
  create: {
    width: metadata.width,
    height: metadata.height,
    channels: 4,
    background: { r: 255, g: 255, b: 255, alpha: 0 },
  },
})
  .composite([{ input: resized, left, top }])
  .png({ compressionLevel: 9, adaptiveFiltering: true })
  .toFile(outputPath);

console.log(JSON.stringify({
  input: inputPath,
  output: outputPath,
  canvas: { width: metadata.width, height: metadata.height },
  transform: { scale, left, top },
}));
