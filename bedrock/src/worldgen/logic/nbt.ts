/**
 * Minimal little-endian NBT (Bedrock flavour) writer + reader. PURE.
 * Used to generate `.mcstructure` templates (tools/gen-structures.mjs) and to verify them in tests.
 * Compound entries keep insertion order (written as given), so output is deterministic.
 */

export const TAG = {
  end: 0,
  byte: 1,
  short: 2,
  int: 3,
  long: 4,
  float: 5,
  double: 6,
  byteArray: 7,
  string: 8,
  list: 9,
  compound: 10,
  intArray: 11,
  longArray: 12,
} as const;

export type Tag =
  | { type: typeof TAG.byte; value: number }
  | { type: typeof TAG.short; value: number }
  | { type: typeof TAG.int; value: number }
  | { type: typeof TAG.long; value: bigint }
  | { type: typeof TAG.float; value: number }
  | { type: typeof TAG.double; value: number }
  | { type: typeof TAG.string; value: string }
  | { type: typeof TAG.list; elementType: number; value: Tag[] }
  | { type: typeof TAG.compound; value: [string, Tag][] }
  | { type: typeof TAG.intArray; value: number[] };

export const nbt = {
  byte: (v: number): Tag => ({ type: TAG.byte, value: v }),
  short: (v: number): Tag => ({ type: TAG.short, value: v }),
  int: (v: number): Tag => ({ type: TAG.int, value: v }),
  long: (v: bigint): Tag => ({ type: TAG.long, value: v }),
  float: (v: number): Tag => ({ type: TAG.float, value: v }),
  double: (v: number): Tag => ({ type: TAG.double, value: v }),
  string: (v: string): Tag => ({ type: TAG.string, value: v }),
  list: (elementType: number, value: Tag[]): Tag => ({ type: TAG.list, elementType, value }),
  compound: (entries: Record<string, Tag> | [string, Tag][]): Tag => ({
    type: TAG.compound,
    value: Array.isArray(entries) ? entries : Object.entries(entries),
  }),
  intArray: (v: number[]): Tag => ({ type: TAG.intArray, value: v }),
};

/** UTF-8 encoding without TextEncoder (not declared in the ES2023 lib). */
export function utf8Encode(s: string): number[] {
  const out: number[] = [];
  for (const ch of s) {
    const c = ch.codePointAt(0) ?? 0;
    if (c < 0x80) out.push(c);
    else if (c < 0x800) out.push(0xc0 | (c >> 6), 0x80 | (c & 63));
    else if (c < 0x10000) out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    else out.push(0xf0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
  }
  return out;
}

export function utf8Decode(b: Uint8Array): string {
  let s = '';
  for (let i = 0; i < b.length; ) {
    const c = b[i++] as number;
    let cp: number;
    if (c < 0x80) cp = c;
    else if (c < 0xe0) cp = ((c & 31) << 6) | ((b[i++] as number) & 63);
    else if (c < 0xf0) cp = ((c & 15) << 12) | (((b[i++] as number) & 63) << 6) | ((b[i++] as number) & 63);
    else cp = ((c & 7) << 18) | (((b[i++] as number) & 63) << 12) | (((b[i++] as number) & 63) << 6) | ((b[i++] as number) & 63);
    s += String.fromCodePoint(cp);
  }
  return s;
}

class Writer {
  private buf = new Uint8Array(1024);
  private view = new DataView(this.buf.buffer);
  private pos = 0;

  private ensure(n: number): void {
    if (this.pos + n <= this.buf.length) return;
    let size = this.buf.length * 2;
    while (size < this.pos + n) size *= 2;
    const next = new Uint8Array(size);
    next.set(this.buf);
    this.buf = next;
    this.view = new DataView(next.buffer);
  }

  u8(v: number): void {
    this.ensure(1);
    this.view.setUint8(this.pos, v & 0xff);
    this.pos += 1;
  }
  i8(v: number): void {
    this.ensure(1);
    this.view.setInt8(this.pos, v);
    this.pos += 1;
  }
  i16(v: number): void {
    this.ensure(2);
    this.view.setInt16(this.pos, v, true);
    this.pos += 2;
  }
  u16(v: number): void {
    this.ensure(2);
    this.view.setUint16(this.pos, v, true);
    this.pos += 2;
  }
  i32(v: number): void {
    this.ensure(4);
    this.view.setInt32(this.pos, v, true);
    this.pos += 4;
  }
  i64(v: bigint): void {
    this.ensure(8);
    this.view.setBigInt64(this.pos, v, true);
    this.pos += 8;
  }
  f32(v: number): void {
    this.ensure(4);
    this.view.setFloat32(this.pos, v, true);
    this.pos += 4;
  }
  f64(v: number): void {
    this.ensure(8);
    this.view.setFloat64(this.pos, v, true);
    this.pos += 8;
  }
  str(s: string): void {
    const bytes = utf8Encode(s);
    if (bytes.length > 0xffff) throw new Error('NBT string too long');
    this.u16(bytes.length);
    this.ensure(bytes.length);
    this.buf.set(bytes, this.pos);
    this.pos += bytes.length;
  }
  result(): Uint8Array {
    return this.buf.slice(0, this.pos);
  }
}

function writePayload(w: Writer, tag: Tag): void {
  switch (tag.type) {
    case TAG.byte:
      return w.i8(tag.value);
    case TAG.short:
      return w.i16(tag.value);
    case TAG.int:
      return w.i32(tag.value);
    case TAG.long:
      return w.i64(tag.value);
    case TAG.float:
      return w.f32(tag.value);
    case TAG.double:
      return w.f64(tag.value);
    case TAG.string:
      return w.str(tag.value);
    case TAG.list:
      w.u8(tag.elementType);
      w.i32(tag.value.length);
      for (const el of tag.value) {
        if (el.type !== tag.elementType) throw new Error(`NBT list of ${tag.elementType} contains tag ${el.type}`);
        writePayload(w, el);
      }
      return;
    case TAG.compound:
      for (const [name, v] of tag.value) {
        w.u8(v.type);
        w.str(name);
        writePayload(w, v);
      }
      w.u8(TAG.end);
      return;
    case TAG.intArray:
      w.i32(tag.value.length);
      for (const v of tag.value) w.i32(v);
      return;
  }
}

/** Serialize a named root tag (Bedrock `.mcstructure` uses a compound named ""). */
export function writeNbt(root: Tag, name = ''): Uint8Array {
  const w = new Writer();
  w.u8(root.type);
  w.str(name);
  writePayload(w, root);
  return w.result();
}

class Reader {
  private readonly view: DataView;
  pos = 0;
  constructor(private readonly buf: Uint8Array) {
    this.view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  }
  u8(): number {
    return this.view.getUint8(this.pos++);
  }
  i8(): number {
    return this.view.getInt8(this.pos++);
  }
  i16(): number {
    const v = this.view.getInt16(this.pos, true);
    this.pos += 2;
    return v;
  }
  u16(): number {
    const v = this.view.getUint16(this.pos, true);
    this.pos += 2;
    return v;
  }
  i32(): number {
    const v = this.view.getInt32(this.pos, true);
    this.pos += 4;
    return v;
  }
  i64(): bigint {
    const v = this.view.getBigInt64(this.pos, true);
    this.pos += 8;
    return v;
  }
  f32(): number {
    const v = this.view.getFloat32(this.pos, true);
    this.pos += 4;
    return v;
  }
  f64(): number {
    const v = this.view.getFloat64(this.pos, true);
    this.pos += 8;
    return v;
  }
  str(): string {
    const n = this.u16();
    const s = utf8Decode(this.buf.subarray(this.pos, this.pos + n));
    this.pos += n;
    return s;
  }
  get done(): boolean {
    return this.pos >= this.buf.length;
  }
}

function readPayload(r: Reader, type: number): Tag {
  switch (type) {
    case TAG.byte:
      return nbt.byte(r.i8());
    case TAG.short:
      return nbt.short(r.i16());
    case TAG.int:
      return nbt.int(r.i32());
    case TAG.long:
      return nbt.long(r.i64());
    case TAG.float:
      return nbt.float(r.f32());
    case TAG.double:
      return nbt.double(r.f64());
    case TAG.string:
      return nbt.string(r.str());
    case TAG.list: {
      const elementType = r.u8();
      const n = r.i32();
      const value: Tag[] = [];
      for (let i = 0; i < n; i++) value.push(readPayload(r, elementType));
      return { type: TAG.list, elementType, value };
    }
    case TAG.compound: {
      const value: [string, Tag][] = [];
      for (;;) {
        const t = r.u8();
        if (t === TAG.end) break;
        const name = r.str();
        value.push([name, readPayload(r, t)]);
      }
      return { type: TAG.compound, value };
    }
    case TAG.intArray: {
      const n = r.i32();
      const value: number[] = [];
      for (let i = 0; i < n; i++) value.push(r.i32());
      return nbt.intArray(value);
    }
    default:
      throw new Error(`unsupported NBT tag type ${type}`);
  }
}

/** Parse a named root tag. Throws on trailing bytes or truncated input. */
export function readNbt(bytes: Uint8Array): { name: string; tag: Tag } {
  const r = new Reader(bytes);
  const type = r.u8();
  const name = r.str();
  const tag = readPayload(r, type);
  if (!r.done) throw new Error(`trailing bytes after NBT root (${bytes.length - r.pos})`);
  return { name, tag };
}

/** Convenience: compound child lookup (tests / validation). */
export function child(tag: Tag, name: string): Tag | undefined {
  if (tag.type !== TAG.compound) return undefined;
  return tag.value.find(([n]) => n === name)?.[1];
}
