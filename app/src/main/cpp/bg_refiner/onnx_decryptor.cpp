#include <jni.h>
#include <vector>

// Helper logic to decrypt the ONNX model byte array in-memory using an obfuscated rotating XOR key.
jbyteArray decrypt_internal(JNIEnv* env, jbyteArray encrypted_data) {
    if (encrypted_data == nullptr) {
        return nullptr;
    }

    // Obfuscated key (XORed with 0x5A) for highly secure 32-byte random key
    unsigned char obfuscated_key[] = {
        202, 18, 184, 16, 42, 13, 255, 19, 59, 221, 
        130, 3, 124, 87, 159, 93, 169, 22, 123, 197, 
        72, 76, 225, 153, 236, 76, 124, 90, 9, 46, 
        149, 190
    };
    int key_len = sizeof(obfuscated_key);
    
    // Recover the original key in volatile memory at runtime
    std::vector<unsigned char> key(key_len);
    for (int i = 0; i < key_len; i++) {
        key[i] = obfuscated_key[i] ^ 0x5A;
    }

    const jsize len = env->GetArrayLength(encrypted_data);
    if (len <= 0) {
        return env->NewByteArray(0);
    }

    jbyte* body = env->GetByteArrayElements(encrypted_data, nullptr);
    if (body == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }

    jbyteArray decrypted_array = env->NewByteArray(len);
    if (decrypted_array == nullptr || env->ExceptionCheck()) {
        env->ReleaseByteArrayElements(encrypted_data, body, JNI_ABORT);
        return nullptr;
    }

    jbyte* decrypted_body = env->GetByteArrayElements(decrypted_array, nullptr);
    if (decrypted_body == nullptr || env->ExceptionCheck()) {
        env->ReleaseByteArrayElements(encrypted_data, body, JNI_ABORT);
        return nullptr;
    }

    // Highly optimized C++ machine code loop for XOR decryption
    for (jsize i = 0; i < len; i++) {
        decrypted_body[i] = body[i] ^ key[i % key_len];
    }

    // Anti-memory-dump: instantly overwrite key buffers on RAM
    for (int i = 0; i < key_len; i++) {
        key[i] = 0;
        obfuscated_key[i] = 0;
    }

    // Release JNI resources and commit byte array changes back to JVM
    env->ReleaseByteArrayElements(encrypted_data, body, JNI_ABORT);
    env->ReleaseByteArrayElements(decrypted_array, decrypted_body, 0);

    return decrypted_array;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_abizer_1r_quickedit_backgroundremove_ModNetBackgroundRemoverRepository_decryptModelNative(
    JNIEnv* env, jobject thiz, jbyteArray encrypted_data
) {
    return decrypt_internal(env, encrypted_data);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_toshiba_modnet_BackgroundRemover_decryptModelNative(
    JNIEnv* env, jobject thiz, jbyteArray encrypted_data
) {
    return decrypt_internal(env, encrypted_data);
}
