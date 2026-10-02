#!/usr/bin/env python3
"""
Minimal Android manifest merger for the manual build.

Merges the app manifest with manifests extracted from dependency AARs.

Rules:
  - <uses-permission>, <uses-feature>, <queries>: union, deduplicated.
  - <application> attributes: app manifest wins; missing attrs filled from libs.
  - <application> children (activity/service/receiver/provider/meta-data):
    merged, deduplicated by (tag, android:name). App entries win on conflict.
  - DROPPED: androidx.startup.InitializationProvider (the app supplies its own
    WorkManager Configuration via FpmApplication implementing
    Configuration.Provider, mirroring tools:node="remove" in the Gradle build).
  - Placeholders ${applicationId} / ${facebookAppId} are substituted.
"""

import sys
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)


def a(el, name):
    return el.get("{%s}%s" % (ANDROID_NS, name))


def parse(path):
    return ET.parse(path).getroot()


def key_for(el):
    """Dedup key for <application> children."""
    if el.tag == "meta-data":
        return ("meta-data", a(el, "name"))
    name = a(el, "name")
    if name:
        return (el.tag, name)
    return None


def main():
    app_manifest, app_id, fb_app_id, out = sys.argv[1:5]
    lib_manifests = sys.argv[5:]

    def subst(text):
        if text is None:
            return None
        return (text.replace("${applicationId}", app_id)
                    .replace("${facebookAppId}", fb_app_id))

    app = parse(app_manifest)

    # Collect library contributions.
    lib_perms = []      # list of elements
    lib_queries = []
    lib_app_attrs = {}
    lib_children = []   # (key, element)

    for lm in lib_manifests:
        try:
            root = parse(lm)
        except Exception as e:
            print(f"  warn: cannot parse {lm}: {e}", file=sys.stderr)
            continue
        for el in list(root):
            if el.tag in ("uses-permission", "uses-permission-sdk-23", "uses-feature"):
                lib_perms.append(el)
            elif el.tag == "queries":
                lib_queries.append(el)
        app_el = root.find("application")
        if app_el is not None:
            for k, v in app_el.attrib.items():
                lib_app_attrs.setdefault(k, v)
            for child in list(app_el):
                if child.tag == "provider" and a(child, "name") == \
                        "androidx.startup.InitializationProvider":
                    continue  # we configure WorkManager manually
                if child.tag == "provider" and a(child, "name") == \
                        "com.facebook.internal.FacebookInitProvider":
                    continue  # we init the Facebook SDK manually on demand only
                k = key_for(child)
                if k is not None:
                    lib_children.append((k, child))

    # --- merge into app manifest ---
    existing_perms = set()
    for el in list(app):
        if el.tag in ("uses-permission", "uses-permission-sdk-23", "uses-feature"):
            existing_perms.add((el.tag, a(el, "name"), a(el, "maxSdkVersion")))
    insert_at = 0
    for i, el in enumerate(list(app)):
        if el.tag == "application":
            insert_at = i
            break
    for el in lib_perms:
        key = (el.tag, a(el, "name"), a(el, "maxSdkVersion"))
        if key not in existing_perms:
            existing_perms.add(key)
            app.insert(insert_at, el)
            insert_at += 1

    # queries: append library <queries> blocks (dedup by serialized content)
    existing_queries = set(ET.tostring(q) for q in app.findall("queries"))
    for q in lib_queries:
        blob = ET.tostring(q)
        if blob not in existing_queries:
            existing_queries.add(blob)
            app.insert(insert_at, q)
            insert_at += 1

    app_el = app.find("application")
    # application attributes: app wins, fill missing
    for k, v in lib_app_attrs.items():
        if k not in app_el.attrib:
            app_el.set(k, v)
    # children: dedupe by key, app wins
    existing_keys = set()
    for child in list(app_el):
        # honour the tools:node="remove" declarations: our merger already drops
        # these providers from libs, so drop the declarations themselves.
        if child.tag == "provider" and a(child, "name") in (
                "androidx.startup.InitializationProvider",
                "com.facebook.internal.FacebookInitProvider"):
            app_el.remove(child)
            continue
        k = key_for(child)
        if k is not None:
            existing_keys.add(k)
    for k, child in lib_children:
        if k not in existing_keys:
            existing_keys.add(k)
            app_el.append(child)

    # placeholder substitution across the whole tree
    for el in app.iter():
        for attr in list(el.attrib):
            el.set(attr, subst(el.get(attr)))
        if el.text:
            el.text = subst(el.text)

    ET.indent(app, space="    ")
    ET.ElementTree(app).write(out, encoding="utf-8", xml_declaration=True)
    print(f"merged manifest -> {out}")


if __name__ == "__main__":
    main()
