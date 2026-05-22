import os
import sys
import time
from datetime import datetime

# Cac extension mac dinh can xuat
DEFAULT_EXTENSIONS = {'.kt', '.xml', '.gradle', '.kts', '.pro', '.properties'}
# Cac thu muc can bo qua de tranh xuat file rac
IGNORE_DIRS = {
    'build', '.gradle', '.idea', '.git', '.cxx', 'captures', 
    'gradle', 'wrapper', 'device-media', 'exports'
}

def generate_tree(dir_path, extensions, prefix=""):
    """
    Tao cau truc cay thu muc dang text chi bao gom cac file co extension phu hop
    """
    if not os.path.exists(dir_path):
        return ""
        
    lines = []
    try:
        items = sorted(os.listdir(dir_path))
    except Exception:
        return ""
        
    # Loc cac item hop le (thu muc khong nam trong IGNORE_DIRS, hoac file co extension dung)
    valid_items = []
    for item in items:
        full_path = os.path.join(dir_path, item)
        if os.path.isdir(full_path):
            if item not in IGNORE_DIRS:
                # Kiem tra thu muc con co chua file hop le nao khong
                if has_valid_files(full_path, extensions):
                    valid_items.append(item)
        else:
            _, ext = os.path.splitext(item)
            if ext.lower() in extensions or item in {'AndroidManifest.xml', 'proguard-rules.pro'}:
                valid_items.append(item)
                
    count = len(valid_items)
    for i, item in enumerate(valid_items):
        full_path = os.path.join(dir_path, item)
        is_last = (i == count - 1)
        connector = "└── " if is_last else "├── "
        
        lines.append(f"{prefix}{connector}{item}")
        
        if os.path.isdir(full_path):
            new_prefix = prefix + ("    " if is_last else "│   ")
            child_tree = generate_tree(full_path, extensions, new_prefix)
            if child_tree:
                lines.append(child_tree)
                
    return "\n".join(lines)

def has_valid_files(dir_path, extensions):
    """
    Kiem tra de quy xem thu muc co chua file nao hop le hay khong de tranh ve thu muc rong tren cay
    """
    try:
        for root, dirs, files in os.walk(dir_path):
            # Bo qua cac thu muc ignore
            dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
            for file in files:
                _, ext = os.path.splitext(file)
                if ext.lower() in extensions:
                    return True
    except Exception:
        pass
    return False

def export_module_source(root_dir, module_dirs, module_name, extensions):
    """
    Xuat cay thu muc va toan bo code nguon vao file txt duy nhat
    """
    export_dir = os.path.join(root_dir, "build-tools", "exports")
    if not os.path.exists(export_dir):
        os.makedirs(export_dir)
        
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    output_filename = f"export_{module_name}_{timestamp}.txt"
    output_path = os.path.join(export_dir, output_filename)
    
    # Lay danh sach tat ca cac file nguon hop le
    source_files = []
    for m_dir in module_dirs:
        full_m_path = os.path.join(root_dir, m_dir)
        if not os.path.exists(full_m_path):
            continue
        for root, dirs, files in os.walk(full_m_path):
            dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
            for file in files:
                _, ext = os.path.splitext(file)
                if ext.lower() in extensions or file in {'AndroidManifest.xml', 'proguard-rules.pro'}:
                    file_path = os.path.join(root, file)
                    rel_path = os.path.relpath(file_path, root_dir)
                    source_files.append((rel_path, file_path))
                    
    source_files.sort(key=lambda x: x[0])
    
    try:
        with open(output_path, 'w', encoding='utf-8') as out_f:
            # 1. Tieu de file
            out_f.write("================================================================================\n")
            out_f.write(f"   MIXCLEAN - MA NGUON XUAT KHO: MODULE {module_name.upper()}\n")
            out_f.write(f"   Thoi gian xuat: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            out_f.write("================================================================================\n\n")
            
            # 2. Cay thu muc
            out_f.write("================================================================================\n")
            out_f.write("   CAY THU MUC DU AN\n")
            out_f.write("================================================================================\n")
            for m_dir in module_dirs:
                full_m_path = os.path.join(root_dir, m_dir)
                if os.path.exists(full_m_path):
                    out_f.write(f"{m_dir}/\n")
                    tree_str = generate_tree(full_m_path, extensions, prefix=" ")
                    if tree_str:
                        out_f.write(tree_str + "\n")
            out_f.write("\n\n")
            
            # 3. Noi dung tung file
            out_f.write("================================================================================\n")
            out_f.write("   CHI TIET NOI DUNG CHI TIET CAC FILE MA NGUON\n")
            out_f.write("================================================================================\n\n")
            
            for rel_path, abs_path in source_files:
                out_f.write("--------------------------------------------------------------------------------\n")
                out_f.write(f" TAP TIN: {rel_path}\n")
                out_f.write("--------------------------------------------------------------------------------\n")
                try:
                    with open(abs_path, 'r', encoding='utf-8') as in_f:
                        code = in_f.read()
                except UnicodeDecodeError:
                    # Fallback neu file co ky tu la
                    try:
                        with open(abs_path, 'r', encoding='utf-8', errors='replace') as in_f:
                            code = in_f.read()
                    except Exception as e:
                        code = f"[ERROR] Khong the doc file: {e}"
                except Exception as e:
                    code = f"[ERROR] Khong the doc file: {e}"
                    
                out_f.write(code)
                out_f.write("\n\n")
                
        return output_path, len(source_files)
    except Exception as e:
        print(f"[ERROR] Gap loi trong qua trinh ghi file: {e}")
        return None, 0

def main():
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    
    print("========================================================")
    print("        MIXCLEAN - CONG CU TRICH XUAT MA NGUON")
    print("========================================================")
    print("  Chon module can xuat:")
    print("    [1] Module goc & Cac core module [app, core-data, core-model, etc.]")
    print("    [2] Module Editor Studio [studio_edit]")
    print("    [3] Module QuickEdit [QuickEdit-Photo-Editor-main]")
    print("    [4] TOAN BO TAT CA CAC MODULE CUNG LUC")
    print("    [5] Thoat")
    print("========================================================")
    print("")
    
    try:
        choice = input("Vui long chon chuc nang [1-5]: ").strip()
    except KeyboardInterrupt:
        print("\nDa huy.")
        sys.exit(0)
        
    if choice == '1':
        module_dirs = ['app', 'core-data', 'core-model', 'core-designsystem', 'core-common']
        module_name = 'goc_va_core'
    elif choice == '2':
        module_dirs = ['studio_edit']
        module_name = 'studio_edit'
    elif choice == '3':
        module_dirs = ['QuickEdit-Photo-Editor-main']
        module_name = 'quick_edit'
    elif choice == '4':
        module_dirs = [
            'app', 'core-data', 'core-model', 'core-designsystem', 'core-common',
            'studio_edit', 'QuickEdit-Photo-Editor-main'
        ]
        module_name = 'toan_bo_du_an'
    else:
        print("Da thoat.")
        sys.exit(0)
        
    print("")
    print("Dang thu thap file va tao cay thu muc...")
    
    output_path, file_count = export_module_source(root_dir, module_dirs, module_name, DEFAULT_EXTENSIONS)
    
    if output_path:
        print("========================================================")
        print("   XUAT MA NGUON PHAN MONG THANH CONG!")
        print("========================================================")
        print(f"  Tong so file da trich xuat: {file_count}")
        print(f"  Tep tin xuat ra: {os.path.basename(output_path)}")
        print(f"  Duong dan full: {output_path}")
        print("========================================================")
        
        # Tu dong mo file bang notepad hoac editor mac dinh de user xem
        try:
            os.startfile(output_path)
        except Exception:
            pass
    else:
        print("[ERROR] Xuat file ma nguon that bai.")
        sys.exit(1)

if __name__ == '__main__':
    main()
