#!/usr/bin/env python3
"""
Realign LOAD segments in ELF shared libraries to 16 KB boundaries.

Required for Android 15+ (16 KB page size compatibility).
Usage: python fix_elf_alignment.py lib.so [lib.so ...]
"""

import struct
import sys
import os

ELF_MAGIC = b'\x7fELF'
PT_LOAD = 1
PT_DYNAMIC = 2
PT_NOTE = 4
PT_PHDR = 6
PT_GNU_EH_FRAME = 0x6474e550
PT_GNU_STACK = 0x6474e551
PT_GNU_RELRO = 0x6474e552
PAGE_16K = 16384


class ELFFixer:
    def __init__(self, path):
        self.path = path
        with open(path, 'rb') as f:
            self.data = bytearray(f.read())
        self._parse()

    def _parse(self):
        if self.data[:4] != ELF_MAGIC:
            raise ValueError(f"Not an ELF file: {self.path}")

        self.is_64 = self.data[4] == 2
        elf_class = 64 if self.is_64 else 32

        F = '<Q' if elf_class == 64 else '<I'
        S = 8 if elf_class == 64 else 4

        # Read program headers
        if elf_class == 64:
            self.e_phoff = struct.unpack('<Q', self.data[0x20:0x28])[0]
            self.e_phentsize = struct.unpack('<H', self.data[0x36:0x38])[0]
            self.e_phnum = struct.unpack('<H', self.data[0x38:0x3A])[0]
            self.e_shoff = struct.unpack('<Q', self.data[0x28:0x30])[0]
            self.e_shentsize = struct.unpack('<H', self.data[0x3A:0x3C])[0]
            self.e_shnum = struct.unpack('<H', self.data[0x3C:0x3E])[0]
            self.e_shstrndx = struct.unpack('<H', self.data[0x3E:0x40])[0]
            self.phdr_struct = '<II' + 'QQQQQQ'  # p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align
            self.phdr_size = 56
        else:
            self.e_phoff = struct.unpack('<I', self.data[0x1C:0x20])[0]
            self.e_phentsize = struct.unpack('<H', self.data[0x2A:0x2C])[0]
            self.e_phnum = struct.unpack('<H', self.data[0x2C:0x2E])[0]
            self.e_shoff = struct.unpack('<I', self.data[0x20:0x24])[0]
            self.e_shentsize = struct.unpack('<H', self.data[0x2E:0x30])[0]
            self.e_shnum = struct.unpack('<H', self.data[0x30:0x32])[0]
            self.e_shstrndx = struct.unpack('<H', self.data[0x32:0x34])[0]
            self.phdr_struct = '<IIIIIIII'  # p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align
            self.phdr_size = 32

        # Read program headers
        self.phdrs = []
        for i in range(self.e_phnum):
            off = self.e_phoff + i * self.phdr_size
            raw = self.data[off:off + self.phdr_size]
            if self.is_64:
                p_type, p_flags, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align = struct.unpack_from(self.phdr_struct, raw, 0)
            else:
                p_type, p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_flags, p_align = struct.unpack_from(self.phdr_struct, raw, 0)
            self.phdrs.append({
                'type': p_type, 'flags': p_flags, 'offset': p_offset,
                'vaddr': p_vaddr, 'paddr': p_paddr,
                'filesz': p_filesz, 'memsz': p_memsz, 'align': p_align,
                'idx': i, 'raw': raw
            })

        # Read section headers
        self.shdrs = []
        self.has_sections = (self.e_shoff > 0 and self.e_shnum > 0)
        if self.has_sections:
            for i in range(self.e_shnum):
                off = self.e_shoff + i * self.e_shentsize
                if self.is_64:
                    raw = self.data[off:off + 64]
                    sh_name, sh_type, sh_flags, sh_addr, sh_offset, sh_size, _, _, _, _ = struct.unpack_from('<IIQQQQII', raw, 0)
                else:
                    raw = self.data[off:off + 40]
                    sh_name, sh_type, sh_flags, sh_addr, sh_offset, sh_size, _, _, _ = struct.unpack_from('<IIIIIIII', raw, 0)
                self.shdrs.append({
                    'name': sh_name, 'type': sh_type, 'flags': sh_flags,
                    'addr': sh_addr, 'offset': sh_offset, 'size': sh_size,
                    'idx': i, 'raw': raw
                })

    def _find_load_segments(self):
        return [p for p in self.phdrs if p['type'] == PT_LOAD]

    def _update_phdr_field(self, phdr_idx, field_name, new_value, field_size):
        """Update a field in a program header in the data array."""
        off = self.e_phoff + phdr_idx * self.phdr_size

        # Field offsets within the program header
        if self.is_64:
            field_offsets = {
                'type': 0, 'flags': 4, 'offset': 8, 'vaddr': 16,
                'paddr': 24, 'filesz': 32, 'memsz': 40, 'align': 48
            }
        else:
            field_offsets = {
                'type': 0, 'offset': 4, 'vaddr': 8, 'paddr': 12,
                'filesz': 16, 'memsz': 20, 'flags': 24, 'align': 28
            }

        foff = off + field_offsets[field_name]
        self.data[foff:foff + field_size] = struct.pack('<I' if field_size == 4 else '<Q', new_value)
        self.phdrs[phdr_idx][field_name] = new_value

    def _update_shdr_field(self, shdr_idx, field_name, new_value):
        """Update a field in a section header."""
        off = self.e_shoff + shdr_idx * self.e_shentsize
        if self.is_64:
            field_offsets = {
                'name': 0, 'type': 4, 'flags': 8, 'addr': 16,
                'offset': 24, 'size': 32, 'link': 40, 'info': 44,
                'addralign': 48, 'entsize': 52
            }
            fmt = '<I' if field_name in ('name', 'type', 'link', 'info') else '<Q'
        else:
            field_offsets = {
                'name': 0, 'type': 4, 'flags': 8, 'addr': 12,
                'offset': 16, 'size': 20, 'link': 24, 'info': 28,
                'addralign': 32, 'entsize': 36
            }
            fmt = '<I'

        foff = off + field_offsets[field_name]
        fsize = 4 if field_name in ('name', 'type', 'link', 'info') else (8 if self.is_64 else 4)
        if field_name in ('addr', 'offset', 'size', 'addralign', 'entsize') and not self.is_64:
            fsize = 4
        self.data[foff:foff + fsize] = struct.pack(fmt, new_value)
        self.shdrs[shdr_idx][field_name] = new_value

    def _insert_padding(self, offset, size):
        """Insert padding bytes at a given offset, shifting all subsequent data."""
        pad = b'\x00' * size
        self.data[offset:offset] = pad
        # Update file size tracking - we'll just use len(self.data)

    def fix(self):
        load_segs = self._find_load_segments()
        if not load_segs:
            return False

        modified = False
        total_shift = 0

        # Process segments in order
        for seg in load_segs:
            if total_shift > 0:
                # Adjust for previous shifts in this file
                seg['offset'] += total_shift

            # Calculate p_vaddr % PAGE_16K
            vaddr_mod = seg['vaddr'] % PAGE_16K
            # We need: seg['offset'] % PAGE_16K == vaddr_mod
            offset_mod = seg['offset'] % PAGE_16K

            if seg['align'] >= PAGE_16K and offset_mod == vaddr_mod:
                continue  # Already good

            modified = True
            old_offset = seg['offset']

            # Calculate new offset: smallest value >= old_offset such that offset % PAGE_16K == vaddr_mod
            # And also >= end of previous load segment data
            if old_offset % PAGE_16K > vaddr_mod:
                new_offset = old_offset + (PAGE_16K - offset_mod) + vaddr_mod
            else:
                new_offset = old_offset - offset_mod + vaddr_mod

            # Ensure new_offset >= previous segment's data end
            if seg['idx'] > 0:
                prev_seg = self.phdrs[load_segs[0]['idx']] if seg == load_segs[0] else \
                    [p for p in self.phdrs if p['type'] == PT_LOAD and p['idx'] < seg['idx']][-1]
                min_offset = prev_seg['offset'] + prev_seg['filesz']
                # But if prev_seg had a different vaddr_mod, we need to account for the gap needed
                if new_offset < min_offset:
                    new_offset = min_offset
                    # Recalculate to satisfy congruence
                    diff = new_offset % PAGE_16K - vaddr_mod
                    if diff < 0:
                        new_offset += -diff
                    elif diff > 0:
                        new_offset += PAGE_16K - diff

            insert_size = new_offset - old_offset
            if insert_size < 0:
                # Can't shift backward - need to find next aligned position
                new_offset = old_offset + (PAGE_16K - offset_mod) + vaddr_mod
                insert_size = new_offset - old_offset
                if insert_size < 0:
                    new_offset = old_offset + PAGE_16K
                    insert_size = PAGE_16K

            if insert_size > 0:
                # Insert padding between previous segment's data and this segment
                padding_start = old_offset + total_shift
                print(f"  Inserting {insert_size} bytes of padding at offset 0x{padding_start:x}")
                self._insert_padding(padding_start, insert_size)
                total_shift += insert_size
                new_offset += total_shift - insert_size  # Hmm, need to track this correctly

            # Update p_align
            if seg['align'] < PAGE_16K:
                self._update_phdr_field(seg['idx'], 'align', PAGE_16K, 8 if self.is_64 else 4)
                print(f"  Updated p_align: 0x{seg['align']:x} -> 0x{PAGE_16K:x}")

        # Actually, the simplistic approach above is flawed for multiple segments.
        # Let me recalculate properly.

        # Re-read: simpler approach - process all load segments in one pass
        return modified


def fix_elf_simple(path):
    """
    Simple approach: For each LOAD segment, if not 16K-aligned,
    insert padding before it and update p_align.
    """
    with open(path, 'rb') as f:
        data = bytearray(f.read())

    if data[:4] != ELF_MAGIC:
        print(f"  Skipping (not ELF): {path}")
        return False

    is_64 = data[4] == 2

    if is_64:
        e_phoff = struct.unpack('<Q', data[0x20:0x28])[0]
        e_phentsize = struct.unpack('<H', data[0x36:0x38])[0]
        e_phnum = struct.unpack('<H', data[0x38:0x3A])[0]
        e_shoff = struct.unpack('<Q', data[0x28:0x30])[0]
        e_shentsize = struct.unpack('<H', data[0x3A:0x3C])[0]
        e_shnum = struct.unpack('<H', data[0x3C:0x3E])[0]
        e_shstrndx = struct.unpack('<H', data[0x3E:0x40])[0]
        phdr_size = 56

        def read_phdr(off):
            fields = struct.unpack_from('<IIQQQQQQ', data, off)
            return {'type': fields[0], 'flags': fields[1], 'offset': fields[2],
                    'vaddr': fields[3], 'filesz': fields[5], 'memsz': fields[6],
                    'align': fields[7]}

        def write_phdr_offset(off, phoff):
            struct.pack_into('<Q', data, off + 8, phoff)

        def write_phdr_align(off, align):
            struct.pack_into('<Q', data, off + 48, align)

        def read_shdr(off):
            fields = struct.unpack_from('<IIQQQQII', data, off)
            return {'name': fields[0], 'type': fields[1], 'flags': fields[2],
                    'addr': fields[3], 'offset': fields[4], 'size': fields[5]}

        def write_shdr_offset(off, shoff):
            struct.pack_into('<Q', data, off + 24, shoff)
    else:
        e_phoff = struct.unpack('<I', data[0x1C:0x20])[0]
        e_phentsize = struct.unpack('<H', data[0x2A:0x2C])[0]
        e_phnum = struct.unpack('<H', data[0x2C:0x2E])[0]
        e_shoff = struct.unpack('<I', data[0x20:0x24])[0]
        e_shentsize = struct.unpack('<H', data[0x2E:0x30])[0]
        e_shnum = struct.unpack('<H', data[0x30:0x32])[0]
        e_shstrndx = struct.unpack('<H', data[0x32:0x34])[0]
        phdr_size = 32

        def read_phdr(off):
            fields = struct.unpack_from('<IIIIIIII', data, off)
            return {'type': fields[0], 'offset': fields[1],
                    'vaddr': fields[2], 'flags': fields[6], 'filesz': fields[4],
                    'memsz': fields[5], 'align': fields[7]}

        def write_phdr_offset(off, phoff):
            struct.pack_into('<I', data, off + 4, phoff)

        def write_phdr_align(off, align):
            struct.pack_into('<I', data, off + 28, align)

        def read_shdr(off):
            fields = struct.unpack_from('<IIIIIIII', data, off)
            return {'name': fields[0], 'type': fields[1], 'flags': fields[2],
                    'addr': fields[3], 'offset': fields[4], 'size': fields[5]}

        def write_shdr_offset(off, shoff):
            struct.pack_into('<I', data, off + 16, shoff)

    # Read program headers
    phdrs = []
    for i in range(e_phnum):
        off = e_phoff + i * phdr_size
        ph = read_phdr(off)
        ph['raw_off'] = off
        ph['idx'] = i
        phdrs.append(ph)

    load_segs = [p for p in phdrs if p['type'] == PT_LOAD]
    if not load_segs:
        return False

    modified = False
    total_shift = 0

    # We track: for each byte range, we need to update p_offset of LOAD segments.
    # Insert padding between load segments so each starts at a 16K-aligned offset.
    for i, seg in enumerate(load_segs):
        # Apply any accumulated shift to the segment's offset (in our tracking)
        adj_offset = seg['offset'] + total_shift

        vaddr_mod = seg['vaddr'] % PAGE_16K
        offset_mod = adj_offset % PAGE_16K

        if seg['align'] >= PAGE_16K and offset_mod == vaddr_mod:
            continue

        modified = True

        # Calculate aligned offset: smallest value >= adj_offset with offset % PAGE_16K == vaddr_mod
        if offset_mod > vaddr_mod:
            aligned_offset = adj_offset + (PAGE_16K - offset_mod) + vaddr_mod
        else:
            aligned_offset = adj_offset + (vaddr_mod - offset_mod)

        # Ensure we're past the end of the previous segment
        if i > 0:
            prev = load_segs[i - 1]
            prev_end = prev['offset'] + prev['filesz'] + total_shift
            if aligned_offset < prev_end:
                # Round up past prev_end to satisfy congruence
                aligned_offset = ((prev_end + PAGE_16K - 1) // PAGE_16K) * PAGE_16K + vaddr_mod

        shift_amount = aligned_offset - adj_offset

        if shift_amount > 0:
            # Insert padding at the start of this segment
            insert_pos = adj_offset  # which is seg['offset'] + total_shift
            padding = b'\x00' * shift_amount
            data[insert_pos:insert_pos] = padding
            total_shift += shift_amount
            print(f"  Inserted {shift_amount} bytes padding at 0x{insert_pos:x} to align LOAD[{seg['idx']}]")

        # Now update p_offset
        new_offset = seg['offset'] + total_shift
        write_phdr_offset(seg['raw_off'], new_offset)
        print(f"  LOAD[{seg['idx']}]: p_offset 0x{seg['offset']:x} -> 0x{new_offset:x}")

        # Update p_align if needed
        if seg['align'] < PAGE_16K:
            write_phdr_align(seg['raw_off'], PAGE_16K)
            print(f"  LOAD[{seg['idx']}]: p_align 0x{seg['align']:x} -> 0x{PAGE_16K:x}")

    if not modified:
        return False

    # Update other PHDR offsets that point into shifted data
    for ph in phdrs:
        if ph['type'] in (PT_LOAD, PT_PHDR):
            continue
        if ph['type'] == PT_DYNAMIC or ph['type'] == PT_GNU_RELRO or ph['type'] == PT_NOTE:
            # These might be within LOAD segments - check and update their p_offset
            for seg in load_segs:
                seg_start = seg['offset']
                seg_end = seg_start + seg['filesz']
                ph_off = ph['offset']
                if seg_start <= ph_off < seg_end:
                    # Track if this segment was shifted (by looking at its current vs tracked offset)
                    loop_off = None
                    for s in load_segs:
                        if s['idx'] == seg['idx']:
                            loop_off = s['offset']
                            break
                    # Actually, we need the NEW offset. Let's re-read it.
                    # We already wrote the new offset to the data.
                    # For simplicity, compute: we know the total_shift.
                    # If ph_off >= seg['offset'] (old offset) but we shifted by total_shift...
                    # This is getting complex. Let's just re-read the new segment offset.
                    new_seg = read_phdr(seg['raw_off'])
                    new_seg_off = new_seg['offset']
                    shift = new_seg_off - seg['offset']
                    new_ph_off = ph_off + shift
                    write_phdr_offset(ph['raw_off'], new_ph_off)
                    print(f"  PHDR[{ph['idx']}]: p_offset 0x{ph_off:x} -> 0x{new_ph_off:x} (inside shifted LOAD)")
                    break

    # Update section headers
    if e_shoff > 0 and e_shnum > 0:
        # Update e_shoff: add total_shift if section header table was after shifted data
        if e_shoff >= load_segs[0]['offset'] + load_segs[0]['filesz']:
            # The section headers are after the first load segment
            new_shoff = e_shoff + total_shift
            if is_64:
                struct.pack_into('<Q', data, 0x28, new_shoff)
            else:
                struct.pack_into('<I', data, 0x20, new_shoff)
            print(f"  e_shoff: 0x{e_shoff:x} -> 0x{new_shoff:x}")

        # Update sh_offset for sections after the shift point
        for i in range(e_shnum):
            hdr_off = e_shoff + i * e_shentsize
            sh = read_shdr(hdr_off)
            # Check if this section was shifted
            # Flag 0x2 means ALLOC (in memory), so these are sections that will be loaded
            # But we care about file offset, not vaddr
            # Determine if this section is in shifted data
            is_shifted = False
            for seg in load_segs:
                # Re-read seg offset
                s = read_phdr(seg['raw_off'])
                s_start = s['offset']
                s_end = s_start + seg['filesz']
                if s_start <= sh['offset'] < s_end:
                    # This section is inside this segment - check if it was shifted
                    if s_start != seg['offset']:  # Segment was shifted
                        shift = s_start - seg['offset']
                        new_soff = sh['offset'] + shift
                        write_shdr_offset(hdr_off, new_soff)
                        print(f"  .section[{i}]: sh_offset 0x{sh['offset']:x} -> 0x{new_soff:x}")
                        is_shifted = True
                        break

    # Write the modified file
    orig_size = len(data) - total_shift
    print(f"  File size: {orig_size} -> {len(data)} (+{total_shift} bytes)")
    with open(path, 'wb') as f:
        f.write(data)

    return True


def main():
    files = sys.argv[1:]
    if not files:
        print("Usage: python fix_elf_alignment.py lib.so [lib.so ...]")
        sys.exit(1)

    for path in files:
        if not os.path.exists(path):
            print(f"Not found: {path}")
            continue

        print(f"Processing: {path}")
        try:
            if fix_elf_simple(path):
                print(f"  => FIXED")
            else:
                print(f"  => Already aligned")
        except Exception as e:
            print(f"  => ERROR: {e}")


if __name__ == '__main__':
    main()
