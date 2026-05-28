import cv2
import numpy as np
import os

def inpaint_product(image_path, output_path, rect, inpaint_radius=9):
    print(f"Processing inpaint for {image_path}...")
    img = cv2.imread(image_path)
    if img is None:
        print(f"Error: Could not read {image_path}")
        return False
        
    h, w, c = img.shape
    
    # 1. Dùng GrabCut để tạo mask chính xác cho đối tượng
    mask = np.zeros(img.shape[:2], np.uint8)
    bgdModel = np.zeros((1, 65), np.float64)
    fgdModel = np.zeros((1, 65), np.float64)
    
    x, y, box_w, box_h = rect
    x = max(0, min(x, w - 1))
    y = max(0, min(y, h - 1))
    box_w = max(1, min(box_w, w - x))
    box_h = max(1, min(box_h, h - y))
    
    try:
        cv2.grabCut(img, mask, (x, y, box_w, box_h), bgdModel, fgdModel, 5, cv2.GC_INIT_WITH_RECT)
        bin_mask = np.where((mask == 2) | (mask == 0), 0, 1).astype('uint8') * 255
    except Exception as e:
        print(f"GrabCut failed, falling back to simple center threshold: {e}")
        # Bounding box fallback
        bin_mask = np.zeros(img.shape[:2], np.uint8)
        bin_mask[y:y+box_h, x:x+box_w] = 255
    
    # 2. Giãn nở mask một chút để bao trọn bóng đổ mờ xung quanh đối tượng
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15))
    dilated_mask = cv2.dilate(bin_mask, kernel, iterations=2)
    
    # 3. Chạy thuật toán Inpainting của OpenCV để lấp đầy nền
    inpainted = cv2.inpaint(img, dilated_mask, inpaintRadius=inpaint_radius, flags=cv2.INPAINT_TELEA)
    
    # Lưu kết quả
    cv2.imwrite(output_path, inpainted)
    print(f"Successfully saved clean background to {output_path}")
    return True

# Khai báo thư mục
assets_dir = "studio_edit/src/main/assets/anh_chuyen_nghiep"

# Danh sách cấu hình bounding box và inpaint cho 7 mẫu mới
# Rộng 600 x Cao 800 hoặc tương tự, đối tượng nhỏ ở giữa nên bounding box rất gọn gàng!
configs = [
    ("sneaker_obj_src.png", "sneaker_bg.png", (100, 200, 400, 450), 9),
    ("glasses_obj_src.png", "glasses_bg.png", (80, 220, 440, 360), 7),
    ("cup_obj_src.png", "cup_bg.png", (120, 200, 360, 450), 7),
    ("lipstick_obj_src.png", "lipstick_bg.png", (150, 150, 300, 550), 7),
    ("smartwatch_obj_src.png", "smartwatch_bg.png", (120, 180, 360, 480), 7),
    ("soda_obj_src.png", "soda_bg.png", (150, 180, 300, 480), 7),
    ("cream_obj_src.png", "cream_bg.png", (120, 180, 360, 480), 7),
]

for src_name, bg_name, rect, radius in configs:
    src_path = os.path.join(assets_dir, src_name)
    bg_path = os.path.join(assets_dir, bg_name)
    if os.path.exists(src_path):
        inpaint_product(src_path, bg_path, rect, radius)
    else:
        print(f"File not found: {src_path}")
