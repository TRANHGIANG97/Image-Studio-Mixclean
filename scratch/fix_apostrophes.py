import os

res_dir = r'app\src\main\res'
for d in os.listdir(res_dir):
    if d.startswith('values-') and d != 'values-night':
        path = os.path.join(res_dir, d, 'strings.xml')
        if os.path.exists(path):
            with open(path, 'r', encoding='utf-8') as f:
                lines = f.readlines()
            
            changed = False
            new_lines = []
            for line in lines:
                if '<string' in line and '</string>' in line:
                    start_idx = line.find('>') + 1
                    end_idx = line.rfind('<')
                    content = line[start_idx:end_idx]
                    
                    if "'" in content:
                        # Only escape if not already escaped
                        # A simple way is to replace all \' with ' and then all ' with \'
                        fixed_content = content.replace("\\'", "'").replace("'", "\\'")
                        if fixed_content != content:
                            line = line[:start_idx] + fixed_content + line[end_idx:]
                            changed = True
                new_lines.append(line)
            
            if changed:
                with open(path, 'w', encoding='utf-8') as f:
                    f.writelines(new_lines)
                print(f"Fixed apostrophes in {path}")
