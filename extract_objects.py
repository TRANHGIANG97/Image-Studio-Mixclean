import cv2
import numpy as np
import os

def extract_foreground(image_path, output_path, rect):
    print(f"Extracting foreground for {image_path}...")
    img = cv2.imread(image_path)
    if img is None:
        print(f"Error: Could not read {image_path}")
        return False
        
    h, w, c = img.shape
    
    # 1. Dùng GrabCut để tạo mask đối tượng
    mask = np.zeros(img.shape[:2], np.uint8)
    bgdModel = np.zeros((1, 65), np.float64)
    fgdModel = np.zeros((1, 65), np.float64)
    
    x, y, box_w, box_h = rect
    x = max(0, min(x, w - 1))
    y = max(0, min(y, h - 1))
    box_w = max(1, min(box_w, w - x))
    box_h = max(1, min(box_h, h - y))
    
    try:
        cv2.grabCut(img, mask, (x, y, box_w, box_h), bgdModel, fgdModel, 7, cv2.GC_INIT_WITH_RECT)
    except Exception as e:
        print(f"GrabCut failed: {e}")
        return False
        
    # Lấy mask (đối tượng là 1 hoặc 3)
    bin_mask = np.where((mask == 2) | (mask == 0), 0, 1).astype('uint8')
    
    # Làm mượt mask bằng Gaussian Blur để viền không bị răng cưa
    blur_mask = cv2.GaussianBlur(bin_mask * 255, (5, 5), 0)
    
    # Tạo ảnh PNG trong suốt với kênh alpha
    rgba = cv2.cvtColor(img, cv2.COLOR_BGR2BGRA)
    rgba[:, :, 3] = blur_mask
    
    # Lưu ảnh kết quả
    cv2.imwrite(output_path, rgba)
    print(f"Successfully saved extracted object to {output_path}")
    return True

# Khai báo thư mục
assets_dir = "studio_edit/src/main/assets/anh_chuyen_nghiep"

# Khai báo cấu hình bounding box cho từng ảnh (tọa độ x, y, w, h)
# Hầu hết ảnh rộng 600 và cao 800 hoặc tương tự, đối tượng nằm ở trung tâm
configs = [
    ("sneaker_obj_src.png", "sneaker_obj.png", (80, 150, 440, 500)),
    ("glasses_obj_src.png", "glasses_obj.png", (60, 200, 480, 400)),
    ("cup_obj_src.png", "cup_obj.png", (100, 150, 400, 550)),
    ("lipstick_obj_src.png", "lipstick_obj.png", (120, 100, 360, 650)),
    ("chair_obj_src.png", "chair_obj.png", (50, 50, 500, 700)),
    ("lamp_obj_src.png", "lamp_obj.png", (80, 80, 440, 680)),
    ("plant_obj_src.png", "plant_obj.png", (80, 80, 440, 680)),
]

for src_name, out_name, rect in configs:
    src_path = os.path.join(assets_dir, src_name)
    out_path = os.path.join(assets_dir, out_name)
    if os.path.exists(src_path):
        extract_foreground(src_path, out_path, rect)
    else:
        print(f"Skipping: {src_path} does not exist.")
