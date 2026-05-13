import os
import re
import io

root_dir = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"

replacements = {
    "values-vi": "Xoá nền",
    "values": "Remove BG",
    "values-en": "Remove BG",
}

# Generic mapping for common languages
generic_map = {
    "vi": "Xoá nền",
    "zh": "移除背景",
    "ko": "배경 제거",
    "ja": "背景削除",
    "fr": "Suppr. fond",
    "es": "Quitar fondo",
    "pt": "Remover fundo",
    "it": "Rimuovi sfondo",
    "ru": "Удалить фон",
    "de": "HG entfernen",
    "hi": "पृष्ठभूमि हटाएँ",
    "tr": "Arka Planı Sil",
}

for folder in os.listdir(root_dir):
    if folder.startswith("values"):
        file_path = os.path.join(root_dir, folder, "strings.xml")
        if os.path.exists(file_path):
            try:
                with io.open(file_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                # Determine replacement
                new_val = "Remove BG" # Default
                if folder in replacements:
                    new_val = replacements[folder]
                else:
                    found = False
                    for lang_code, val in generic_map.items():
                        if folder == f"values-{lang_code}" or folder.startswith(f"values-{lang_code}-"):
                            new_val = val
                            found = True
                            break
                    if not found:
                        new_val = "Remove BG"
                
                # Regex to find <string name="home_dock_pick_image">...</string>
                pattern = r'(<string name="home_dock_pick_image">)(.*?)(</string>)'
                if re.search(pattern, content):
                    new_content = re.sub(pattern, rf'\1{new_val}\3', content)
                    if new_content != content:
                        with io.open(file_path, "w", encoding="utf-8") as f:
                            f.write(new_content)
                        # Avoid printing non-ascii to console if it causes issues
                        try:
                            print(f"Updated {folder}: {new_val}")
                        except:
                            print(f"Updated {folder}")
            except Exception as e:
                print(f"Error processing {folder}: {str(e)}")
