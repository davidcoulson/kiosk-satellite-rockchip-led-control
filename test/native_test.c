// SPDX-License-Identifier: Apache-2.0
#include <assert.h>
#include <stdarg.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <errno.h>
static int opens, closes, calls, fail_request;
static unsigned long requests[8], values[8];
static int fake_open(const char *path,int flags,...) {
    assert(strcmp(path,"/dev/ledjni")==0);assert((flags&O_ACCMODE)==O_RDWR);opens++;return 9;
}
static int fake_close(int fd){assert(fd==9);closes++;return 0;}
static int fake_ioctl(int fd,unsigned long request,...) {
    assert(fd==9);va_list args;va_start(args,request);unsigned long value=va_arg(args,unsigned long);va_end(args);
    requests[calls]=request;values[calls++]=value;
    if((int)request==fail_request){errno=EACCES;return -1;}return 0;
}
#define open fake_open
#define close fake_close
#define ioctl fake_ioctl
#include "../native/led.c"
int main(void){
    assert(Java_me_jxl_kiosk_plugins_rockchip_NativeLed_probe(0,0)==0);assert(calls==0);
    assert(Java_me_jxl_kiosk_plugins_rockchip_NativeLed_write(0,0,15,7,0)==0);
    assert(calls==3&&requests[0]==0xa1&&requests[1]==0xa2&&requests[2]==0xa3);
    assert(values[0]==15&&values[1]==7&&values[2]==0);calls=0;
    assert(Java_me_jxl_kiosk_plugins_rockchip_NativeLed_write(0,0,256,0,0)==-EINVAL);assert(calls==0);
    fail_request=0xa2;assert(Java_me_jxl_kiosk_plugins_rockchip_NativeLed_write(0,0,1,2,3)==-EACCES);
    assert(calls==3&&requests[2]==0x99);calls=0;fail_request=0;
    assert(Java_me_jxl_kiosk_plugins_rockchip_NativeLed_off(0,0)==0);assert(requests[0]==0x99&&values[0]==0);
    assert(opens==closes);return 0;
}
