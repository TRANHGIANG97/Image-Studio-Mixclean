import os
import xml.etree.ElementTree as ET
import re

BASE_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"

def escape_android(s):
    if not s: return s
    
    # First, convert any &quot; to " so we have a clean state
    s = s.replace('&quot;', '"')
    
    # Remove invalid backslashes before quotes or ampersands
    s = s.replace('\\"', '"') # We'll re-escape later
    s = s.replace("\\'", "'") # We'll re-escape later
    s = s.replace('\\&', '&')
    
    # Now escape all ' and "
    s = s.replace("'", "\\'")
    s = s.replace('"', '\\"')
    
    return s

def fix_file(file_path):
    try:
        # Read the file as text first to avoid ET entity decoding issues
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()
            
        # Regex to find all <string ...>...</string>
        # This is safer than ET if we want to handle escaping manually
        def replacer(match):
            tag_open = match.group(1)
            inner_text = match.group(2)
            tag_close = match.group(3)
            
            fixed_text = escape_android(inner_text)
            return f"{tag_open}{fixed_text}{tag_close}"

        new_content = re.sub(r'(<string[^>]*>)(.*?)(</string>)', replacer, content, flags=re.DOTALL)
        
        if new_content != content:
            print(f"Fixed escaping in {file_path}")
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(new_content)
    except Exception as e:
        print(f"Error processing {file_path}: {e}")

DIRS = [
    r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res",
    r"c:\Users\Toshiba\Desktop\imageProduction-main\QuickEdit-Photo-Editor-main\quickedit\src\main\res"
]

for base_dir in DIRS:
    if not os.path.exists(base_dir): continue
    for item in os.listdir(base_dir):
        if item.startswith('values-'):
            file_path = os.path.join(base_dir, item, 'strings.xml')
            if os.path.exists(file_path):
                fix_file(file_path)

print("Done.")
