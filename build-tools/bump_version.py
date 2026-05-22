import os
import re
import sys

def main():
    gradle_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "app", "build.gradle.kts"))
    
    if not os.path.exists(gradle_path):
        print(f"[ERROR] Khong tim thay file build.gradle.kts tai: {gradle_path}")
        sys.exit(1)
        
    try:
        with open(gradle_path, 'r', encoding='utf-8') as f:
            content = f.read()
    except Exception as e:
        print(f"[ERROR] Khong the doc file build.gradle.kts: {e}")
        sys.exit(1)
        
    # Tim versionCode va versionName trong build.gradle.kts
    code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    
    if not code_match or not name_match:
        print("[ERROR] Khong tim thay thong tin versionCode hoac versionName trong build.gradle.kts!")
        sys.exit(1)
        
    current_code = int(code_match.group(1))
    current_name = name_match.group(1)
    
    print("========================================================")
    print("        MIXCLEAN - TU DONG NANG CAP PHIEN BAN APP")
    print("========================================================")
    print(f"  Phien ban hien tai:")
    print(f"    - versionCode: {current_code}")
    print(f"    - versionName: \"{current_name}\"")
    print("========================================================")
    print("")
    print("  [1] Tu dong tang phien ban (+1 versionCode, tang patch versionName)")
    print("  [2] Thiet lap phien ban thu cong (Custom)")
    print("  [3] Thoat")
    print("")
    
    try:
        choice = input("Vui long chon chuc nang [1-3]: ").strip()
    except KeyboardInterrupt:
        print("\nDa huy.")
        sys.exit(0)
        
    if choice == '1':
        new_code = current_code + 1
        
        # Tach phien ban de tang patch (e.g. 2.0.2 -> 2.0.3)
        parts = current_name.split('.')
        if len(parts) >= 3 and parts[-1].isdigit():
            parts[-1] = str(int(parts[-1]) + 1)
            new_name = '.'.join(parts)
        elif len(parts) == 2 and parts[-1].isdigit():
            parts[-1] = str(int(parts[-1]) + 1)
            new_name = '.'.join(parts)
        else:
            new_name = current_name + ".1"
            
    elif choice == '2':
        try:
            code_input = input(f"Nhap versionCode moi (Mac dinh: {current_code + 1}): ").strip()
            if not code_input:
                new_code = current_code + 1
            else:
                if not code_input.isdigit():
                    print("[ERROR] versionCode phai la mot so nguyen duong!")
                    sys.exit(1)
                new_code = int(code_input)
                
            name_input = input(f"Nhap versionName moi (de trong de giu nguyen '{current_name}'): ").strip()
            new_name = name_input if name_input else current_name
        except KeyboardInterrupt:
            print("\nDa huy.")
            sys.exit(0)
            
    else:
        print("Da thoat.")
        sys.exit(0)
        
    # Thay the trong noi dung file
    new_content = content
    new_content = re.sub(r'(versionCode\s*=\s*)\d+', f'\\1{new_code}', new_content)
    new_content = re.sub(r'(versionName\s*=\s*)"[^"]+"', f'\\1"{new_name}"', new_content)
    
    try:
        with open(gradle_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
    except Exception as e:
        print(f"[ERROR] Khong the ghi file build.gradle.kts: {e}")
        sys.exit(1)
        
    print("")
    print("========================================================")
    print("   CAP NHAT PHIEN BAN MOI THANH CONG!")
    print("========================================================")
    print(f"  Phien ban moi da duoc luu:")
    print(f"    - versionCode: {new_code}")
    print(f"    - versionName: \"{new_name}\"")
    print("========================================================")
    print("")

if __name__ == '__main__':
    main()
