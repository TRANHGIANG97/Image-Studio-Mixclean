import os
import xml.etree.ElementTree as ET

APP_RES_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"

APP_TRANSLATIONS = {
    'vi': {
        'home_dock_pick_image': 'Chọn ảnh',
        'choose_image': 'Chọn ảnh',
        'home_dock_photo_editor': 'Sửa ảnh',
        'draft_confirm_delete_multiple': 'Bạn có chắc chắn muốn xóa %1$d bản nháp đã chọn không?',
        'exit_dialog_message': 'Bạn có muốn thoát ứng dụng không?'
    },
    'ko': {
        'home_dock_pick_image': '이미지 선택',
        'choose_image': '이미지 선택',
        'home_dock_photo_editor': '사진 편집',
        'draft_confirm_delete_multiple': '선택한 %1$d개의 초안을 삭제하시겠습니까?',
        'exit_dialog_message': '앱을 종료하시겠습니까?'
    },
    'zh-rCN': {
        'home_dock_pick_image': '选择图片',
        'choose_image': '选择图片',
        'home_dock_photo_editor': '照片编辑',
        'draft_confirm_delete_multiple': '您确定要删除选中的 %1$d 个草稿吗？',
        'exit_dialog_message': '您确定要退出应用吗？'
    },
    'ja': {
        'home_dock_pick_image': '画像を選択',
        'choose_image': '画像を選択',
        'home_dock_photo_editor': '写真編集',
        'draft_confirm_delete_multiple': '選択した %1$d 個の下書きを削除してもよろしいですか？',
        'exit_dialog_message': 'アプリを終了しますか？'
    },
    'es': {
        'home_dock_pick_image': 'Elegir imagen',
        'choose_image': 'Elegir imagen',
        'home_dock_photo_editor': 'Editor de fotos',
        'draft_confirm_delete_multiple': '¿Estás seguro de que deseas eliminar %1$d borradores seleccionados?',
        'exit_dialog_message': '¿Quieres salir de la aplicación?'
    },
    'fr': {
        'home_dock_pick_image': 'Choisir une image',
        'choose_image': 'Choisir une image',
        'home_dock_photo_editor': 'Éditeur de photos',
        'draft_confirm_delete_multiple': 'Êtes-vous sûr de vouloir supprimer les %1$d brouillons sélectionnés ?',
        'exit_dialog_message': 'Voulez-vous quitter l\'application ?'
    },
    'de': {
        'home_dock_pick_image': 'Bild auswählen',
        'choose_image': 'Bild auswählen',
        'home_dock_photo_editor': 'Foto-Editor',
        'draft_confirm_delete_multiple': 'Sind Sie sicher, dass Sie %1$d ausgewählte Entwürfe löschen möchten?',
        'exit_dialog_message': 'Möchten Sie die App beenden?'
    },
    'ru': {
        'home_dock_pick_image': 'Выбрать изображение',
        'choose_image': 'Выбрать изображение',
        'home_dock_photo_editor': 'Фоторедактор',
        'draft_confirm_delete_multiple': 'Вы уверены, что хотите удалить %1$d выбранных черновиков?',
        'exit_dialog_message': 'Вы хотите выйти из приложения?'
    },
    'pt-rBR': {
        'home_dock_pick_image': 'Escolher imagem',
        'choose_image': 'Escolher imagem',
        'home_dock_photo_editor': 'Editor de fotos',
        'draft_confirm_delete_multiple': 'Tem certeza de que deseja excluir %1$d rascunhos selecionados?',
        'exit_dialog_message': 'Deseja sair do aplicativo?'
    },
    'it': {
        'home_dock_pick_image': 'Scegli immagine',
        'choose_image': 'Scegli immagine',
        'home_dock_photo_editor': 'Editor foto',
        'draft_confirm_delete_multiple': 'Sei sicuro di voler eliminare %1$d bozze selezionate?',
        'exit_dialog_message': 'Vuoi uscire dall\'applicazione?'
    },
    'ar': {
        'home_dock_pick_image': 'اختر صورة',
        'choose_image': 'اختر صورة',
        'home_dock_photo_editor': 'محرر الصور',
        'draft_confirm_delete_multiple': 'هل أنت متأكد أنك تريد حذف %1$d من المسودات المختارة؟',
        'exit_dialog_message': 'هل تريد الخروج من التطبيق؟'
    },
    'hi': {
        'home_dock_pick_image': 'छवि चुनें',
        'choose_image': 'छवि चुनें',
        'home_dock_photo_editor': 'फोटो संपादक',
        'draft_confirm_delete_multiple': 'क्या आप वाकई %1$d चयनित ड्राफ्ट हटाना चाहते हैं?',
        'exit_dialog_message': 'क्या आप ऐप से बाहर निकलना चाहते हैं?'
    },
    'tr': {
        'home_dock_pick_image': 'Resim Seç',
        'choose_image': 'Resim Seç',
        'home_dock_photo_editor': 'Fotoğraf Düzenleyici',
        'draft_confirm_delete_multiple': 'Seçilen %1$d taslağı silmek istediğinizden emin misiniz?',
        'exit_dialog_message': 'Uygulamadan çıkmak istiyor musunuz?'
    },
    'in': {
        'home_dock_pick_image': 'Pilih Gambar',
        'choose_image': 'Pilih Gambar',
        'home_dock_photo_editor': 'Editor Foto',
        'draft_confirm_delete_multiple': 'Apakah Anda yakin ingin menghapus %1$d draf yang dipilih?',
        'exit_dialog_message': 'Apakah Anda muốn keluar từ aplikasi?'
    },
    'th': {
        'home_dock_pick_image': 'เลือกรูปภาพ',
        'choose_image': 'เลือกรูปภาพ',
        'home_dock_photo_editor': 'แก้ไขรูปภาพ',
        'draft_confirm_delete_multiple': 'คุณแน่ใจหรือไม่ว่าต้องการลบ %1$d ร่างที่เลือก?',
        'exit_dialog_message': 'คุณต้องการออกจากแอปหรือไม่?'
    },
    'pl': {
        'home_dock_pick_image': 'Wybierz obraz',
        'choose_image': 'Wybierz obraz',
        'home_dock_photo_editor': 'Edytor zdjęć',
        'draft_confirm_delete_multiple': 'Czy na pewno chcesz usunąć %1$d wybranych szkiców?',
        'exit_dialog_message': 'Czy chcesz wyjść z aplikacji?'
    }
}

app_langs = [item.replace('values-', '') for item in os.listdir(APP_RES_DIR) if item.startswith('values-') and item != 'values-night']

for lang in app_langs:
    app_file = os.path.join(APP_RES_DIR, f'values-{lang}', 'strings.xml')
    if not os.path.exists(app_file): continue
    
    try:
        tree = ET.parse(app_file)
        root = tree.getroot()
        
        changed = False
        translations = APP_TRANSLATIONS.get(lang, {})
        
        for child in root:
            if child.tag == 'string':
                name = child.attrib.get('name')
                if name in translations:
                    child.text = translations[name]
                    changed = True
        
        if changed:
            tree.write(app_file, encoding='utf-8', xml_declaration=True)
            print(f"Updated app strings for: {lang}")
            
    except Exception as e:
        print(f"Error updating {app_file}: {e}")

print("Done.")
