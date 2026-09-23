import { describe, expect, it } from 'vitest';
import { TAG, child, nbt, readNbt, utf8Decode, utf8Encode, writeNbt } from './nbt';

describe('nbt', () => {
  it('round-trips every supported tag type', () => {
    const root = nbt.compound([
      ['b', nbt.byte(-5)],
      ['s', nbt.short(-1234)],
      ['i', nbt.int(-123456789)],
      ['l', nbt.long(-9007199254740993n)],
      ['f', nbt.float(1.5)],
      ['d', nbt.double(Math.PI)],
      ['str', nbt.string('Казино ✓')],
      ['list', nbt.list(TAG.int, [nbt.int(1), nbt.int(-1)])],
      ['empty', nbt.list(TAG.compound, [])],
      ['nested', nbt.compound({ x: nbt.intArray([1, 2, 3]) })],
    ]);
    const bytes = writeNbt(root, 'root');
    const back = readNbt(bytes);
    expect(back.name).toBe('root');
    expect(back.tag).toEqual(root);
  });

  it('is little-endian with the Bedrock root header', () => {
    const bytes = writeNbt(nbt.compound([['v', nbt.int(1)]]));
    // compound tag, empty name (u16 LE 0), int tag, name "v", 1 as i32 LE, end
    expect([...bytes]).toEqual([10, 0, 0, 3, 1, 0, 0x76, 1, 0, 0, 0, 0]);
  });

  it('rejects mixed list element types and trailing bytes', () => {
    expect(() => writeNbt(nbt.list(TAG.int, [nbt.byte(1)]))).toThrow();
    const bytes = writeNbt(nbt.compound([]));
    expect(() => readNbt(new Uint8Array([...bytes, 0]))).toThrow();
  });

  it('encodes UTF-8 like the platform does', () => {
    for (const s of ['', 'abc', 'фишка', '€', '😀']) expect(utf8Decode(new Uint8Array(utf8Encode(s)))).toBe(s);
    expect(utf8Encode('é')).toEqual([0xc3, 0xa9]);
  });

  it('child() finds compound entries', () => {
    const c = nbt.compound({ a: nbt.int(7) });
    expect(child(c, 'a')).toEqual(nbt.int(7));
    expect(child(c, 'b')).toBeUndefined();
  });
});
