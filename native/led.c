// SPDX-License-Identifier: Apache-2.0
// Interoperability facts: davidcoulson/kiosk-satellite issue #494 and
// maxlyth/ha-paneld's vendor protocol research. No vendor code is included.
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <unistd.h>

static int open_led(void) {
    int fd;
    do { fd = open("/dev/ledjni", O_RDWR | O_CLOEXEC | O_NOCTTY); }
    while (fd < 0 && errno == EINTR);
    return fd;
}

JNIEXPORT jint JNICALL
Java_me_jxl_kiosk_plugins_rockchip_NativeLed_probe(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    int fd = open_led();
    if (fd < 0) return -errno;
    close(fd);
    return 0;
}

JNIEXPORT jint JNICALL
Java_me_jxl_kiosk_plugins_rockchip_NativeLed_write(JNIEnv *env, jclass cls, jint red, jint green, jint blue) {
    (void)env; (void)cls;
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) return -EINVAL;
    int fd = open_led();
    if (fd < 0) return -errno;
    const unsigned long requests[] = {0xa1, 0xa2, 0xa3};
    const unsigned long values[] = {(unsigned long)red, (unsigned long)green, (unsigned long)blue};
    int error = 0;
    for (int i = 0; i < 3; ++i) {
        if (ioctl(fd, requests[i], values[i]) < 0) { error = -errno; break; }
    }
    if (error) ioctl(fd, 0x99, 0UL);
    close(fd);
    return error;
}

JNIEXPORT jint JNICALL
Java_me_jxl_kiosk_plugins_rockchip_NativeLed_off(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    int fd = open_led();
    if (fd < 0) return -errno;
    int result = ioctl(fd, 0x99, 0UL) < 0 ? -errno : 0;
    close(fd);
    return result;
}
