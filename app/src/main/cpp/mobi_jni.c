/* Fwip: hands the parts of a MOBI/AZW3 book, rebuilt by libmobi, to Kotlin. GPL-3.0-or-later. */
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include "mobi.h"

#define OK 0
#define CANNOT_READ 1
#define DRM 2
#define CANNOT_PARSE 3
#define STOPPED 4

static int send_part(JNIEnv *env, jobject sink, jmethodID method, const char *name, const unsigned char *data, size_t size) {
    jstring jname = (*env)->NewStringUTF(env, name);
    jbyteArray bytes = (*env)->NewByteArray(env, (jsize) size);
    if (jname == NULL || bytes == NULL) return STOPPED;
    (*env)->SetByteArrayRegion(env, bytes, 0, (jsize) size, (const jbyte *) data);
    (*env)->CallVoidMethod(env, sink, method, jname, bytes);
    (*env)->DeleteLocalRef(env, jname);
    (*env)->DeleteLocalRef(env, bytes);
    return (*env)->ExceptionCheck(env) ? STOPPED : OK;
}

JNIEXPORT jint JNICALL
Java_io_github_christiandonatti_fwip_data_MobiBooks_nativeParts(JNIEnv *env, jclass cls, jstring jpath, jobject sink) {
    jclass sink_class = (*env)->GetObjectClass(env, sink);
    jmethodID method = (*env)->GetMethodID(env, sink_class, "part", "(Ljava/lang/String;[B)V");
    if (method == NULL) return STOPPED;

    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    MOBIData *m = mobi_init();
    if (m == NULL) { (*env)->ReleaseStringUTFChars(env, jpath, path); return CANNOT_READ; }
    MOBI_RET ret = mobi_load_filename(m, path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    if (ret != MOBI_SUCCESS) { mobi_free(m); return CANNOT_READ; }
    if (mobi_is_encrypted(m)) { mobi_free(m); return DRM; }

    MOBIRawml *rawml = mobi_init_rawml(m);
    if (rawml == NULL) { mobi_free(m); return CANNOT_PARSE; }
    ret = mobi_parse_rawml(rawml, m);
    if (ret != MOBI_SUCCESS) { mobi_free_rawml(rawml); mobi_free(m); return CANNOT_PARSE; }

    int result = OK;
    char name[64];
    for (MOBIPart *p = rawml->markup; p != NULL && result == OK; p = p->next) {
        MOBIFileMeta meta = mobi_get_filemeta_by_type(p->type);
        snprintf(name, sizeof(name), "part%05zu.%s", p->uid, meta.extension);
        result = send_part(env, sink, method, name, p->data, p->size);
    }
    /* The first flow part is the raw HTML, already in the markup. */
    for (MOBIPart *p = rawml->flow ? rawml->flow->next : NULL; p != NULL && result == OK; p = p->next) {
        MOBIFileMeta meta = mobi_get_filemeta_by_type(p->type);
        snprintf(name, sizeof(name), "flow%05zu.%s", p->uid, meta.extension);
        result = send_part(env, sink, method, name, p->data, p->size);
    }
    for (MOBIPart *p = rawml->resources; p != NULL && result == OK; p = p->next) {
        if (p->size == 0) continue;
        MOBIFileMeta meta = mobi_get_filemeta_by_type(p->type);
        if (meta.type == T_OPF) snprintf(name, sizeof(name), "content.opf");
        else snprintf(name, sizeof(name), "resource%05zu.%s", p->uid, meta.extension);
        result = send_part(env, sink, method, name, p->data, p->size);
    }
    mobi_free_rawml(rawml);
    mobi_free(m);
    return result;
}
