// Copy build/BP + build/RP into a local Minecraft "com.mojang" folder as development packs
// (reloadable with /reload without re-importing). Run `npm run build:dev` first.
// Set MC_COM_MOJANG to override the target, e.g.
//   Windows (GDK, 1.21.120+): %APPDATA%\Minecraft Bedrock\Users\Shared\games\com.mojang
//   Windows (legacy UWP):     %LOCALAPPDATA%\Packages\Microsoft.MinecraftUWP_8wekyb3d8bbwe\LocalState\games\com.mojang
//   Dedicated server:         <bds>/  (uses development_*_packs next to the server)
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { PACK, ROOT } from './lib.mjs';

const candidates = [
  process.env.MC_COM_MOJANG,
  process.env.APPDATA && path.join(process.env.APPDATA, 'Minecraft Bedrock', 'Users', 'Shared', 'games', 'com.mojang'),
  process.env.LOCALAPPDATA &&
    path.join(process.env.LOCALAPPDATA, 'Packages', 'Microsoft.MinecraftUWP_8wekyb3d8bbwe', 'LocalState', 'games', 'com.mojang'),
  path.join(os.homedir(), '.local', 'share', 'mcpelauncher', 'games', 'com.mojang'),
].filter(Boolean);

const target = candidates.find((c) => fs.existsSync(c));
if (!target) {
  console.error('com.mojang folder not found. Set MC_COM_MOJANG. Tried:\n  ' + candidates.join('\n  '));
  process.exit(1);
}
for (const [kind, dir] of [
  ['BP', 'development_behavior_packs'],
  ['RP', 'development_resource_packs'],
]) {
  const src = path.join(ROOT, 'build', kind);
  if (!fs.existsSync(src)) {
    console.error(`build/${kind} missing - run npm run build:dev first`);
    process.exit(1);
  }
  const dst = path.join(target, dir, `${PACK.name}_${kind}`);
  fs.rmSync(dst, { recursive: true, force: true });
  fs.cpSync(src, dst, { recursive: true });
  console.log(`deployed ${kind} -> ${dst}`);
}
