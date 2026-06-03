import os
from pathlib import Path
from PIL import Image

ASSETS_DIR = Path(r'C:\Users\Toshiba\Desktop\imageProduction-main\studio_edit\src\main\assets')
count = 0
for f in ASSETS_DIR.rglob('*.png'):
    if 'obj_src' in f.name or len(f.name) == 6: # x2.png
        # Convert to WEBP lossy with alpha
        webp_path = f.with_suffix('.webp')
        img = Image.open(f)
        # resize if needed
        w, h = img.size
        max_px = 700
        if max(w, h) > max_px:
            if w >= h:
                new_w = max_px
                new_h = int(h * max_px / w)
            else:
                new_h = max_px
                new_w = int(w * max_px / h)
            img = img.resize((new_w, new_h), Image.LANCZOS)
        
        img.save(webp_path, 'WEBP', quality=85)
        
        orig_kb = f.stat().st_size / 1024
        new_kb = webp_path.stat().st_size / 1024
        
        if new_kb < orig_kb:
            print(f'[WEBP] {f.name} {orig_kb:.0f}KB -> {new_kb:.0f}KB')
            f.unlink() # delete original png
            count += 1
        else:
            print(f'[WEBP-SKIP] {f.name} {orig_kb:.0f}KB (webp is larger)')
            webp_path.unlink()
