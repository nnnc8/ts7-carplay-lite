/*
 * TS7 Diagnostic v0.1
 * Pure NativeActivity diagnostic utility for low-memory Android head units.
 * Target device: TS7 / SL8141E / Android 8.1 / armeabi-v7a.
 * No network access. The app only reads local device capabilities, shows them,
 * copies the report to the clipboard, and attempts to save it to Download.
 */

typedef unsigned int size_t;
typedef int int32_t;
#include "jni.h"

__attribute__((visibility("hidden"), noinline))
void __aeabi_memcpy(void* dst, const void* src, size_t n) {
    volatile unsigned char* d=(volatile unsigned char*)dst;
    const volatile unsigned char* s=(const volatile unsigned char*)src;
    while(n--) *d++=*s++;
}
__attribute__((visibility("hidden"), noinline))
void __aeabi_memcpy4(void* dst, const void* src, size_t n) { __aeabi_memcpy(dst,src,n); }
__attribute__((visibility("hidden"), noinline))
void __aeabi_memcpy8(void* dst, const void* src, size_t n) { __aeabi_memcpy(dst,src,n); }


typedef struct ANativeActivityCallbacks ANativeActivityCallbacks;
typedef struct AAssetManager AAssetManager;
typedef struct ANativeActivity {
    ANativeActivityCallbacks* callbacks;
    JavaVM* vm;
    JNIEnv* env;
    jobject clazz;
    const char* internalDataPath;
    const char* externalDataPath;
    int32_t sdkVersion;
    void* instance;
    AAssetManager* assetManager;
    const char* obbPath;
} ANativeActivity;

#define REPORT_CAP 60000
static char g_report[REPORT_CAP];
static unsigned int g_len;

static void r_reset(void) { g_len = 0; g_report[0] = 0; }
static void r_ch(char c) {
    if (g_len + 1 >= REPORT_CAP) return;
    g_report[g_len++] = c;
    g_report[g_len] = 0;
}
static void r_str(const char* s) {
    if (!s) { s = "(null)"; }
    while (*s && g_len + 1 < REPORT_CAP) g_report[g_len++] = *s++;
    g_report[g_len] = 0;
}
static void r_u32(unsigned int v) {
    char t[16]; int n = 0;
    if (v == 0) { r_ch('0'); return; }
    while (v && n < 15) { t[n++] = (char)('0' + (v % 10)); v /= 10; }
    while (n) r_ch(t[--n]);
}
static void r_i32(int v) {
    if (v < 0) { r_ch('-'); r_u32((unsigned int)(-(v + 1)) + 1U); }
    else r_u32((unsigned int)v);
}
static void r_hex4(unsigned int v) {
    const char* h = "0123456789ABCDEF";
    r_str("0x");
    for (int i = 3; i >= 0; --i) r_ch(h[(v >> (i*4)) & 0xf]);
}
static void r_mb(jlong bytes) {
    if (bytes < 0) { r_str("?"); return; }
    unsigned int mb = (unsigned int)(((unsigned long long)bytes) >> 20);
    r_u32(mb); r_str(" MB");
}
static int s_eq_ci(const char* a, const char* b) {
    if (!a || !b) return 0;
    while (*a && *b) {
        char ca=*a++, cb=*b++;
        if (ca>='A'&&ca<='Z') ca += 'a'-'A';
        if (cb>='A'&&cb<='Z') cb += 'a'-'A';
        if (ca != cb) return 0;
    }
    return *a==0 && *b==0;
}
static int s_contains_ci(const char* s, const char* needle) {
    if (!s || !needle || !*needle) return 0;
    for (; *s; ++s) {
        const char* a=s; const char* b=needle;
        while (*a && *b) {
            char ca=*a, cb=*b;
            if (ca>='A'&&ca<='Z') ca += 'a'-'A';
            if (cb>='A'&&cb<='Z') cb += 'a'-'A';
            if (ca != cb) break;
            ++a; ++b;
        }
        if (!*b) return 1;
    }
    return 0;
}

static int clear_exc(JNIEnv* e) {
    jthrowable x = (*e)->ExceptionOccurred(e);
    if (x) {
        (*e)->ExceptionClear(e);
        (*e)->DeleteLocalRef(e, x);
        return 1;
    }
    return 0;
}

static void r_jstr(JNIEnv* e, jstring s) {
    if (!s) { r_str("(null)"); return; }
    const char* p = (*e)->GetStringUTFChars(e, s, 0);
    if (!p || clear_exc(e)) { r_str("(unavailable)"); return; }
    r_str(p);
    (*e)->ReleaseStringUTFChars(e, s, p);
}

static void r_kv_jstring(JNIEnv* e, const char* key, jstring value) {
    r_str(key); r_str(": "); r_jstr(e, value); r_ch('\n');
}

static jstring static_string_field(JNIEnv* e, const char* className, const char* field) {
    jclass c = (*e)->FindClass(e, className);
    if (!c || clear_exc(e)) return 0;
    jfieldID f = (*e)->GetStaticFieldID(e, c, field, "Ljava/lang/String;");
    if (!f || clear_exc(e)) { (*e)->DeleteLocalRef(e,c); return 0; }
    jstring s = (jstring)(*e)->GetStaticObjectField(e, c, f);
    if (clear_exc(e)) s=0;
    (*e)->DeleteLocalRef(e,c);
    return s;
}

static int static_int_field(JNIEnv* e, const char* className, const char* field, int fallback) {
    jclass c = (*e)->FindClass(e, className);
    if (!c || clear_exc(e)) return fallback;
    jfieldID f = (*e)->GetStaticFieldID(e, c, field, "I");
    if (!f || clear_exc(e)) { (*e)->DeleteLocalRef(e,c); return fallback; }
    jint v = (*e)->GetStaticIntField(e, c, f);
    if (clear_exc(e)) v=fallback;
    (*e)->DeleteLocalRef(e,c);
    return (int)v;
}

static jstring system_property(JNIEnv* e, const char* key) {
    jclass c = (*e)->FindClass(e, "android/os/SystemProperties");
    if (!c || clear_exc(e)) return 0;
    jmethodID m = (*e)->GetStaticMethodID(e,c,"get","(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (!m || clear_exc(e)) { (*e)->DeleteLocalRef(e,c); return 0; }
    jstring jk = (*e)->NewStringUTF(e,key);
    jstring jd = (*e)->NewStringUTF(e,"(unset)");
    jstring out = (jstring)(*e)->CallStaticObjectMethod(e,c,m,jk,jd);
    if (clear_exc(e)) out=0;
    (*e)->DeleteLocalRef(e,jk); (*e)->DeleteLocalRef(e,jd); (*e)->DeleteLocalRef(e,c);
    return out;
}

static jstring java_system_property(JNIEnv* e, const char* key) {
    jclass c = (*e)->FindClass(e, "java/lang/System");
    if (!c || clear_exc(e)) return 0;
    jmethodID m = (*e)->GetStaticMethodID(e,c,"getProperty","(Ljava/lang/String;)Ljava/lang/String;");
    if (!m || clear_exc(e)) { (*e)->DeleteLocalRef(e,c); return 0; }
    jstring jk = (*e)->NewStringUTF(e,key);
    jstring out = (jstring)(*e)->CallStaticObjectMethod(e,c,m,jk);
    if (clear_exc(e)) out=0;
    (*e)->DeleteLocalRef(e,jk); (*e)->DeleteLocalRef(e,c);
    return out;
}

static void report_build(JNIEnv* e, ANativeActivity* a) {
    r_reset();
    r_str("TS7 Diagnostic v0.1\n");
    r_str("Purpose: CarPlay performance / stability profiling\n");
    r_str("No network access is used by this app.\n\n");

    r_str("=== ANDROID / BUILD ===\n");
    r_str("NativeActivity SDK: "); r_i32(a->sdkVersion); r_ch('\n');
    r_str("SDK_INT: "); r_i32(static_int_field(e,"android/os/Build$VERSION","SDK_INT",-1)); r_ch('\n');
    jstring s;
    s=static_string_field(e,"android/os/Build$VERSION","RELEASE"); r_kv_jstring(e,"Android release",s); if(s)(*e)->DeleteLocalRef(e,s);
    const char* fields[][2] = {
        {"MODEL","Model"},{"MANUFACTURER","Manufacturer"},{"BRAND","Brand"},
        {"DEVICE","Device"},{"PRODUCT","Product"},{"HARDWARE","Hardware"},
        {"BOARD","Board"},{"DISPLAY","Build display"},{"FINGERPRINT","Fingerprint"}
    };
    for (unsigned i=0;i<sizeof(fields)/sizeof(fields[0]);++i) {
        s=static_string_field(e,"android/os/Build",fields[i][0]); r_kv_jstring(e,fields[i][1],s); if(s)(*e)->DeleteLocalRef(e,s);
    }
    const char* props[] = {
        "ro.product.cpu.abi","ro.product.cpu.abilist","ro.board.platform","ro.hardware",
        "ro.build.version.security_patch","ro.sf.lcd_density","ro.opengles.version",
        "ro.hardware.egl","debug.hwui.renderer","wifi.interface"
    };
    for (unsigned i=0;i<sizeof(props)/sizeof(props[0]);++i) {
        s=system_property(e,props[i]); r_str(props[i]); r_str(": "); r_jstr(e,s); r_ch('\n'); if(s)(*e)->DeleteLocalRef(e,s);
    }
    s=java_system_property(e,"os.arch"); r_kv_jstring(e,"Java os.arch",s); if(s)(*e)->DeleteLocalRef(e,s);
    s=java_system_property(e,"os.version"); r_kv_jstring(e,"Kernel os.version",s); if(s)(*e)->DeleteLocalRef(e,s);

    r_str("\n=== CPU / RAM ===\n");
    jclass rtCls=(*e)->FindClass(e,"java/lang/Runtime");
    if (rtCls && !clear_exc(e)) {
        jmethodID gm=(*e)->GetStaticMethodID(e,rtCls,"getRuntime","()Ljava/lang/Runtime;");
        jmethodID ap=(*e)->GetMethodID(e,rtCls,"availableProcessors","()I");
        jobject rt=(gm?(*e)->CallStaticObjectMethod(e,rtCls,gm):0);
        if (!clear_exc(e) && rt && ap) { r_str("CPU logical cores: "); r_i32((*e)->CallIntMethod(e,rt,ap)); r_ch('\n'); clear_exc(e); }
        if(rt)(*e)->DeleteLocalRef(e,rt); (*e)->DeleteLocalRef(e,rtCls);
    } else clear_exc(e);

    jclass actCls=(*e)->GetObjectClass(e,a->clazz);
    jmethodID getSvc=actCls?(*e)->GetMethodID(e,actCls,"getSystemService","(Ljava/lang/String;)Ljava/lang/Object;"):0;
    jobject am=0;
    if (getSvc) {
        jstring jact=(*e)->NewStringUTF(e,"activity");
        am=(*e)->CallObjectMethod(e,a->clazz,getSvc,jact); clear_exc(e); (*e)->DeleteLocalRef(e,jact);
    }
    if (am) {
        jclass miCls=(*e)->FindClass(e,"android/app/ActivityManager$MemoryInfo");
        jclass amCls=(*e)->GetObjectClass(e,am);
        if(miCls && amCls && !clear_exc(e)) {
            jmethodID ctor=(*e)->GetMethodID(e,miCls,"<init>","()V");
            jmethodID gm=(*e)->GetMethodID(e,amCls,"getMemoryInfo","(Landroid/app/ActivityManager$MemoryInfo;)V");
            jobject mi=ctor?(*e)->NewObject(e,miCls,ctor):0;
            if(mi && gm) {
                (*e)->CallVoidMethod(e,am,gm,mi);
                if(!clear_exc(e)) {
                    jfieldID ft=(*e)->GetFieldID(e,miCls,"totalMem","J");
                    jfieldID fa=(*e)->GetFieldID(e,miCls,"availMem","J");
                    jfieldID fl=(*e)->GetFieldID(e,miCls,"lowMemory","Z");
                    jfieldID fth=(*e)->GetFieldID(e,miCls,"threshold","J");
                    clear_exc(e);
                    if(ft){r_str("RAM total: ");r_mb((*e)->GetLongField(e,mi,ft));r_ch('\n');}
                    if(fa){r_str("RAM available: ");r_mb((*e)->GetLongField(e,mi,fa));r_ch('\n');}
                    if(fth){r_str("Low-memory threshold: ");r_mb((*e)->GetLongField(e,mi,fth));r_ch('\n');}
                    if(fl){r_str("System lowMemory flag: ");r_str((*e)->GetBooleanField(e,mi,fl)?"YES":"NO");r_ch('\n');}
                }
            }
            if(mi)(*e)->DeleteLocalRef(e,mi); (*e)->DeleteLocalRef(e,miCls); (*e)->DeleteLocalRef(e,amCls);
        } else clear_exc(e);
    }

    r_str("\n=== DISPLAY ===\n");
    if (actCls) {
        jmethodID gwm=(*e)->GetMethodID(e,actCls,"getWindowManager","()Landroid/view/WindowManager;");
        jobject wm=gwm?(*e)->CallObjectMethod(e,a->clazz,gwm):0; clear_exc(e);
        if(wm){
            jclass wmCls=(*e)->GetObjectClass(e,wm);
            jmethodID gd=(*e)->GetMethodID(e,wmCls,"getDefaultDisplay","()Landroid/view/Display;");
            jobject disp=gd?(*e)->CallObjectMethod(e,wm,gd):0; clear_exc(e);
            jclass dmCls=(*e)->FindClass(e,"android/util/DisplayMetrics");
            jmethodID ctor=dmCls?(*e)->GetMethodID(e,dmCls,"<init>","()V"):0;
            jobject dm=(dmCls&&ctor)?(*e)->NewObject(e,dmCls,ctor):0;
            if(disp&&dm){
                jclass dCls=(*e)->GetObjectClass(e,disp);
                jmethodID grm=(*e)->GetMethodID(e,dCls,"getRealMetrics","(Landroid/util/DisplayMetrics;)V");
                if(grm){(*e)->CallVoidMethod(e,disp,grm,dm);}
                if(!clear_exc(e)){
                    jfieldID fw=(*e)->GetFieldID(e,dmCls,"widthPixels","I");
                    jfieldID fh=(*e)->GetFieldID(e,dmCls,"heightPixels","I");
                    jfieldID fd=(*e)->GetFieldID(e,dmCls,"densityDpi","I"); clear_exc(e);
                    if(fw&&fh){r_str("Real resolution: ");r_i32((*e)->GetIntField(e,dm,fw));r_ch('x');r_i32((*e)->GetIntField(e,dm,fh));r_ch('\n');}
                    if(fd){r_str("Density DPI: ");r_i32((*e)->GetIntField(e,dm,fd));r_ch('\n');}
                }
                (*e)->DeleteLocalRef(e,dCls);
            }
            if(dm)(*e)->DeleteLocalRef(e,dm); if(dmCls)(*e)->DeleteLocalRef(e,dmCls); if(disp)(*e)->DeleteLocalRef(e,disp); (*e)->DeleteLocalRef(e,wmCls); (*e)->DeleteLocalRef(e,wm);
        }
    }

    r_str("\n=== STORAGE ===\n");
    if(actCls){
        jmethodID gf=(*e)->GetMethodID(e,actCls,"getFilesDir","()Ljava/io/File;");
        jobject f=gf?(*e)->CallObjectMethod(e,a->clazz,gf):0; clear_exc(e);
        if(f){
            jclass fc=(*e)->GetObjectClass(e,f);
            jmethodID gt=(*e)->GetMethodID(e,fc,"getTotalSpace","()J");
            jmethodID gfree=(*e)->GetMethodID(e,fc,"getFreeSpace","()J");
            if(gt){r_str("/data total: ");r_mb((*e)->CallLongMethod(e,f,gt));r_ch('\n');clear_exc(e);}
            if(gfree){r_str("/data free: ");r_mb((*e)->CallLongMethod(e,f,gfree));r_ch('\n');clear_exc(e);}
            (*e)->DeleteLocalRef(e,fc);(*e)->DeleteLocalRef(e,f);
        }
    }

    r_str("\n=== GRAPHICS / FEATURES ===\n");
    if(am){
        jclass amc=(*e)->GetObjectClass(e,am);
        jmethodID gdc=(*e)->GetMethodID(e,amc,"getDeviceConfigurationInfo","()Landroid/content/pm/ConfigurationInfo;");
        jobject ci=gdc?(*e)->CallObjectMethod(e,am,gdc):0; clear_exc(e);
        if(ci){
            jclass cic=(*e)->GetObjectClass(e,ci);
            jmethodID gg=(*e)->GetMethodID(e,cic,"getGlEsVersion","()Ljava/lang/String;");
            jstring gl=gg?(jstring)(*e)->CallObjectMethod(e,ci,gg):0; clear_exc(e);
            r_kv_jstring(e,"OpenGL ES",gl); if(gl)(*e)->DeleteLocalRef(e,gl);
            (*e)->DeleteLocalRef(e,cic);(*e)->DeleteLocalRef(e,ci);
        }
        (*e)->DeleteLocalRef(e,amc);
    }
    jobject pm=0;
    if(actCls){
        jmethodID gpm=(*e)->GetMethodID(e,actCls,"getPackageManager","()Landroid/content/pm/PackageManager;");
        pm=gpm?(*e)->CallObjectMethod(e,a->clazz,gpm):0; clear_exc(e);
    }
    if(pm){
        jclass pmc=(*e)->GetObjectClass(e,pm);
        jmethodID hf=(*e)->GetMethodID(e,pmc,"hasSystemFeature","(Ljava/lang/String;)Z");
        const char* feats[][2]={{"android.hardware.wifi","Wi-Fi"},{"android.hardware.bluetooth","Bluetooth"},{"android.hardware.bluetooth_le","Bluetooth LE"},{"android.hardware.usb.host","USB Host"},{"android.hardware.touchscreen","Touchscreen"}};
        for(unsigned i=0;i<sizeof(feats)/sizeof(feats[0]);++i){
            jstring jf=(*e)->NewStringUTF(e,feats[i][0]); jboolean yes=hf?(*e)->CallBooleanMethod(e,pm,hf,jf):0; clear_exc(e); (*e)->DeleteLocalRef(e,jf);
            r_str(feats[i][1]);r_str(": ");r_str(yes?"YES":"NO");r_ch('\n');
        }
        (*e)->DeleteLocalRef(e,pmc);
    }

    r_str("\n=== WI-FI / BLUETOOTH ===\n");
    if(getSvc){
        jstring jw=(*e)->NewStringUTF(e,"wifi"); jobject wifi=(*e)->CallObjectMethod(e,a->clazz,getSvc,jw); clear_exc(e); (*e)->DeleteLocalRef(e,jw);
        if(wifi){
            jclass wc=(*e)->GetObjectClass(e,wifi);
            jmethodID iwe=(*e)->GetMethodID(e,wc,"isWifiEnabled","()Z");
            jmethodID gci=(*e)->GetMethodID(e,wc,"getConnectionInfo","()Landroid/net/wifi/WifiInfo;");
            if(iwe){r_str("Wi-Fi enabled: ");r_str((*e)->CallBooleanMethod(e,wifi,iwe)?"YES":"NO");r_ch('\n');clear_exc(e);}
            jobject wi=gci?(*e)->CallObjectMethod(e,wifi,gci):0; clear_exc(e);
            if(wi){
                jclass wic=(*e)->GetObjectClass(e,wi);
                jmethodID gls=(*e)->GetMethodID(e,wic,"getLinkSpeed","()I");
                jmethodID gr=(*e)->GetMethodID(e,wic,"getRssi","()I");
                jmethodID gfq=(*e)->GetMethodID(e,wic,"getFrequency","()I");
                if(gls){r_str("Wi-Fi link speed: ");r_i32((*e)->CallIntMethod(e,wi,gls));r_str(" Mbps\n");clear_exc(e);}
                if(gr){r_str("Wi-Fi RSSI: ");r_i32((*e)->CallIntMethod(e,wi,gr));r_str(" dBm\n");clear_exc(e);}
                if(gfq){r_str("Wi-Fi frequency: ");r_i32((*e)->CallIntMethod(e,wi,gfq));r_str(" MHz\n");clear_exc(e);}
                (*e)->DeleteLocalRef(e,wic);(*e)->DeleteLocalRef(e,wi);
            }
            (*e)->DeleteLocalRef(e,wc);(*e)->DeleteLocalRef(e,wifi);
        } else r_str("Wi-Fi service: unavailable\n");
    }
    jclass bac=(*e)->FindClass(e,"android/bluetooth/BluetoothAdapter");
    if(bac && !clear_exc(e)){
        jmethodID gda=(*e)->GetStaticMethodID(e,bac,"getDefaultAdapter","()Landroid/bluetooth/BluetoothAdapter;");
        jobject ba=gda?(*e)->CallStaticObjectMethod(e,bac,gda):0; clear_exc(e);
        if(ba){jmethodID ie=(*e)->GetMethodID(e,bac,"isEnabled","()Z"); r_str("Bluetooth enabled: "); r_str((ie&&(*e)->CallBooleanMethod(e,ba,ie))?"YES":"NO");r_ch('\n');clear_exc(e);(*e)->DeleteLocalRef(e,ba);} else r_str("Bluetooth adapter: none\n");
        (*e)->DeleteLocalRef(e,bac);
    } else {clear_exc(e);r_str("Bluetooth API: unavailable\n");}

    r_str("\n=== USB DEVICES ===\n");
    if(getSvc){
        jstring ju=(*e)->NewStringUTF(e,"usb"); jobject um=(*e)->CallObjectMethod(e,a->clazz,getSvc,ju); clear_exc(e); (*e)->DeleteLocalRef(e,ju);
        if(um){
            jclass umc=(*e)->GetObjectClass(e,um); jmethodID gdl=(*e)->GetMethodID(e,umc,"getDeviceList","()Ljava/util/HashMap;");
            jobject map=gdl?(*e)->CallObjectMethod(e,um,gdl):0; clear_exc(e);
            if(map){
                jclass mc=(*e)->FindClass(e,"java/util/Map"); jmethodID vals=mc?(*e)->GetMethodID(e,mc,"values","()Ljava/util/Collection;"):0;
                jobject col=vals?(*e)->CallObjectMethod(e,map,vals):0; clear_exc(e);
                jclass cc=(*e)->FindClass(e,"java/util/Collection"); jmethodID ta=cc?(*e)->GetMethodID(e,cc,"toArray","()[Ljava/lang/Object;"):0;
                jobjectArray arr=(col&&ta)?(jobjectArray)(*e)->CallObjectMethod(e,col,ta):0; clear_exc(e);
                int n=arr?(*e)->GetArrayLength(e,arr):0; r_str("Connected USB device count: ");r_i32(n);r_ch('\n');
                for(int i=0;i<n;i++){
                    jobject d=(*e)->GetObjectArrayElement(e,arr,i); if(!d)continue; jclass dc=(*e)->GetObjectClass(e,d);
                    jmethodID gvid=(*e)->GetMethodID(e,dc,"getVendorId","()I"); jmethodID gpid=(*e)->GetMethodID(e,dc,"getProductId","()I");
                    jmethodID gcl=(*e)->GetMethodID(e,dc,"getDeviceClass","()I"); jmethodID gsc=(*e)->GetMethodID(e,dc,"getDeviceSubclass","()I");
                    jmethodID gpr=(*e)->GetMethodID(e,dc,"getDeviceProtocol","()I"); jmethodID gdn=(*e)->GetMethodID(e,dc,"getDeviceName","()Ljava/lang/String;");
                    r_str("USB[");r_i32(i);r_str("] VID:PID="); if(gvid)r_hex4((*e)->CallIntMethod(e,d,gvid)); else r_str("?"); r_ch(':'); if(gpid)r_hex4((*e)->CallIntMethod(e,d,gpid)); else r_str("?"); clear_exc(e);
                    if(gcl){r_str(" class=");r_i32((*e)->CallIntMethod(e,d,gcl));} if(gsc){r_str(" sub=");r_i32((*e)->CallIntMethod(e,d,gsc));} if(gpr){r_str(" proto=");r_i32((*e)->CallIntMethod(e,d,gpr));} clear_exc(e);r_ch('\n');
                    if(gdn){jstring dn=(jstring)(*e)->CallObjectMethod(e,d,gdn);clear_exc(e);r_str("  path: ");r_jstr(e,dn);r_ch('\n');if(dn)(*e)->DeleteLocalRef(e,dn);}
                    (*e)->DeleteLocalRef(e,dc);(*e)->DeleteLocalRef(e,d);
                }
                if(arr)(*e)->DeleteLocalRef(e,arr); if(cc)(*e)->DeleteLocalRef(e,cc); if(col)(*e)->DeleteLocalRef(e,col); if(mc)(*e)->DeleteLocalRef(e,mc);
                (*e)->DeleteLocalRef(e,map);
            } else r_str("USB device list unavailable\n");
            (*e)->DeleteLocalRef(e,umc);(*e)->DeleteLocalRef(e,um);
        } else r_str("USB service unavailable\n");
    }

    r_str("\n=== H.264 / AVC DECODERS ===\n");
    int avcCount=0;
    jclass mcl=(*e)->FindClass(e,"android/media/MediaCodecList");
    jclass mci=(*e)->FindClass(e,"android/media/MediaCodecInfo");
    if(mcl&&mci&&!clear_exc(e)){
        jmethodID gcc=(*e)->GetStaticMethodID(e,mcl,"getCodecCount","()I");
        jmethodID gcia=(*e)->GetStaticMethodID(e,mcl,"getCodecInfoAt","(I)Landroid/media/MediaCodecInfo;");
        jmethodID ien=(*e)->GetMethodID(e,mci,"isEncoder","()Z");
        jmethodID gst=(*e)->GetMethodID(e,mci,"getSupportedTypes","()[Ljava/lang/String;");
        jmethodID gn=(*e)->GetMethodID(e,mci,"getName","()Ljava/lang/String;");
        jmethodID gcap=(*e)->GetMethodID(e,mci,"getCapabilitiesForType","(Ljava/lang/String;)Landroid/media/MediaCodecInfo$CodecCapabilities;");
        int count=gcc?(*e)->CallStaticIntMethod(e,mcl,gcc):0; if(clear_exc(e))count=0;
        r_str("All codec entries: ");r_i32(count);r_ch('\n');
        for(int i=0;i<count;i++){
            jobject info=gcia?(*e)->CallStaticObjectMethod(e,mcl,gcia,i):0; if(clear_exc(e)||!info)continue;
            if(ien&&(*e)->CallBooleanMethod(e,info,ien)){clear_exc(e);(*e)->DeleteLocalRef(e,info);continue;} clear_exc(e);
            jobjectArray types=gst?(jobjectArray)(*e)->CallObjectMethod(e,info,gst):0; if(clear_exc(e))types=0;
            int tn=types?(*e)->GetArrayLength(e,types):0; int isAvc=0;
            for(int t=0;t<tn;t++){jstring jt=(jstring)(*e)->GetObjectArrayElement(e,types,t);const char* p=jt?(*e)->GetStringUTFChars(e,jt,0):0;if(p&&s_eq_ci(p,"video/avc"))isAvc=1;if(p)(*e)->ReleaseStringUTFChars(e,jt,p);if(jt)(*e)->DeleteLocalRef(e,jt);if(isAvc)break;}
            if(types)(*e)->DeleteLocalRef(e,types);
            if(isAvc){
                avcCount++;
                jstring name=gn?(jstring)(*e)->CallObjectMethod(e,info,gn):0; clear_exc(e);
                r_str("Decoder ");r_i32(avcCount);r_str(": ");
                const char* np=name?(*e)->GetStringUTFChars(e,name,0):0;
                if(np){r_str(np); r_str((s_contains_ci(np,"google")||s_contains_ci(np,"ffmpeg")||s_contains_ci(np,"software"))?"  [likely software]":"  [likely hardware/vendor]");(*e)->ReleaseStringUTFChars(e,name,np);} else r_str("(name unavailable)");
                r_ch('\n');
                if(gcap){
                    jstring avc=(*e)->NewStringUTF(e,"video/avc"); jobject cap=(*e)->CallObjectMethod(e,info,gcap,avc); if(clear_exc(e))cap=0; (*e)->DeleteLocalRef(e,avc);
                    if(cap){
                        jclass capc=(*e)->GetObjectClass(e,cap); jmethodID gvc=(*e)->GetMethodID(e,capc,"getVideoCapabilities","()Landroid/media/MediaCodecInfo$VideoCapabilities;");
                        jobject vc=gvc?(*e)->CallObjectMethod(e,cap,gvc):0; if(clear_exc(e))vc=0;
                        if(vc){
                            jclass vcc=(*e)->GetObjectClass(e,vc);
                            const char* methods[][2]={{"getSupportedWidths","width"},{"getSupportedHeights","height"},{"getSupportedFrameRates","fps"},{"getBitrateRange","bitrate"}};
                            for(unsigned q=0;q<sizeof(methods)/sizeof(methods[0]);q++){
                                jmethodID gm=(*e)->GetMethodID(e,vcc,methods[q][0],"()Landroid/util/Range;"); jobject range=gm?(*e)->CallObjectMethod(e,vc,gm):0; if(clear_exc(e))range=0;
                                if(range){jclass oc=(*e)->GetObjectClass(e,range);jmethodID ts=(*e)->GetMethodID(e,oc,"toString","()Ljava/lang/String;");jstring rs=ts?(jstring)(*e)->CallObjectMethod(e,range,ts):0;clear_exc(e);r_str("  ");r_str(methods[q][1]);r_str(": ");r_jstr(e,rs);r_ch('\n');if(rs)(*e)->DeleteLocalRef(e,rs);(*e)->DeleteLocalRef(e,oc);(*e)->DeleteLocalRef(e,range);}
                            }
                            (*e)->DeleteLocalRef(e,vcc);(*e)->DeleteLocalRef(e,vc);
                        }
                        (*e)->DeleteLocalRef(e,capc);(*e)->DeleteLocalRef(e,cap);
                    }
                }
                if(name)(*e)->DeleteLocalRef(e,name);
            }
            (*e)->DeleteLocalRef(e,info);
        }
        r_str("H.264 decoder count: ");r_i32(avcCount);r_ch('\n');
        jclass mc=(*e)->FindClass(e,"android/media/MediaCodec");
        if(mc&&!clear_exc(e)){
            jmethodID cd=(*e)->GetStaticMethodID(e,mc,"createDecoderByType","(Ljava/lang/String;)Landroid/media/MediaCodec;");
            jstring avc=(*e)->NewStringUTF(e,"video/avc"); jobject codec=cd?(*e)->CallStaticObjectMethod(e,mc,cd,avc):0;
            int failed=clear_exc(e); (*e)->DeleteLocalRef(e,avc);
            if(codec&&!failed){jmethodID rel=(*e)->GetMethodID(e,mc,"release","()V");r_str("H.264 decoder instantiate test: PASS\n");if(rel)(*e)->CallVoidMethod(e,codec,rel);clear_exc(e);(*e)->DeleteLocalRef(e,codec);}else r_str("H.264 decoder instantiate test: FAIL\n");
            (*e)->DeleteLocalRef(e,mc);
        } else {clear_exc(e);r_str("H.264 instantiate test: API unavailable\n");}
        (*e)->DeleteLocalRef(e,mci);(*e)->DeleteLocalRef(e,mcl);
    } else {clear_exc(e);r_str("MediaCodecList unavailable\n"); if(mci)(*e)->DeleteLocalRef(e,mci);if(mcl)(*e)->DeleteLocalRef(e,mcl);}

    r_str("\n=== CARPLAY-RELEVANT SUMMARY ===\n");
    if(avcCount>0) r_str("H.264 decode path exists. Decoder name/capability above determines whether vendor HW decode is exposed.\n");
    else r_str("WARNING: no H.264 decoder was enumerated. This would be a major blocker.\n");
    r_str("For the next test, connect the iPhone/CarPlay USB path before launching this app so USB VID/PID is captured.\n");

    if(pm)(*e)->DeleteLocalRef(e,pm);
    if(am)(*e)->DeleteLocalRef(e,am);
    if(actCls)(*e)->DeleteLocalRef(e,actCls);
}

static int save_report(JNIEnv* e) {
    jclass envc=(*e)->FindClass(e,"android/os/Environment");
    if(!envc||clear_exc(e))return 0;
    jmethodID gep=(*e)->GetStaticMethodID(e,envc,"getExternalStoragePublicDirectory","(Ljava/lang/String;)Ljava/io/File;");
    jstring dl=(*e)->NewStringUTF(e,"Download"); jobject dir=gep?(*e)->CallStaticObjectMethod(e,envc,gep,dl):0; int bad=clear_exc(e); (*e)->DeleteLocalRef(e,dl); (*e)->DeleteLocalRef(e,envc);
    if(bad||!dir)return 0;
    jclass fc=(*e)->FindClass(e,"java/io/File"); jmethodID ctor=fc?(*e)->GetMethodID(e,fc,"<init>","(Ljava/io/File;Ljava/lang/String;)V"):0;
    jstring fn=(*e)->NewStringUTF(e,"TS7-Diagnostic.txt"); jobject file=(fc&&ctor)?(*e)->NewObject(e,fc,ctor,dir,fn):0; clear_exc(e); (*e)->DeleteLocalRef(e,fn); (*e)->DeleteLocalRef(e,dir);
    if(!file){if(fc)(*e)->DeleteLocalRef(e,fc);return 0;}
    jclass fos=(*e)->FindClass(e,"java/io/FileOutputStream"); jmethodID fctor=fos?(*e)->GetMethodID(e,fos,"<init>","(Ljava/io/File;)V"):0;
    jobject out=(fos&&fctor)?(*e)->NewObject(e,fos,fctor,file):0; if(clear_exc(e)||!out){if(fos)(*e)->DeleteLocalRef(e,fos);if(fc)(*e)->DeleteLocalRef(e,fc);(*e)->DeleteLocalRef(e,file);return 0;}
    jstring js=(*e)->NewStringUTF(e,g_report); jclass sc=(*e)->FindClass(e,"java/lang/String"); jmethodID gb=sc?(*e)->GetMethodID(e,sc,"getBytes","(Ljava/lang/String;)[B"):0; jstring utf=(*e)->NewStringUTF(e,"UTF-8");
    jbyteArray data=(js&&gb)?(jbyteArray)(*e)->CallObjectMethod(e,js,gb,utf):0; if(clear_exc(e))data=0; (*e)->DeleteLocalRef(e,utf);
    int ok=0;
    if(data){jmethodID wr=(*e)->GetMethodID(e,fos,"write","([B)V");jmethodID cl=(*e)->GetMethodID(e,fos,"close","()V");if(wr){(*e)->CallVoidMethod(e,out,wr,data);if(!clear_exc(e))ok=1;}if(cl){(*e)->CallVoidMethod(e,out,cl);clear_exc(e);}(*e)->DeleteLocalRef(e,data);}
    if(js)(*e)->DeleteLocalRef(e,js);if(sc)(*e)->DeleteLocalRef(e,sc);(*e)->DeleteLocalRef(e,out);(*e)->DeleteLocalRef(e,fos);(*e)->DeleteLocalRef(e,file);(*e)->DeleteLocalRef(e,fc);
    return ok;
}

static int copy_clipboard(JNIEnv* e, ANativeActivity* a) {
    jclass ac=(*e)->GetObjectClass(e,a->clazz); if(!ac)return 0;
    jmethodID gs=(*e)->GetMethodID(e,ac,"getSystemService","(Ljava/lang/String;)Ljava/lang/Object;");
    jstring key=(*e)->NewStringUTF(e,"clipboard"); jobject cm=gs?(*e)->CallObjectMethod(e,a->clazz,gs,key):0; clear_exc(e);(*e)->DeleteLocalRef(e,key);(*e)->DeleteLocalRef(e,ac);if(!cm)return 0;
    jclass cd=(*e)->FindClass(e,"android/content/ClipData"); jmethodID np=cd?(*e)->GetStaticMethodID(e,cd,"newPlainText","(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;"):0;
    jstring label=(*e)->NewStringUTF(e,"TS7 Diagnostic"); jstring text=(*e)->NewStringUTF(e,g_report); jobject clip=(cd&&np)?(*e)->CallStaticObjectMethod(e,cd,np,label,text):0; clear_exc(e);(*e)->DeleteLocalRef(e,label);(*e)->DeleteLocalRef(e,text);
    jclass cmc=(*e)->GetObjectClass(e,cm); jmethodID sp=cmc?(*e)->GetMethodID(e,cmc,"setPrimaryClip","(Landroid/content/ClipData;)V"):0; int ok=0;
    if(clip&&sp){(*e)->CallVoidMethod(e,cm,sp,clip);ok=!clear_exc(e);} if(clip)(*e)->DeleteLocalRef(e,clip);if(cmc)(*e)->DeleteLocalRef(e,cmc);if(cd)(*e)->DeleteLocalRef(e,cd);(*e)->DeleteLocalRef(e,cm);return ok;
}

static void show_ui(JNIEnv* e, ANativeActivity* a, int saved, int copied) {
    char finalText[REPORT_CAP];
    unsigned int n=0;
    const char* p="TS7 診斷工具 v0.1\n";
    while(*p&&n+1<REPORT_CAP)finalText[n++]=*p++;
    p=copied?"報告已複製到剪貼簿。":"剪貼簿複製失敗，可長按下方文字選取。";while(*p&&n+1<REPORT_CAP)finalText[n++]=*p++;
    if(saved)p="\n檔案已儲存：Download/TS7-Diagnostic.txt\n\n";else p="\nDownload 儲存失敗，但畫面報告仍可使用。\n\n";while(*p&&n+1<REPORT_CAP)finalText[n++]=*p++;
    for(unsigned i=0;i<g_len&&n+1<REPORT_CAP;i++)finalText[n++]=g_report[i]; finalText[n]=0;

    jclass sv=(*e)->FindClass(e,"android/widget/ScrollView"); jclass tv=(*e)->FindClass(e,"android/widget/TextView");
    if(!sv||!tv||clear_exc(e))return;
    jmethodID sc=(*e)->GetMethodID(e,sv,"<init>","(Landroid/content/Context;)V"); jmethodID tc=(*e)->GetMethodID(e,tv,"<init>","(Landroid/content/Context;)V");
    jobject scroll=sc?(*e)->NewObject(e,sv,sc,a->clazz):0; jobject text=tc?(*e)->NewObject(e,tv,tc,a->clazz):0;
    if(!scroll||!text||clear_exc(e))return;
    jmethodID st=(*e)->GetMethodID(e,tv,"setText","(Ljava/lang/CharSequence;)V"); jmethodID sel=(*e)->GetMethodID(e,tv,"setTextIsSelectable","(Z)V"); jmethodID pad=(*e)->GetMethodID(e,tv,"setPadding","(IIII)V"); jmethodID color=(*e)->GetMethodID(e,tv,"setTextColor","(I)V");
    jstring js=(*e)->NewStringUTF(e,finalText); if(st)(*e)->CallVoidMethod(e,text,st,js);clear_exc(e);if(sel)(*e)->CallVoidMethod(e,text,sel,1);clear_exc(e);if(pad)(*e)->CallVoidMethod(e,text,pad,28,24,28,36);clear_exc(e);if(color)(*e)->CallVoidMethod(e,text,color,(jint)0xff111111);clear_exc(e);(*e)->DeleteLocalRef(e,js);
    jclass vg=(*e)->FindClass(e,"android/view/ViewGroup"); jmethodID av=vg?(*e)->GetMethodID(e,vg,"addView","(Landroid/view/View;)V"):0;if(av)(*e)->CallVoidMethod(e,scroll,av,text);clear_exc(e);
    jclass view=(*e)->FindClass(e,"android/view/View"); jmethodID bg=view?(*e)->GetMethodID(e,view,"setBackgroundColor","(I)V"):0;if(bg)(*e)->CallVoidMethod(e,scroll,bg,(jint)0xfff5f5f5);clear_exc(e);
    jclass ac=(*e)->GetObjectClass(e,a->clazz); jmethodID cv=ac?(*e)->GetMethodID(e,ac,"setContentView","(Landroid/view/View;)V"):0;if(cv)(*e)->CallVoidMethod(e,a->clazz,cv,scroll);clear_exc(e);
    if(ac)(*e)->DeleteLocalRef(e,ac);if(view)(*e)->DeleteLocalRef(e,view);if(vg)(*e)->DeleteLocalRef(e,vg);(*e)->DeleteLocalRef(e,text);(*e)->DeleteLocalRef(e,scroll);(*e)->DeleteLocalRef(e,tv);(*e)->DeleteLocalRef(e,sv);
}

__attribute__((visibility("default")))
void ANativeActivity_onCreate(ANativeActivity* activity, void* savedState, size_t savedStateSize) {
    (void)savedState; (void)savedStateSize;
    if(!activity || !activity->env || !activity->clazz) return;
    JNIEnv* e=activity->env;
    report_build(e,activity);
    int saved=save_report(e);
    int copied=copy_clipboard(e,activity);
    show_ui(e,activity,saved,copied);
}
