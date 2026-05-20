import sys
import struct

def check_elf_alignment(filepath):
    print(f"Checking ELF alignment for: {filepath}")
    with open(filepath, 'rb') as f:
        magic = f.read(4)
        if magic != b'\x7fELF':
            print("Not a valid ELF file")
            return
        
        # 1 = 32-bit, 2 = 64-bit
        elf_class = ord(f.read(1))
        f.seek(0)
        data = f.read()

    if elf_class == 2:  # 64-bit ELF
        # Read e_phoff, e_phentsize, e_phnum from ELF header
        e_phoff = struct.unpack('<Q', data[32:40])[0]
        e_phentsize = struct.unpack('<H', data[54:56])[0]
        e_phnum = struct.unpack('<H', data[56:58])[0]
        
        print(f"ELF64: phoff={e_phoff}, phentsize={e_phentsize}, phnum={e_phnum}")
        
        for i in range(e_phnum):
            offset = e_phoff + i * e_phentsize
            p_type = struct.unpack('<I', data[offset:offset+4])[0]
            if p_type == 1:  # PT_LOAD
                p_align = struct.unpack('<Q', data[offset+48:offset+56])[0]
                p_vaddr = struct.unpack('<Q', data[offset+16:offset+24])[0]
                print(f"  LOAD Segment {i}: vaddr=0x{p_vaddr:x}, alignment=0x{p_align:x} ({p_align} bytes)")
    
    elif elf_class == 1:  # 32-bit ELF
        # Read e_phoff, e_phentsize, e_phnum from ELF header
        e_phoff = struct.unpack('<I', data[28:32])[0]
        e_phentsize = struct.unpack('<H', data[42:44])[0]
        e_phnum = struct.unpack('<H', data[44:46])[0]
        
        print(f"ELF32: phoff={e_phoff}, phentsize={e_phentsize}, phnum={e_phnum}")
        
        for i in range(e_phnum):
            offset = e_phoff + i * e_phentsize
            p_type = struct.unpack('<I', data[offset:offset+4])[0]
            if p_type == 1:  # PT_LOAD
                p_align = struct.unpack('<I', data[offset+28:offset+32])[0]
                p_vaddr = struct.unpack('<I', data[offset+8:offset+12])[0]
                print(f"  LOAD Segment {i}: vaddr=0x{p_vaddr:x}, alignment=0x{p_align:x} ({p_align} bytes)")
    else:
        print("Unknown ELF class:", elf_class)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python check_alignment.py <so_file>")
    else:
        check_elf_alignment(sys.argv[1])
