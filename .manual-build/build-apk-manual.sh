#!/usr/bin/env bash
# Manual APK build for Facebook Page Manager (no Gradle daemon needed).
#
# Replicates what `./gradlew assembleDebug -PFACEBOOK_APP_ID=...` does:
#   1. select newest-wins deps, extract AARs (classes.jar, manifests, resources)
#   2. merge manifests (Python) + generate BuildConfig.java + fb id resources
#   3. aapt2 compile/link resources -> base APK + R.java
#   4. kotlinc (embeddable compiler) + kapt aptMode=stubs -> Java stubs
#   5. javac + Room annotation processor on stubs -> generated Room impls
#   6. kotlinc (embeddable, no kapt) + Compose plugin -> classes
#   7. javac (R.java, BuildConfig.java, Room-generated) -> classes
#   8. d8 -> classes.dex -> zipalign + apksigner -> dist/FacebookPageManager-debug.apk
#
# Why the 3-phase Kotlin build: kapt's full pipeline (stubs+apt+compile in one
# kotlinc invocation) crashes the 1.9.24 backend with
# "KtConstantExpression was not evaluated" on data-class default values
# (deterministic, reproduced 3x). Splitting into stubs -> javac(APT) ->
# plain kotlinc avoids the crash; each phase is verified working.
#
# Usage: ./build-apk-manual.sh [FACEBOOK_APP_ID]
# Env overrides: JAVA_HOME, ANDROID_SDK, FB_APP_ID
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MB="$ROOT/.manual-build"
APP_ID="${1:-${FB_APP_ID:-${FACEBOOK_APP_ID:-YOUR_FACEBOOK_APP_ID}}}"
PKG="com.facebookpagemanager.app"

export JAVA_HOME="${JAVA_HOME:-$HOME/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_SDK:-$HOME/android-sdk}"
BT="$SDK/build-tools/34.0.0"
AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"

# Kotlin compiler: kotlin-compiler-embeddable (the CLI kotlinc can't load the
# Compose plugin: compose-compiler 1.5.14 needs the relocated intellij
# MockProject that only kotlin-compiler-embeddable provides).
KCP_CP="$MB/kcp/kotlin-compiler-embeddable-1.9.24.jar"
KCP_CP="$KCP_CP:$MB/kcp/kotlin-stdlib-1.9.24.jar"
KCP_CP="$KCP_CP:$MB/kcp/kotlin-script-runtime-1.9.24.jar"
KCP_CP="$KCP_CP:$MB/kcp/kotlin-reflect-1.6.10.jar"
KCP_CP="$KCP_CP:$MB/kcp/trove4j-1.0.20200330.jar"
KCP_CP="$KCP_CP:$MB/kotlinc/lib/annotations-13.0.jar"
KOTLINC=(java -cp "$KCP_CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler)
COMPOSE_PLUGIN="$MB/compiler-1.5.14.jar"
KAPT_PLUGIN="$MB/kcp/kotlin-annotation-processing-embeddable-1.9.24.jar"
STDLIB_JAR="$MB/kotlinc/lib/kotlin-stdlib.jar"
ANNOT13_JAR="$MB/kotlinc/lib/annotations-13.0.jar"

WORK="$MB/work"
GEN="$WORK/gen"            # R.java, BuildConfig.java, Room-generated sources
STUBS="$WORK/stubs"        # kapt-generated Java stubs
KAPT_GEN="$GEN/kapt"       # Room-generated Java sources
CLASSES="$WORK/classes"
DEXOUT="$WORK/dex-out"
AARX="$WORK/aarx"          # extracted AARs
COMPILED="$WORK/compiled"  # aapt2-compiled res per module
if [ -d "$WORK" ]; then chmod -R u+rwX "$WORK" 2>/dev/null || true; rm -rf "$WORK"; fi
mkdir -p "$GEN" "$STUBS" "$KAPT_GEN" "$CLASSES" "$DEXOUT" "$AARX" "$COMPILED" "$ROOT/dist"

echo "==> [1/8] selecting deps + extracting AARs"
python3 "$MB/select_deps.py" > "$WORK/selected.txt"
# Dedupe JARs that duplicate a selected AAR (same normalized base): d8 fails
# on duplicate classes. Prefer the AAR (Android variant). Must run before the
# extraction loop populates cp.txt/dexcp.txt.
python3 - "$WORK/selected.txt" <<'PYEOF'
import re, sys
sel = open(sys.argv[1]).read().splitlines()
def norm_base(path):
    f = path.rsplit("/", 1)[-1]
    f = re.sub(r"\.(aar|jar)$", "", f)
    f = re.sub(r"-\d+\.\d+.*$", "", f)  # strip version
    for sfx in ("-android", "-jvm", "-ktx", "-java8"):
        if f.endswith(sfx):
            f = f[: -len(sfx)]
            break
    return f
aar_bases = {norm_base(p) for k, p in (l.split(" ", 1) for l in sel) if k == "aar"}
# listenablefuture is bundled inside guava; keep guava only for d8
has_guava = any("guava" in p for k, p in (l.split(" ", 1) for l in sel) if k == "jar")
out = []
for l in sel:
    k, p = l.split(" ", 1)
    if k == "jar" and norm_base(p) in aar_bases:
        print(f"  dedupe (aar wins): {p}", file=sys.stderr)
        continue
    if k == "jar" and has_guava and "listenablefuture" in p:
        print(f"  dedupe (guava wins): {p}", file=sys.stderr)
        continue
    out.append(l)
open(sys.argv[1], "w").write("\n".join(out) + "\n")
PYEOF
: > "$WORK/cp.txt"    # kotlinc/javac compile classpath entries
: > "$WORK/dexcp.txt" # d8 program classpath entries
while read -r kind path; do
  case "$kind" in
    aar)
      name="$(basename "$path" .aar)"
      d="$AARX/$name"
      mkdir -p "$d"
      unzip -q -o "$path" -d "$d"
      chmod -R u+rwX "$d" 2>/dev/null || true
      if [ -f "$d/classes.jar" ]; then
        mkdir -p "$d/classes"
        unzip -q -o "$d/classes.jar" -d "$d/classes"
        echo "$d/classes" >> "$WORK/cp.txt"
        echo "$d/classes" >> "$WORK/dexcp.txt"
      fi
      for j in "$d"/libs/*.jar; do
        [ -f "$j" ] || continue
        jd="$d/libs-classes-$(basename "$j" .jar)"
        mkdir -p "$jd"
        unzip -q -o "$j" -d "$jd"
        echo "$jd" >> "$WORK/cp.txt"
        echo "$jd" >> "$WORK/dexcp.txt"
      done
      ;;
    jar)
      echo "$path" >> "$WORK/cp.txt"
      echo "$path" >> "$WORK/dexcp.txt"
      ;;
    proc) ;;
  esac
done < "$WORK/selected.txt"
# kotlin-stdlib from the compiler distribution (app bundles 1.9.24)
echo "$STDLIB_JAR" >> "$WORK/cp.txt"
echo "$STDLIB_JAR" >> "$WORK/dexcp.txt"
# org.jetbrains annotations (kapt stubs reference @NotNull/@Nullable)
echo "$ANNOT13_JAR" >> "$WORK/cp.txt"
echo "$ANDROID_JAR" >> "$WORK/cp.txt"
echo "  aars: $(grep -c '^aar' "$WORK/selected.txt"), jars: $(grep -c '^jar' "$WORK/selected.txt")"

echo "==> [2/8] generating BuildConfig.java, resources, merged manifest"
mkdir -p "$GEN/com/facebookpagemanager/app"
cat > "$GEN/com/facebookpagemanager/app/BuildConfig.java" <<EOF
package com.facebookpagemanager.app;
public final class BuildConfig {
  public static final boolean DEBUG = true;
  public static final String APPLICATION_ID = "$PKG";
  public static final String BUILD_TYPE = "debug";
  public static final int VERSION_CODE = 6;
  public static final String VERSION_NAME = "1.0.5";
  public static final String FACEBOOK_APP_ID = "$APP_ID";
  public static final String GRAPH_API_VERSION = "v20.0";
  public static final String GRAPH_API_BASE_URL = "https://graph.facebook.com/";
  private BuildConfig() {}
}
EOF
mkdir -p "$WORK/genres/values"
cat > "$WORK/genres/values/fbconfig.xml" <<EOF
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="facebook_app_id">$APP_ID</string>
</resources>
EOF
MANIFESTS=()
for m in "$AARX"/*/AndroidManifest.xml; do MANIFESTS+=("$m"); done
python3 "$MB/merge_manifest.py" \
  "$ROOT/app/src/main/AndroidManifest.xml" \
  "$PKG" "$APP_ID" "$WORK/AndroidManifest.xml" \
  "${MANIFESTS[@]}"
python3 - "$WORK/AndroidManifest.xml" "$PKG" <<'PYEOF'
import sys, xml.etree.ElementTree as ET
path, pkg = sys.argv[1], sys.argv[2]
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
t = ET.parse(path); r = t.getroot()
r.set("package", pkg)
ET.indent(r, space="    ")
t.write(path, encoding="utf-8", xml_declaration=True)
print("package ->", pkg)
PYEOF

echo "==> [3/8] aapt2 compile + link"
R_ARGS=()
for d in "$AARX"/*; do
  name="$(basename "$d")"
  if [ -d "$d/res" ]; then
    "$AAPT2" compile --dir "$d/res" -o "$COMPILED/${name}.zip" 2>/dev/null || \
      echo "  (no resources compiled for $name)"
    [ -f "$COMPILED/${name}.zip" ] && R_ARGS+=(-R "$COMPILED/${name}.zip")
  fi
done
"$AAPT2" compile --dir "$ROOT/app/src/main/res" -o "$COMPILED/app.zip"
"$AAPT2" compile --dir "$WORK/genres" -o "$COMPILED/genres.zip"
# Collect unique library packages (from AAR manifests) so aapt2 generates
# R.java for EACH library, not just the app. Without this, library code that
# references its own R class (e.g. androidx.lifecycle.runtime.R$id) crashes
# with NoClassDefFoundError at runtime.
EXTRA_PKGS=""
for d in "$AARX"/*; do
  if [ -d "$d/res" ] && [ -f "$d/AndroidManifest.xml" ]; then
    pkg=$(grep -o 'package="[^"]*"' "$d/AndroidManifest.xml" | head -1 | cut -d'"' -f2)
    if [ -n "$pkg" ] && [ "$pkg" != "$PKG" ]; then
      case ":$EXTRA_PKGS:" in
        *":$pkg:"*) ;;
        *) EXTRA_PKGS="${EXTRA_PKGS:+$EXTRA_PKGS:}$pkg" ;;
      esac
    fi
  fi
done
echo "  extra packages for R.java: $(echo "$EXTRA_PKGS" | tr ':' ' ' | wc -w)"
"$AAPT2" link -o "$WORK/base.apk" \
  --manifest "$WORK/AndroidManifest.xml" \
  -I "$ANDROID_JAR" \
  "${R_ARGS[@]}" \
  -R "$COMPILED/app.zip" \
  -R "$COMPILED/genres.zip" \
  --java "$GEN" \
  --extra-packages "$EXTRA_PKGS" \
  --auto-add-overlay \
  --output-text-symbols "$WORK/symbols.txt" \
  --min-sdk-version 26 --target-sdk-version 34 \
  --version-code 6 --version-name 1.0.5

CP="$(paste -sd: "$WORK/cp.txt")"

echo "==> [3b/8] javac R.java + BuildConfig.java (kotlinc needs them on its classpath)"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 17 -target 17 -nowarn \
  -cp "$CP" \
  -d "$CLASSES" \
  $(find "$GEN" -name 'R.java' -o -name 'BuildConfig.java') 2>&1 | grep -vE "unknown enum|reason:|^$" | head -10 || true
echo "  pre-javac done"

echo "==> [4/8] kotlinc kapt stubs (aptMode=stubs)"
mkdir -p "$WORK/kapt-classes"
find "$ROOT/app/src/main/kotlin" -name '*.kt' > "$WORK/sources.txt"
wc -l < "$WORK/sources.txt" | xargs echo "  kotlin sources:"
APCP="$(awk '$1=="proc"{print $2}' "$WORK/selected.txt" | paste -sd:)"
# room-common carries the annotations (androidx.room.Database); guava and
# room-migration are needed by the processor at runtime. All three also live
# on the app compile classpath, so pull them into the processor path too.
for _need in "/room-common-[0-9]" "/guava-" "/room-migration-[0-9]"; do
  _j="$(awk '$1=="jar"{print $2}' "$WORK/selected.txt" | grep -E "$_need" | head -1)"
  [ -n "$_j" ] && APCP="$APCP:$_j"
done
APCP="$APCP:$STDLIB_JAR"
if [ -z "$APCP" ] || [ "$APCP" = ":$STDLIB_JAR" ]; then
  echo "ERROR: no processor jars selected"; exit 1
fi
"${KOTLINC[@]}" $(cat "$WORK/sources.txt") \
  -cp "$CP" \
  -d "$WORK/unused" \
  -jvm-target 17 \
  -Xplugin="$KAPT_PLUGIN" \
  -P "plugin:org.jetbrains.kotlin.kapt3:sources=$KAPT_GEN" \
  -P "plugin:org.jetbrains.kotlin.kapt3:classes=$WORK/kapt-classes" \
  -P "plugin:org.jetbrains.kotlin.kapt3:stubs=$STUBS" \
  -P "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=true" \
  -P "plugin:org.jetbrains.kotlin.kapt3:aptMode=stubs" \
  -P "plugin:org.jetbrains.kotlin.kapt3:apclasspath=$APCP" \
  -nowarn 2>&1 | grep -vE "^warning:|^info:" | head -20 || true
find "$STUBS" -name '*.java' > "$WORK/stublist.txt"
wc -l < "$WORK/stublist.txt" | xargs echo "  stubs generated:"
[ -s "$WORK/stublist.txt" ] || { echo "ERROR: no stubs generated"; exit 1; }

echo "==> [5/8] javac + Room annotation processor"
# Only Room-annotated stubs need processing; other stubs may contain
# Java-unrepresentable Kotlin constructs (e.g. top-level val imports).
grep -lE "androidx\.room\.(Entity|Dao|Database)" -r "$STUBS" --include='*.java' > "$WORK/stublist.txt" || true
wc -l < "$WORK/stublist.txt" | xargs echo "  room stubs:"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -proc:only \
  -processorpath "$APCP" \
  -cp "$CLASSES:$CP" \
  -s "$KAPT_GEN" -d "$WORK/kapt-classes" \
  @"$WORK/stublist.txt" 2>&1 | grep -vE "unknown enum|reason:|^$" | head -10 || true
find "$KAPT_GEN" -name '*.java' > "$WORK/kaptgen.txt"
wc -l < "$WORK/kaptgen.txt" | xargs echo "  Room sources generated:"
[ -s "$WORK/kaptgen.txt" ] || { echo "ERROR: Room generated nothing"; exit 1; }

echo "==> [6/8] kotlinc (Compose plugin, no kapt) -> classes"
"${KOTLINC[@]}" $(cat "$WORK/sources.txt") \
  -cp "$CLASSES:$CP" \
  -d "$CLASSES" \
  -jvm-target 17 \
  -Xplugin="$COMPOSE_PLUGIN" \
  -P "plugin:androidx.compose.compiler.plugins.kotlin:suppressKotlinVersionCompatibilityCheck=true" \
  -nowarn 2>&1 | grep -vE "^warning:|^info:" | head -30 || true
echo "  kotlinc done: $(find "$CLASSES" -name '*.class' | wc -l) classes"

echo "==> [7/8] javac (R.java, BuildConfig.java, Room-generated) -> classes"
find "$GEN" -name '*.java' > "$WORK/jsources.txt"
wc -l < "$WORK/jsources.txt" | xargs echo "  java sources:"
"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 17 -target 17 -nowarn \
  -cp "$CLASSES:$CP" \
  -d "$CLASSES" \
  @"$WORK/jsources.txt" 2>&1 | grep -vE "unknown enum|reason:|^$" | head -20 || true
echo "  javac done"

echo "==> [8/8] d8 -> classes.dex -> zipalign + apksigner"
# d8 (8.2.2) does not accept raw directories; package all classes into jars.
APP_JAR="$WORK/app-classes.jar"
(cd "$CLASSES" && "$JAVA_HOME/bin/jar" cf "$APP_JAR" .)
# Convert dexcp directories to jars (d8 needs jar inputs, not dirs)
DEXJARS="$APP_JAR"
while read -r entry; do
  if [ -d "$entry" ]; then
    j="$WORK/dex-$(echo "$entry" | tr '/.' '__').jar"
    if [ ! -f "$j" ]; then
      (cd "$entry" && "$JAVA_HOME/bin/jar" cf "$j" .) 2>/dev/null || true
    fi
    [ -f "$j" ] && DEXJARS="$DEXJARS $j"
  elif [ -f "$entry" ]; then
    DEXJARS="$DEXJARS $entry"
  fi
done < "$WORK/dexcp.txt"
"$D8" --lib "$ANDROID_JAR" --min-api 26 --output "$DEXOUT" $DEXJARS
cp "$WORK/base.apk" "$WORK/unaligned.apk"
# Multidex: d8 emits classes.dex, classes2.dex, ... — ALL of them must go in.
(cd "$DEXOUT" && zip -q -X "$WORK/unaligned.apk" classes*.dex)
if [ ! -f "$MB/debug.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$MB/debug.keystore" \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass android -keypass android \
    -dname "CN=Android Debug,O=Android,C=US"
fi
"$ZIPALIGN" -f 4 "$WORK/unaligned.apk" "$WORK/aligned.apk"
"$APKSIGNER" sign --ks "$MB/debug.keystore" \
  --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT/dist/FacebookPageManager-debug.apk" "$WORK/aligned.apk"
"$APKSIGNER" verify --print-certs "$ROOT/dist/FacebookPageManager-debug.apk" | head -5 || true
echo
echo "APK: $ROOT/dist/FacebookPageManager-debug.apk"
ls -la "$ROOT/dist/FacebookPageManager-debug.apk"
