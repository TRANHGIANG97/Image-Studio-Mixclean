import os
import xml.etree.ElementTree as ET

def check_file(file_path):
    if not os.path.exists(file_path):
        return "MISSING"
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        if '????' in content:
            return "CORRUPTED (????)"
        
        # Check for mojibake patterns common in ISO-8859-1 as UTF-8
        # Like Ø, æ, Ø§, etc.
        mojibake_chars = ['Ø', 'æ', 'ì', 'ë', 'ç']
        count = sum(1 for c in mojibake_chars if c in content)
        if count >= 2:
            return "CORRUPTED (MOJIBAKE)"

        tree = ET.parse(file_path)
        return "OK"
    except ET.ParseError:
        return "INVALID XML"
    except Exception as e:
        return f"ERROR: {str(e)}"

base_dir = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"
master_file = os.path.join(base_dir, 'values', 'strings.xml')

print(f"Auditing {base_dir}...")

results = {}
for item in os.listdir(base_dir):
    if item.startswith('values-'):
        lang = item.replace('values-', '')
        file_path = os.path.join(base_dir, item, 'strings.xml')
        status = check_file(file_path)
        results[lang] = status

for lang, status in sorted(results.items()):
    if status != "OK":
        print(f"{lang:10} : {status}")

print("Done.")
