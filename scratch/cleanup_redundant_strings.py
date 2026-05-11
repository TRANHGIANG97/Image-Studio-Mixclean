import os
import xml.etree.ElementTree as ET

APP_RES_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"
QE_RES_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\QuickEdit-Photo-Editor-main\quickedit\src\main\res"
QE_MASTER_FILE = os.path.join(QE_RES_DIR, 'values', 'strings.xml')

def get_keys(file_path):
    if not os.path.exists(file_path): return set()
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        return set(child.attrib['name'] for child in root if child.tag == 'string')
    except:
        return set()

qe_keys = get_keys(QE_MASTER_FILE)
print(f"Found {len(qe_keys)} keys in QuickEdit module.")

app_values_dirs = [d for d in os.listdir(APP_RES_DIR) if d.startswith('values')]

for d in app_values_dirs:
    app_file = os.path.join(APP_RES_DIR, d, 'strings.xml')
    if not os.path.exists(app_file): continue
    
    try:
        # Use a more robust way to preserve comments if possible, but standard ET is easier for filtering
        tree = ET.parse(app_file)
        root = tree.getroot()
        
        initial_count = len(root)
        
        # Remove children whose name is in qe_keys
        to_remove = []
        for child in root:
            if child.tag == 'string' and child.attrib.get('name') in qe_keys:
                # SPECIAL CASE: We might want to keep 'app_name' if it's different
                if child.attrib.get('name') == 'app_name': continue
                to_remove.append(child)
        
        for child in to_remove:
            root.remove(child)
        
        final_count = len(root)
        removed = initial_count - final_count
        
        if removed > 0:
            tree.write(app_file, encoding='utf-8', xml_declaration=True)
            print(f"Cleaned {app_file}: removed {removed} redundant keys.")
        else:
            print(f"No redundant keys in {app_file}.")
            
    except Exception as e:
        print(f"Error cleaning {app_file}: {e}")

print("Cleanup done.")
