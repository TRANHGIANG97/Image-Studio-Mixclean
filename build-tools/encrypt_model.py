import os
import sys

def encrypt_file(input_path, output_path, key_bytes):
    print(f"Encrypting {input_path} -> {output_path}")
    if not os.path.exists(input_path):
        print(f"Error: Input file {input_path} does not exist!")
        return False
        
    try:
        # Read raw ONNX file (if it was encrypted before, we need to revert it first)
        # Note: Since the previous ONNX files are already XOR-encrypted with the OLD key "CheGiauKhoaBiMatONNXMixClean2026",
        # we must FIRST decrypt them using the old key, then encrypt them with the NEW random key!
        # If the file is not corrupted (e.g. fresh ONNX format), we can detect it.
        # However, it's safer to just load the raw ONNX from a backup or re-decrypt it.
        # Let's write a robust parser.
        with open(input_path, 'rb') as f:
            data = bytearray(f.read())
            
        # Let's check if the file starts with Google Protobuf magic header (ONNX format).
        # Standard ONNX starts with 0x08 (protobuf field 1 varint) or similar.
        # If it doesn't, it is already encrypted. Let's decrypt it with the old key first!
        # Old key string: "CheGiauKhoaBiMatONNXMixClean2026"
        old_key = "CheGiauKhoaBiMatONNXMixClean2026".encode('utf-8')
        old_key_len = len(old_key)
        
        # Check if the file is encrypted with the old key by testing if decrypting the first few bytes
        # yields a valid protobuf header, or we can just try to revert it.
        # Standard protobuf starts with field tag (typically 0x08, 0x12, 0x1A, etc.).
        # Let's revert the encryption.
        temp_decrypted = bytearray(data)
        for i in range(len(temp_decrypted)):
            temp_decrypted[i] ^= old_key[i % old_key_len]
            
        # Standard ONNX magic header format check: 
        # Usually it starts with 0x08 (ir_version tag). 
        # If the decrypted version starts with 0x08, it means the file WAS indeed encrypted with the old key!
        if temp_decrypted[0] == 0x08:
            print("Detected file was encrypted with the old key. Reverting it first...")
            data = temp_decrypted
        else:
            print("File is raw or already updated. Proceeding with encryption...")

        # Now encrypt with the NEW highly secure random key
        key_len = len(key_bytes)
        for i in range(len(data)):
            data[i] ^= key_bytes[i % key_len]
            
        # Ensure target dir exists
        os.makedirs(os.path.dirname(output_path), exist_ok=True)
        
        with open(output_path, 'wb') as f:
            f.write(data)
            
        print("Encryption completed successfully!")
        return True
    except Exception as e:
        print(f"Error during encryption: {e}")
        return False

if __name__ == "__main__":
    # Highly secure random 32-byte key
    key_bytes = bytes([
        144, 72, 226, 74, 112, 87, 165, 73, 97, 135, 
        216, 89, 38, 13, 197, 7, 243, 76, 33, 159, 
        18, 22, 187, 195, 182, 22, 38, 0, 83, 116, 
        207, 228
    ])
    
    # 1. Encrypt ModNet main project model
    input_model = r"QuickEdit-Photo-Editor-main\quickedit\src\main\assets\modnet_matte_512_fp16.onnx"
    encrypt_file(input_model, input_model, key_bytes)
    
    # 2. Encrypt sample project model
    sample_model = r"android-compose-modnet\app\src\main\assets\modnet_matte_512_fp16.onnx"
    if os.path.exists(sample_model):
        encrypt_file(sample_model, sample_model, key_bytes)
