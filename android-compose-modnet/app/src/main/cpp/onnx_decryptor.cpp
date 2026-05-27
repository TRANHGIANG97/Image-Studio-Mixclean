#include <jni.h>
#include <vector>

static jbyteArray decrypt_internal(JNIEnv* env, jbyteArray encrypted_data) {
    if (encrypted_data == nullptr) {
        return nullptr;
    }

    unsigned char obfuscated_key[] = {
        202, 18, 184, 16, 42, 13, 255, 19, 59, 221,
        130, 3, 124, 87, 159, 93, 169, 22, 123, 197,
        72, 76, 225, 153, 236, 76, 124, 90, 9, 46,
        149, 190
    };
    const int key_len = sizeof(obfuscated_key);

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

    for (jsize i = 0; i < len; i++) {
        decrypted_body[i] = body[i] ^ key[i % key_len];
    }

    for (int i = 0; i < key_len; i++) {
        key[i] = 0;
        obfuscated_key[i] = 0;
    }

    env->ReleaseByteArrayElements(encrypted_data, body, JNI_ABORT);
    env->ReleaseByteArrayElements(decrypted_array, decrypted_body, 0);
    return decrypted_array;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_toshiba_modnet_BackgroundRemover_decryptModelNative(
    JNIEnv* env, jobject, jbyteArray encrypted_data
) {
    return decrypt_internal(env, encrypted_data);
}
