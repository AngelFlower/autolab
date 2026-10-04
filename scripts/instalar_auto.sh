#!/usr/bin/env bash
# Fase 7H: compila e instala las tres variantes de AutoLab (parked, templates, media)
# marcandolas como instaladas desde Play Store. Android Auto en un auto real filtra las
# apps segun el instalador; el DHU no lo hace (ver RESULTS.md, Fase 7H).
#
# Uso: scripts/instalar_auto.sh [serial-adb]
set -euo pipefail
cd "$(dirname "$0")/.."

ADB=(adb)
[[ $# -ge 1 ]] && ADB=(adb -s "$1")

./gradlew assembleDebug -q

PLAY=com.android.vending
for flavor in parked templates media; do
    apk="app/build/outputs/apk/$flavor/debug/app-$flavor-debug.apk"
    case $flavor in
        parked)    pkg=dev.personal.autolab ;;
        templates) pkg=dev.personal.autolab.tpl ;;
        media)     pkg=dev.personal.autolab.media ;;
    esac
    echo "== $flavor ($pkg)"
    # Desinstalar primero: el host cachea el componente de entrada del paquete (Fase 4).
    "${ADB[@]}" uninstall "$pkg" >/dev/null 2>&1 || true
    "${ADB[@]}" install -r -i "$PLAY" "$apk"
    # Refuerzo por si el -i fue ignorado en esta version de Android.
    "${ADB[@]}" shell pm set-installer "$pkg" "$PLAY" >/dev/null 2>&1 || true
    "${ADB[@]}" shell dumpsys package "$pkg" | grep -m1 -E "installerPackageName|installInitiatingPackageName" || true
done

# Que Android Auto vuelva a leer la lista de apps la proxima vez que conecte.
"${ADB[@]}" shell am force-stop com.google.android.projection.gearhead || true
echo "Listo. Conecta el telefono al auto y busca 'AutoLab P', 'AutoLab T' y 'AutoLab M'."
