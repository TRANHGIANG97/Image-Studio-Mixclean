import os
import re

BASE_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\QuickEdit-Photo-Editor-main\quickedit\src\main\res"

def clean_module_strings(file_path):
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # Remove app_name and app_name_full
        new_content = re.sub(r'\s*<string name="app_name">.*?</string>', '', content)
        new_content = re.sub(r'\s*<string name="app_name_full">.*?</string>', '', new_content)
        
        if new_content != content:
            print(f"Cleaned module strings in {file_path}")
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(new_content)
    except Exception as e:
        print(f"Error processing {file_path}: {e}")

if os.path.exists(BASE_DIR):
    for item in os.listdir(BASE_DIR):
        if item.startswith('values-'):
            file_path = os.path.join(BASE_DIR, item, 'strings.xml')
            if os.path.exists(file_path):
                clean_module_strings(file_path)

print("Done.")
