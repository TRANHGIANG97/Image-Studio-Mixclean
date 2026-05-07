import os
import xml.etree.ElementTree as ET

def get_keys(file_path):
    if not os.path.exists(file_path):
        return set()
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        return {child.attrib['name'] for child in root if child.tag == 'string'}
    except:
        return set()

# Cập nhật đường dẫn tương đối để chạy được ở mọi nơi trong dự án
base_dir = os.path.dirname(os.path.abspath(__file__))
res_path = os.path.join(base_dir, 'QuickEdit-Photo-Editor-main', 'quickedit', 'src', 'main', 'res')

master_file = os.path.join(res_path, 'values', 'strings.xml')
master_keys = get_keys(master_file)

languages = [
    'ar', 'de', 'es', 'fr', 'hi', 'in', 'it', 'ja', 'ko', 'pl', 
    'pt-rBR', 'th', 'tr', 'vi', 'zh-rCN', 'zh-rTW'
]

print(f"--- QuickEdit Translation Audit ---")
print(f"Master keys in 'values/strings.xml': {len(master_keys)}")
print("-" * 40)

for lang in languages:
    lang_file = os.path.join(res_path, f'values-{lang}', 'strings.xml')
    lang_keys = get_keys(lang_file)
    missing = master_keys - lang_keys
    if missing:
        print(f"[!] Language {lang.upper()} is missing {len(missing)} keys.")
        print(f"    Missing: {sorted(list(missing))}")
    elif not os.path.exists(lang_file):
        print(f"[X] Language {lang.upper()} file NOT FOUND!")
    else:
        print(f"[OK] Language {lang.upper()} is up to date.")

print("-" * 40)
print("Audit complete.")
