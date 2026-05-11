import os

DIRS = [
    r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res",
    r"c:\Users\Toshiba\Desktop\imageProduction-main\QuickEdit-Photo-Editor-main\quickedit\src\main\res"
]

for base_dir in DIRS:
    if not os.path.exists(base_dir): continue
    for root, dirs, files in os.walk(base_dir):
        for file in files:
            if file == 'strings.xml':
                file_path = os.path.join(root, file)
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                # Fix the double escaping
                new_content = content.replace("\\\\'", "\\'")
                new_content = new_content.replace("\\\\\"", "\\\"")
                
                if new_content != content:
                    print(f"Fixed double escapes in {file_path}")
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(new_content)

print("Done.")
