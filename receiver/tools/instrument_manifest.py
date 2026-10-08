import pathlib
import sys
import xml.etree.ElementTree as ET

path = pathlib.Path(sys.argv[1])
android = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
tree = ET.parse(path)
application = tree.getroot().find("application")
application.set(android + "debuggable", "true")
ET.SubElement(tree.getroot(), "instrumentation", {
    android + "name": "io.ts7.carplay.RendererInstrumentation",
    android + "targetPackage": "io.ts7.carplay",
})
ET.SubElement(tree.getroot(), "instrumentation", {
    android + "name": "io.ts7.carplay.ReadinessInstrumentation",
    android + "targetPackage": "io.ts7.carplay",
})
ET.SubElement(tree.getroot(), "instrumentation", {
    android + "name": "io.ts7.carplay.AuthenticationInstrumentation",
    android + "targetPackage": "io.ts7.carplay",
})
tree.write(path, encoding="utf-8", xml_declaration=True)
