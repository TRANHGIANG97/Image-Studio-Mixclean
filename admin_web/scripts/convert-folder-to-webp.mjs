import fs from 'node:fs/promises';
import path from 'node:path';
import sharp from 'sharp';

const INPUT_DIR = process.argv[2];
const QUALITY = Number(process.argv[3] ?? 82);
const MAX_EDGE = Number(process.argv[4] ?? 0);

if (!INPUT_DIR) {
  console.error('Usage: node scripts/convert-folder-to-webp.mjs <folder> [quality] [maxEdge]');
  process.exit(1);
}

const sourceDir = path.resolve(INPUT_DIR);
const imagePattern = /\.(png|jpe?g)$/i;

async function convertFile(filePath) {
  const ext = path.extname(filePath);
  const outputPath = filePath.replace(new RegExp(`${ext}$`, 'i'), '.webp');

  let pipeline = sharp(filePath);
  const metadata = await pipeline.metadata();

  if (MAX_EDGE > 0) {
    const width = metadata.width ?? 0;
    const height = metadata.height ?? 0;
    const longest = Math.max(width, height);
    if (longest > MAX_EDGE) {
      pipeline = pipeline.resize({
        width: width >= height ? MAX_EDGE : undefined,
        height: height > width ? MAX_EDGE : undefined,
        fit: 'inside',
        withoutEnlargement: true,
      });
    }
  }

  await pipeline
    .webp({ quality: QUALITY, effort: 4 })
    .toFile(outputPath);

  const before = (await fs.stat(filePath)).size;
  const after = (await fs.stat(outputPath)).size;
  await fs.unlink(filePath);

  return { file: path.basename(outputPath), before, after };
}

const entries = await fs.readdir(sourceDir);
const files = entries
  .filter((name) => imagePattern.test(name))
  .map((name) => path.join(sourceDir, name));

if (files.length === 0) {
  console.log('No PNG/JPEG files found.');
  process.exit(0);
}

let totalBefore = 0;
let totalAfter = 0;

for (const filePath of files) {
  const result = await convertFile(filePath);
  totalBefore += result.before;
  totalAfter += result.after;
  const saved = ((1 - result.after / result.before) * 100).toFixed(1);
  console.log(
    `${result.file}: ${(result.before / 1024).toFixed(0)} KB -> ${(result.after / 1024).toFixed(0)} KB (-${saved}%)`,
  );
}

console.log(
  `\nDone: ${files.length} files, ${(totalBefore / 1024 / 1024).toFixed(2)} MB -> ${(totalAfter / 1024 / 1024).toFixed(2)} MB (-${((1 - totalAfter / totalBefore) * 100).toFixed(1)}%)`,
);
