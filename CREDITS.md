# Credits

Burmaldaholic is MIT-licensed (see `LICENSE`). It includes third-party work under the licences below.

## Structures

| What | Used for | Author / source | Licence |
|---|---|---|---|
| "Piglin Manor" (`piglin_manor_2.nbt`) from **Bygone Nether** | The building shell of the Piglin Parlor (Nether casino): rotated, widened by two wall bays per side, modded blocks replaced by vanilla ones, interior refitted as a casino | izofar — https://github.com/izofar/bygone-nether (branch `1.18.2`, `data/bygonenether/structures/piglin_manor/`); continued as Eternal Nether by Fuzss (MIT) | MIT, Copyright (c) 2022 izofar |

The original template is kept at `java/src/main/blueprints/parlor/piglin_manor_2.nbt`; the conversion is
`java/tools/gen_parlor_shell.py`, which generates `PiglinManorShell.java`. The full MIT licence text ships inside the
mod jar at `META-INF/licenses/bygone-nether-MIT.txt`.
