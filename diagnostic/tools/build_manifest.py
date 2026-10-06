#!/usr/bin/env python3
import struct
from dataclasses import dataclass
from pathlib import Path

ANDROID_NS='http://schemas.android.com/apk/res/android'
NO=0xffffffff
RES={
    'theme':0x01010000,
    'label':0x01010001,
    'name':0x01010003,
    'hasCode':0x0101000c,
    'screenOrientation':0x0101001e,
    'configChanges':0x0101001f,
    'value':0x01010024,
    'minSdkVersion':0x0101020c,
    'versionCode':0x0101021b,
    'versionName':0x0101021c,
    'targetSdkVersion':0x01010270,
}

@dataclass
class Attr:
    ns: str|None
    name: str
    typ: str
    value: object

@dataclass
class Elem:
    name: str
    attrs: list
    children: list
    line: int=1

manifest=Elem('manifest',[
    Attr(None,'package','str','io.ts7diag.tool'),
    Attr(ANDROID_NS,'versionCode','int',1),
    Attr(ANDROID_NS,'versionName','str','0.1'),
],[
    Elem('uses-sdk',[
        Attr(ANDROID_NS,'minSdkVersion','int',21),
        Attr(ANDROID_NS,'targetSdkVersion','int',22),
    ],[],2),
    Elem('uses-permission',[Attr(ANDROID_NS,'name','str','android.permission.WRITE_EXTERNAL_STORAGE')],[],3),
    Elem('uses-permission',[Attr(ANDROID_NS,'name','str','android.permission.ACCESS_WIFI_STATE')],[],4),
    Elem('uses-permission',[Attr(ANDROID_NS,'name','str','android.permission.BLUETOOTH')],[],5),
    Elem('application',[
        Attr(ANDROID_NS,'label','str','TS7 Diagnostic'),
        Attr(ANDROID_NS,'hasCode','bool',False),
        Attr(ANDROID_NS,'theme','ref',0x0103000d), # Theme_Light_NoTitleBar
    ],[
        Elem('activity',[
            Attr(ANDROID_NS,'name','str','android.app.NativeActivity'),
            Attr(ANDROID_NS,'label','str','TS7 Diagnostic'),
            Attr(ANDROID_NS,'screenOrientation','int',0),
            Attr(ANDROID_NS,'configChanges','hex',0x4a0), # keyboardHidden|orientation|screenSize
        ],[
            Elem('meta-data',[
                Attr(ANDROID_NS,'name','str','android.app.lib_name'),
                Attr(ANDROID_NS,'value','str','main'),
            ],[],8),
            Elem('intent-filter',[],[
                Elem('action',[Attr(ANDROID_NS,'name','str','android.intent.action.MAIN')],[],10),
                Elem('category',[Attr(ANDROID_NS,'name','str','android.intent.category.LAUNCHER')],[],11),
            ],11),
        ],7)
    ],6)
],1)

# Gather strings, ensuring resource-name strings are first so ResourceMap can be contiguous.
used_res=set()
others=[]
def add_other(s):
    if s not in others: others.append(s)
def walk(e):
    add_other(e.name)
    for a in e.attrs:
        if a.ns==ANDROID_NS and a.name in RES: used_res.add(a.name)
        else: add_other(a.name)
        if a.ns: add_other(a.ns)
        if a.typ=='str': add_other(str(a.value))
    for c in e.children: walk(c)
walk(manifest)
add_other('android')
# resource names first, stable order by resource id
strings=sorted(used_res,key=lambda x:RES[x])+[s for s in others if s not in used_res]
idx={s:i for i,s in enumerate(strings)}

def u16(x): return struct.pack('<H',x&0xffff)
def u32(x): return struct.pack('<I',x&0xffffffff)
def hdr(t,hs,size): return u16(t)+u16(hs)+u32(size)

def utf16z(s):
    enc=s.encode('utf-16le')
    chars=len(enc)//2
    if chars>=0x8000: raise ValueError('string too long')
    return u16(chars)+enc+b'\x00\x00'

def string_pool():
    encs=[utf16z(s) for s in strings]
    offsets=[]; off=0
    for e in encs: offsets.append(off); off+=len(e)
    header_size=0x1c
    strings_start=header_size+4*len(strings)
    body=b''.join(u32(x) for x in offsets)+b''.join(encs)
    size=header_size+len(body)
    pad=(-size)%4
    size+=pad
    return hdr(0x0001,header_size,size)+u32(len(strings))+u32(0)+u32(0)+u32(strings_start)+u32(0)+body+b'\x00'*pad

def res_map():
    names=sorted(used_res,key=lambda x:RES[x])
    assert strings[:len(names)]==names
    body=b''.join(u32(RES[n]) for n in names)
    return hdr(0x0180,8,8+len(body))+body

def ns_start():
    body=u32(1)+u32(NO)+u32(idx['android'])+u32(idx[ANDROID_NS])
    return hdr(0x0100,0x10,0x18)+body[8:] if False else (u16(0x0100)+u16(0x10)+u32(0x18)+u32(1)+u32(NO)+u32(idx['android'])+u32(idx[ANDROID_NS]))

def ns_end():
    return u16(0x0101)+u16(0x10)+u32(0x18)+u32(99)+u32(NO)+u32(idx['android'])+u32(idx[ANDROID_NS])

def attr_bytes(a:Attr):
    ns_i=idx[a.ns] if a.ns else NO
    name_i=idx[a.name]
    out=u32(ns_i)+u32(name_i)
    if a.typ=='str':
        vi=idx[str(a.value)]
        out+=u32(vi)+u16(8)+b'\x00\x03'+u32(vi)
    elif a.typ=='int':
        out+=u32(NO)+u16(8)+b'\x00\x10'+u32(int(a.value))
    elif a.typ=='hex':
        out+=u32(NO)+u16(8)+b'\x00\x11'+u32(int(a.value))
    elif a.typ=='bool':
        out+=u32(NO)+u16(8)+b'\x00\x12'+u32(0xffffffff if a.value else 0)
    elif a.typ=='ref':
        out+=u32(NO)+u16(8)+b'\x00\x01'+u32(int(a.value))
    else: raise ValueError(a.typ)
    return out

def start_elem(e:Elem):
    # Android attrs first by resource id; unnamespaced after.
    attrs=sorted(e.attrs,key=lambda a:(0 if a.ns==ANDROID_NS else 1, RES.get(a.name,0xffffffff), a.name))
    ab=b''.join(attr_bytes(a) for a in attrs)
    size=0x24+len(ab)
    return (u16(0x0102)+u16(0x10)+u32(size)+u32(e.line)+u32(NO)+
            u32(NO)+u32(idx[e.name])+u16(0x14)+u16(0x14)+u16(len(attrs))+u16(0)+u16(0)+u16(0)+ab)

def end_elem(e:Elem):
    return u16(0x0103)+u16(0x10)+u32(0x18)+u32(e.line)+u32(NO)+u32(NO)+u32(idx[e.name])

def emit(e):
    b=start_elem(e)
    for c in e.children: b+=emit(c)
    b+=end_elem(e)
    return b

body=string_pool()+res_map()+ns_start()+emit(manifest)+ns_end()
out=hdr(0x0003,8,8+len(body))+body

import argparse
parser=argparse.ArgumentParser()
parser.add_argument('--output', default='build/AndroidManifest.xml')
args=parser.parse_args()
p=Path(args.output)
p.parent.mkdir(parents=True, exist_ok=True)
p.write_bytes(out)
print('manifest bytes',len(out),'strings',len(strings),'resource map entries',len(used_res))
print('string pool order:',strings)
