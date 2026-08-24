import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const sharpModulePath = process.argv[2];

if (!sharpModulePath) {
  throw new Error("Usage: node build-layer-assets.mjs <absolute-path-to-sharp-module>");
}

const sharp = require(sharpModulePath);
const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(scriptDir, "..");
const config = JSON.parse(
  await fs.readFile(path.join(rootDir, "muscle-layers.json"), "utf8"),
);

const { width, height } = config.canvas;
const palette = [
  "#ef4444",
  "#f97316",
  "#f59e0b",
  "#84cc16",
  "#10b981",
  "#06b6d4",
  "#3b82f6",
  "#6366f1",
  "#8b5cf6",
  "#d946ef",
  "#ec4899",
];

function validateConfig() {
  const ids = new Set();
  for (const layer of config.layers) {
    if (ids.has(layer.id)) {
      throw new Error(`Duplicate muscle layer id: ${layer.id}`);
    }
    ids.add(layer.id);
    if (!["front", "back"].includes(layer.view)) {
      throw new Error(`Unsupported view for ${layer.id}: ${layer.view}`);
    }
    if (!Array.isArray(layer.paths) || layer.paths.length === 0) {
      throw new Error(`Layer has no vector paths: ${layer.id}`);
    }
  }

  for (const [groupId, layerIds] of Object.entries(config.exerciseGroups)) {
    for (const layerId of layerIds) {
      if (!ids.has(layerId)) {
        throw new Error(`Exercise group ${groupId} references missing layer: ${layerId}`);
      }
    }
  }
}

validateConfig();

function styleKey(layer) {
  if (layer.kind === "deep_projection") return "deep";
  if (layer.kind === "functional_region") return "functional";
  if (layer.kind === "anatomical_landmark") return "landmark";
  return "surface";
}

function layerStyle(layer, fillOverride, opacityOverride) {
  const configured = config.highlight.styles?.[styleKey(layer)] ?? {};
  return {
    fill: fillOverride ?? configured.fill ?? config.highlight.color,
    opacity: opacityOverride ?? configured.opacity ?? config.highlight.opacity,
    stroke: configured.stroke ?? config.highlight.stroke,
    dash: configured.dash,
    hatch: configured.hatch === true,
  };
}

function deepPattern(fill, id = "deepProjectionHatch") {
  return `<pattern id="${id}" width="14" height="14" patternUnits="userSpaceOnUse" patternTransform="rotate(18)">
    <rect width="14" height="14" fill="${fill}" fill-opacity="0.18"/>
    <path d="M0 0 V14" stroke="${fill}" stroke-opacity="0.78" stroke-width="4"/>
  </pattern>`;
}

function paintAttributes(layer, patternId, fillOverride, opacityOverride) {
  const style = layerStyle(layer, fillOverride, opacityOverride);
  const fill = style.hatch ? `url(#${patternId})` : style.fill;
  const fillOpacity = style.hatch ? 1 : style.opacity;
  const dash = style.dash ? ` stroke-dasharray="${style.dash}"` : "";
  return `fill="${fill}" fill-opacity="${fillOpacity}" stroke="${style.stroke}" stroke-width="2" stroke-linejoin="round"${dash}`;
}

function isBackground(r, g, b) {
  const min = Math.min(r, g, b);
  const max = Math.max(r, g, b);
  return min >= 238 && max - min <= 14;
}

async function normalizeTransparency(inputPath, outputPath) {
  const { data, info } = await sharp(inputPath)
    .ensureAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });

  const pixelCount = info.width * info.height;
  const visited = new Uint8Array(pixelCount);
  const queue = new Int32Array(pixelCount);
  let head = 0;
  let tail = 0;

  function enqueue(index) {
    if (visited[index]) return;
    const offset = index * 4;
    if (!isBackground(data[offset], data[offset + 1], data[offset + 2])) return;
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

  for (let index = 0; index < pixelCount; index += 1) {
    const offset = index * 4;
    if (visited[index]) {
      data[offset] = 255;
      data[offset + 1] = 255;
      data[offset + 2] = 255;
      data[offset + 3] = 0;
    } else {
      data[offset + 3] = 255;
    }
  }

  await sharp(data, {
    raw: {
      width: info.width,
      height: info.height,
      channels: 4,
    },
  })
    .png()
    .toFile(outputPath);
}

function pathMarkup(layer) {
  const direct = layer.paths.map((d) => `<path d="${d}"/>`).join("");
  if (!layer.mirror) return direct;
  const mirrored = layer.paths
    .map((d) => `<path d="${d}" transform="translate(${width} 0) scale(-1 1)"/>`)
    .join("");
  return direct + mirrored;
}

function overlaySvg(layer, fillOverride, opacityOverride) {
  const style = layerStyle(layer, fillOverride, opacityOverride);
  const patternId = "deepProjectionHatch";
  return Buffer.from(`
    <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <defs>${style.hatch ? deepPattern(style.fill, patternId) : ""}</defs>
      <g ${paintAttributes(layer, patternId, fillOverride, opacityOverride)}>
        ${pathMarkup(layer)}
      </g>
    </svg>
  `);
}

async function renderLayer(layer) {
  const outputDir = path.join(rootDir, "layers", layer.view);
  await fs.mkdir(outputDir, { recursive: true });
  await sharp(overlaySvg(layer))
    .png()
    .toFile(path.join(outputDir, `${layer.id}.png`));
}

async function writeLayeredCanvas(view) {
  const layers = config.layers.filter((layer) => layer.view === view);
  const groups = layers
    .map(
      (layer) => `
      <g id="${layer.id}" data-muscle-id="${layer.id}" data-name-ko="${layer.nameKo}" data-kind="${layer.kind}" style="display:none" ${paintAttributes(layer, "deepProjectionHatch")}>
        ${pathMarkup(layer)}
      </g>`,
    )
    .join("");

  const svg = `<?xml version="1.0" encoding="UTF-8"?>
  <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
    <defs>${deepPattern(config.highlight.styles?.deep?.fill ?? config.highlight.color)}</defs>
    <g id="base"><image href="../source/${view}-master.png" x="0" y="0" width="${width}" height="${height}"/></g>
    <g id="muscle-layers">${groups}
    </g>
  </svg>`;

  await fs.writeFile(path.join(rootDir, "canvas", `${view}-layered.svg`), svg);
}

async function writePreview(view) {
  const layers = config.layers.filter((layer) => layer.view === view);
  const composites = layers.map((layer, index) => ({
    input: overlaySvg(
      layer,
      palette[index % palette.length],
      layer.kind === "deep_projection" ? undefined : 0.46,
    ),
  }));
  await sharp(path.join(rootDir, "source", `${view}-master.png`))
    .composite(composites)
    .png()
    .toFile(path.join(rootDir, "previews", `${view}-anatomy-map.png`));
}

async function removeStaleLayers(view) {
  const outputDir = path.join(rootDir, "layers", view);
  await fs.mkdir(outputDir, { recursive: true });
  const expected = new Set(
    config.layers
      .filter((layer) => layer.view === view)
      .map((layer) => `${layer.id}.png`),
  );
  const entries = await fs.readdir(outputDir, { withFileTypes: true });
  await Promise.all(
    entries
      .filter(
        (entry) =>
          entry.isFile() && entry.name.endsWith(".png") && !expected.has(entry.name),
      )
      .map((entry) => fs.unlink(path.join(outputDir, entry.name))),
  );
}

async function writeGroupPreview(view, groupId) {
  const layerIds = config.exerciseGroups[groupId];
  const viewLayerIds = layerIds.filter((id) =>
    config.layers.some((layer) => layer.id === id && layer.view === view),
  );
  if (viewLayerIds.length === 0) return;

  const composites = viewLayerIds.map((id) => ({
    input: path.join(rootDir, "layers", view, `${id}.png`),
  }));

  await sharp(path.join(rootDir, "source", `${view}-master.png`))
    .composite(composites)
    .png()
    .toFile(path.join(rootDir, "previews", `${view}-${groupId}.png`));
}

await fs.mkdir(path.join(rootDir, "canvas"), { recursive: true });
await fs.mkdir(path.join(rootDir, "previews"), { recursive: true });

for (const view of ["front", "back"]) {
  await normalizeTransparency(
    path.join(rootDir, "source", "raw", `${view}-generated.png`),
    path.join(rootDir, "source", `${view}-master.png`),
  );
}

await Promise.all([removeStaleLayers("front"), removeStaleLayers("back")]);

for (const layer of config.layers) {
  await renderLayer(layer);
}

await Promise.all([
  writeLayeredCanvas("front"),
  writeLayeredCanvas("back"),
  writePreview("front"),
  writePreview("back"),
]);

await Promise.all([
  writeGroupPreview("front", "upper_chest"),
  writeGroupPreview("front", "quads"),
  writeGroupPreview("front", "core_stability"),
  writeGroupPreview("front", "adductors"),
  writeGroupPreview("front", "forearms"),
  writeGroupPreview("back", "lats"),
  writeGroupPreview("back", "mid_back"),
  writeGroupPreview("back", "hamstrings"),
  writeGroupPreview("back", "calves"),
]);

console.log(`Built ${config.layers.length} muscle layers.`);
