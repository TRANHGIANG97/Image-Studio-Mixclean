def fix_mojibake(text):
    try:
        # If it's already correctly UTF-8, this might fail or do nothing
        return text.encode('cp1252').decode('utf-8')
    except:
        return text

# Test with the Arabic app_name: MixClean - Ø¥Ø²Ø§Ù„Ø© Ø§Ù„Ø®Ù„Ù ÙŠØ©
test_str = "MixClean - Ø¥Ø²Ø§Ù„Ø© Ø§Ù„Ø®Ù„Ù ÙŠØ©"
fixed = fix_mojibake(test_str)
print(f"Original: {test_str}")
print(f"Fixed:    {fixed}")
