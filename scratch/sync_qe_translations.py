import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

APP_RES_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"
QE_RES_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\QuickEdit-Photo-Editor-main\quickedit\src\main\res"
QE_MASTER_FILE = os.path.join(QE_RES_DIR, 'values', 'strings.xml')

def get_keys(file_path):
    if not os.path.exists(file_path): return []
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        return [(child.attrib['name'], child.text) for child in root if child.tag == 'string']
    except:
        return []

def save_xml(root, file_path):
    os.makedirs(os.path.dirname(file_path), exist_ok=True)
    xml_str = ET.tostring(root, encoding='utf-8')
    pretty_xml = minidom.parseString(xml_str).toprettyxml(indent="    ")
    pretty_xml = "\n".join([line for line in pretty_xml.splitlines() if line.strip()])
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(pretty_xml)

def android_escape(s):
    if not s: return s
    s = s.replace("\\'", "'").replace('\\"', '"')
    s = s.replace("'", "\\'").replace('"', '\\"')
    return s

TOOL_TRANSLATIONS = {
    'vi': {
        'app_name': 'QuickEdit', 'app_name_full': 'QuickEdit - Trình chỉnh sửa ảnh',
        'color': 'Màu sắc', 'format': 'Định dạng', 'font': 'Phông chữ', 'brush': 'Cọ vẽ',
        'pan': 'Di chuyển', 'zoom': 'Thu phóng', 'shape': 'Hình dạng', 'eraser': 'Tẩy',
        'crop': 'Cắt ảnh', 'draw': 'Vẽ', 'text': 'Văn bản', 'effects': 'Hiệu ứng',
        'width': 'Độ rộng', 'opacity': 'Độ mờ', 'done': 'Xong', 'something_went_wrong': 'Đã có lỗi xảy ra',
        'enter_your_text': 'Nhập văn bản', 'pick_image': 'Chọn ảnh', 'capture_image': 'Chụp ảnh',
        'saving_image': 'Đang lưu ảnh', 'image_saved_successfully': 'Đã lưu ảnh thành công',
        'failed_to_save_image': 'Lưu ảnh thất bại', 'permission_required': 'Cần cấp quyền',
        'grant_permission': 'Cấp quyền', 'okay': 'Đồng ý', 'storage_permission_rationale': 'Cần quyền truy cập bộ nhớ để lưu ảnh.',
        'storage_permission_permanently_declined': 'Bạn đã từ chối quyền vĩnh viễn, vui lòng mở cài đặt.',
        'app_not_found': 'Không tìm thấy ứng dụng', 'enter_aspect_ratio': 'Nhập tỷ lệ khung hình',
        'select': 'Chọn', 'border': 'Viền', 'studio': 'Studio', 'blur': 'Làm mờ', 'portrait': 'Chân dung',
        'clean': 'Làm sạch', 'darken': 'Làm tối', 'remove_bg': 'Xoá nền', 'background': 'Nền',
        'remove_bg_warning': 'Vui lòng xóa nền trước', 'back': 'Quay lại', 'original': 'Gốc',
        'save_draft': 'Lưu nháp', 'magic_brush': 'Tẩy xoá AI', 'tool_magic_brush': 'Tẩy xoá AI',
        'remove_object': 'Xoá vật thể', 'mosaic': 'Làm mờ (Mosaic)', 'label_magic_wand': 'Đũa thần',
        'label_offset': 'Bù trừ', 'label_brush_size': 'Kích thước cọ', 'label_tolerance': 'Độ nhạy',
        'label_erase_brush': 'Cọ xoá', 'effect_original': 'Gốc', 'effect_grayscale': 'Trắng đen',
        'crop_free': 'Tự do', 'crop_custom': 'Tuỳ chỉnh', 'crop_square': 'Hình vuông',
        'cancel': 'Huỷ', 'error_apply_effect': 'Lỗi áp dụng hiệu ứng',
        'error_apply_background': 'Lỗi áp dụng nền', 'error_auto_removal_failed': 'Tự động xóa nền thất bại: %1$s',
        'error_recycled_bitmap': 'Dữ liệu ảnh đã bị giải phóng'
    },
    'ko': {
        'app_name': 'QuickEdit', 'app_name_full': 'QuickEdit - 사진 편집기',
        'color': '색상', 'format': '형식', 'font': '글꼴', 'brush': '브러시', 'pan': '팬', 'zoom': '확대/축소',
        'shape': '모양', 'eraser': '지우개', 'crop': '자르기', 'draw': '그리기', 'text': '텍스트', 'effects': '효과',
        'width': '너비', 'opacity': '불투명도', 'done': '완료', 'something_went_wrong': '문제가 발생했습니다',
        'enter_your_text': '텍스트를 입력하세요', 'pick_image': '이미지 선택', 'capture_image': '사진 촬영',
        'saving_image': '저장 중', 'image_saved_successfully': '저장되었습니다', 'failed_to_save_image': '저장 실패',
        'permission_required': '권한 필요', 'grant_permission': '권한 허용', 'okay': '확인',
        'storage_permission_rationale': '편집된 파일을 저장하려면 저장소 권한이 필요합니다.',
        'storage_permission_permanently_declined': '저장소 권한을 영구적으로 거부한 것 같습니다. 설정에서 허용할 수 있습니다.',
        'app_not_found': '앱을 찾을 수 없음', 'enter_aspect_ratio': '가로세로 비율 입력',
        'select': '선택', 'border': '테두리', 'studio': '스튜디오', 'blur': '흐림', 'portrait': '인물 사진',
        'clean': '깨끗하게', 'darken': '어둡게', 'remove_bg': '배경 제거', 'background': '배경',
        'remove_bg_warning': '배경을 먼저 제거해 주세요', 'back': '뒤로', 'original': '원본', 'save_draft': '초안 저장',
        'magic_brush': '매직 브러시', 'tool_magic_brush': '매직 브러시', 'remove_object': '제거', 'mosaic': '모자이크',
        'label_magic_wand': '마법 지팡이', 'label_offset': '오프셋', 'label_brush_size': '브러시 크기',
        'label_tolerance': '허용 오차', 'label_erase_brush': '지우개 브러시', 'effect_original': '원본',
        'effect_grayscale': '회색조', 'effect_blur': '흐림', 'crop_free': '자유형', 'crop_custom': '사용자 정의',
        'crop_square': '정사각형', 'cancel': '취소',
        'failed_to_apply_effect': '효과를 적용하지 못했습니다', 'failed_to_apply_background': '배경을 적용하지 못했습니다',
        'aspect_ratio_equals': '가로세로 비율 = %1$s', 'min_allowed_aspect_ratio': '최소 허용 비율 = %1$.2f',
        'max_allowed_aspect_ratio': '최대 허용 비율 = %1$.2f', 'error_apply_effect': '효과 적용 오류',
        'error_apply_background': '배경 적용 오류', 'error_auto_removal_failed': '자동 배경 제거 실패: %1$s',
        'error_recycled_bitmap': '비트맵이 이미 재활용되었습니다'
    },
    'zh-rCN': {
        'app_name': 'QuickEdit', 'app_name_full': 'QuickEdit - 照片编辑器',
        'color': '颜色', 'format': '格式', 'font': '字体', 'brush': '画笔', 'pan': '平移', 'zoom': '缩放',
        'shape': '形状', 'eraser': '橡皮擦', 'crop': '裁剪', 'draw': '绘制', 'text': '文字', 'effects': '效果',
        'width': '宽度', 'opacity': '不透明度', 'done': '完成', 'something_went_wrong': '出错了',
        'enter_your_text': '输入文字', 'pick_image': '选择图片', 'capture_image': '拍照',
        'saving_image': '正在保存', 'image_saved_successfully': '保存成功', 'failed_to_save_image': '保存失败',
        'permission_required': '需要权限', 'grant_permission': '授予权限', 'okay': '确定',
        'storage_permission_rationale': '应用需要存储权限才能将编辑的文件保存到您的设备。',
        'storage_permission_permanently_declined': '您已永久拒绝权限，请在设置中手动开启。',
        'app_not_found': '未找到应用', 'enter_aspect_ratio': '输入纵横比', 'select': '选择',
        'border': '边框', 'studio': '工作室', 'blur': '模糊', 'portrait': '人像', 'clean': '清洁',
        'darken': '加深', 'remove_bg': '移除背景', 'studio_intensity': '强度', 'background': '背景',
        'gradient': '渐变', 'remove_bg_warning': '请先移除背景', 'back': '返回', 'original': '原图',
        'save_draft': '保存草稿', 'magic_brush': '智能擦除', 'tool_magic_brush': '智能擦除',
        'remove_object': '移除', 'mosaic': '马赛克', 'label_magic_wand': '智能擦除',
        'label_offset': '偏移', 'label_brush_size': '画笔大小', 'label_tolerance': '容差',
        'label_erase_brush': '画笔擦除', 'effect_original': '原图', 'effect_grayscale': '灰度',
        'effect_blur': '模糊', 'crop_free': '自由', 'crop_custom': '自定义', 'crop_square': '正方形',
        'cancel': '取消', 'error_apply_effect': '应用效果失败', 'error_apply_background': '应用背景失败',
        'error_auto_removal_failed': '自动背景移除失败：%1$s', 'error_recycled_bitmap': '位图已被回收'
    },
    'ja': {
        'color': '色', 'format': 'フォーマット', 'font': 'フォント', 'brush': 'ブラシ', 'pan': 'パン', 'zoom': 'ズーム',
        'shape': '形', 'eraser': '消しゴム', 'crop': '切り抜き', 'draw': '描画', 'text': 'テキスト', 'effects': 'エフェクト',
        'width': '幅', 'opacity': '不透明度', 'done': '完了', 'pick_image': '画像を選択', 'capture_image': '写真を撮る',
        'saving_image': '保存中', 'image_saved_successfully': '保存しました', 'failed_to_save_image': '保存に失敗しました',
        'okay': 'OK', 'border': 'ボーダー', 'studio': 'スタジオ', 'blur': 'ぼかし', 'remove_bg': '背景削除',
        'background': '背景', 'magic_brush': 'マジックブラシ', 'tool_magic_brush': 'マジックブラシ',
        'remove_object': '削除', 'mosaic': 'モザイク', 'label_magic_wand': '魔法の杖', 'label_erase_brush': '消しゴムブラシ',
        'effect_blur': 'ぼかし', 'cancel': 'キャンセル'
    }
}

qe_master_keys = get_keys(QE_MASTER_FILE)
app_langs = [item.replace('values-', '') for item in os.listdir(APP_RES_DIR) if item.startswith('values-') and item != 'values-night']

for lang in app_langs:
    qe_lang_dir = os.path.join(QE_RES_DIR, f'values-{lang}')
    qe_lang_file = os.path.join(qe_lang_dir, 'strings.xml')
    
    existing_qe_strings = dict(get_keys(qe_lang_file))
    
    root = ET.Element("resources")
    for name, master_val in qe_master_keys:
        val = TOOL_TRANSLATIONS.get(lang, {}).get(name)
        if not val:
            val = existing_qe_strings.get(name)
        if not val or val == master_val:
            val = master_val
            
        s = ET.SubElement(root, "string")
        s.set("name", name)
        s.text = android_escape(val)

    save_xml(root, qe_lang_file)
    print(f"Updated QuickEdit strings for: {lang}")

print("Done.")
