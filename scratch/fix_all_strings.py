import os
import xml.etree.ElementTree as ET
import re

# Masters
BASE_DIR = r"c:\Users\Toshiba\Desktop\imageProduction-main\app\src\main\res"
MASTER_FILE = os.path.join(BASE_DIR, 'values', 'strings.xml')

def get_master_strings():
    tree = ET.parse(MASTER_FILE)
    root = tree.getroot()
    return [(child.attrib['name'], child.text, child.attrib.get('translatable', 'true')) for child in root if child.tag == 'string']

def fix_encoding(text):
    if not text: return text
    try:
        # Try to fix mojibake: UTF-8 -> ISO-8859-1 -> UTF-8
        return text.encode('cp1252').decode('utf-8')
    except:
        return text

def is_corrupted(content):
    if '????' in content: return True
    # Look for mojibake patterns
    if re.search(r'[Øæ][^ ]', content): return True
    return False

def get_lang_name(lang_code):
    # Simple map for app_name translation
    names = {
        'am': 'የጀርባ ማጥፊያ',
        'ar': 'إزالة الخلفية',
        'az': 'Fon Silicisi',
        'be': 'Выдаленне фону',
        'bg': 'Премахване на фон',
        'bn': 'ব্যাকগ্রাউন্ড রিমুভার',
        'ca': 'Eliminador de fons',
        'cs': 'Odstraňovač pozadí',
        'da': 'Baggrundsfjerner',
        'de': 'Hintergrund-Entferner',
        'el': 'Αφαίρεση φόντου',
        'es': 'Eliminador de fondos',
        'et': 'Tausta eemaldaja',
        'eu': 'Atzeko planoa kentzeko',
        'fa': 'حذف پس‌زمینه',
        'fi': 'Taustan poisto',
        'fr': 'Effaceur de fond',
        'gl': 'Eliminador de fondos',
        'gu': 'બેકગ્રાઉન્ડ રીમુવર',
        'hi': 'बैकग्राउंड रिमूवर',
        'hr': 'Uklanjanje pozadine',
        'hu': 'Háttér eltávolító',
        'in': 'Penghapus Latar Belakang',
        'is': 'Bakgrunnur fjarlægja',
        'it': 'Rimozione sfondo',
        'iw': 'מסיר רקע',
        'ja': '背景削除',
        'ka': 'ფონის მოცილება',
        'kk': 'Фонды жою',
        'km': 'លុបផ្ទៃខាងក្រោយ',
        'kn': 'ಹಿನ್ನೆಲೆ ಹೋಗಲಾಡಿಸುವವನು',
        'ko': '배경 제거',
        'lo': 'ລຶບພື້ນຫຼັງ',
        'lt': 'Fono šalinimas',
        'lv': 'Fona noņemšana',
        'mk': 'Отстранувач на позадина',
        'ml': 'ബാക്ക്ഗ്രൗണ്ട് റിമൂവർ',
        'mn': 'Арын дэвсгэр арилгагч',
        'mr': 'बॅकग्राउंड रिमूव्हर',
        'ms': 'Pembuang Latar Belakang',
        'my': 'နောက်ခံဖျက်စက်',
        'ne': 'पृष्ठभूमि हटाउने',
        'nl': 'Achtergrond verwijderen',
        'no': 'Bakgrunnsfjerner',
        'pl': 'Usuwanie tła',
        'pt-rBR': 'Remover fundo',
        'ro': 'Eliminare fundal',
        'ru': 'Удаление фона',
        'si': 'පසුබිම ඉවත් කිරීම',
        'sk': 'Odstraňovač pozadia',
        'sl': 'Odstranjevalec ozadja',
        'sr': 'Уклањање позадине',
        'sv': 'Bakgrundsborttagare',
        'sw': 'Kiondoa mandhari',
        'ta': 'பின்னணி நீக்கி',
        'te': 'బ్యాక్‌గ్రౌండ్ రిమూవర్',
        'th': 'ลบพื้นหลัง',
        'tl': 'Tagatanggal ng Background',
        'tr': 'Arka Plan Silici',
        'uk': 'Видалення фону',
        'ur': 'بیک گراؤنڈ ریموور',
        'uz': 'Fonni o\'chirish',
        'vi': 'Xoá nền chuyên nghiệp',
        'zh-rCN': '背景消除',
        'zh-rTW': '背景消除',
        'zu': 'Isisusi sangemuva'
    }
    return names.get(lang_code, "Background Remover")

master_keys = get_master_strings()

for item in os.listdir(BASE_DIR):
    if item.startswith('values-') and item != 'values-night':
        lang = item.replace('values-', '')
        file_path = os.path.join(BASE_DIR, item, 'strings.xml')
        
        needs_fix = False
        existing_strings = {}
        
        if os.path.exists(file_path):
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                
                # If it's missing many keys or has corruption
                tree = ET.parse(file_path)
                root = tree.getroot()
                found_keys = set()
                for child in root:
                    if child.tag == 'string':
                        name = child.attrib.get('name')
                        val = child.text
                        found_keys.add(name)
                        if val and not is_corrupted(val) and '?' not in val:
                            existing_strings[name] = val
                
                master_key_names = {k[0] for k in master_keys}
                missing_keys = master_key_names - found_keys
                
                if missing_keys or is_corrupted(content) or len(found_keys) < 10:
                    needs_fix = True
            except:
                needs_fix = True
        else:
            needs_fix = True

        if needs_fix:
            print(f"Fixing {lang}...")
            # Create new XML
            root = ET.Element("resources")
            root.set("xmlns:ns0", "http://schemas.android.com/tools")
            root.set("ns0:ignore", "MissingTranslation")
            
            for name, master_val, translatable in master_keys:
                s = ET.SubElement(root, "string")
                s.set("name", name)
                if translatable == 'false':
                    s.set("translatable", "false")
                
                # Logic:
                # 1. Use existing if valid
                # 2. If app_name, use localized name
                # 3. Else use English (master)
                
                # Special case: Language names should always come from Master
                # as they are already localized there.
                is_language_name = name.startswith('language_') and name != 'language_title' and name != 'language_description'
                
                if is_language_name:
                    s.text = master_val
                elif name in existing_strings:
                    val = existing_strings[name]
                    # More aggressive corruption check
                    # If it has weird characters that aren't expected in most languages
                    if is_corrupted(val) or any(c in val for c in 'Øæð'):
                        fixed = fix_encoding(val)
                        if not is_corrupted(fixed) and not any(c in fixed for c in 'Øæð'):
                            s.text = fixed
                        else:
                            s.text = master_val
                    else:
                        s.text = val
                elif name == 'app_name':
                    s.text = f"MixClean - {get_lang_name(lang)}"
                else:
                    s.text = master_val
            
            # Save
            xml_str = ET.tostring(root, encoding='utf-8')
            # Pretty print manually because ET is ugly
            from xml.dom import minidom
            pretty_xml = minidom.parseString(xml_str).toprettyxml(indent="    ")
            
            # minidom adds extra newlines, fix them
            pretty_xml = "\n".join([line for line in pretty_xml.splitlines() if line.strip()])
            
            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(pretty_xml)

print("Fix complete.")
