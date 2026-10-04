# AutoLab

Proyecto de laboratorio personal para medir los límites de Android Auto en un
Pixel 6. No se distribuye, solo se instala por ADB para pruebas propias.

Ver `RESULTS.md` para los hallazgos de cada fase.

## Requisitos

- Android Studio con el SDK y el **Android Auto Desktop Head Unit Emulator**
  (`extras;google;auto`, ya instalado en este entorno vía `sdkmanager`).
- Un Pixel 6 con:
  - Opciones de desarrollador + depuración USB activadas.
  - La app Android Auto instalada, con **modo desarrollador** activado
    (tocar varias veces el número de versión en los ajustes de Android Auto)
    y **"Fuentes desconocidas"** activado en esos mismos ajustes de desarrollador.

## Compilar e instalar

```bash
./gradlew assembleDebug
./gradlew installParkedDebug   # solo la variante parked (DHU), con el Pixel 6 por USB
scripts/instalar_auto.sh       # las 3 variantes, marcadas como instaladas desde Play (auto real)
```

## Conectar el DHU (emulador de unidad principal)

1. En el teléfono: abre Android Auto → menú de overflow → **"Iniciar servidor
   de unidad principal"**. Verifica que aparezca la notificación del servicio
   en primer plano.
2. Conecta el teléfono por USB, con la pantalla desbloqueada.
3. En la terminal:
   ```bash
   adb forward tcp:5277 tcp:5277
   ~/Library/Android/sdk/extras/google/auto/desktop-head-unit
   ```
4. Si la pantalla del DHU aparece en blanco: ciérralo, reinicia el servidor
   de unidad principal en el teléfono, y vuelve a abrir el DHU.

Referencia oficial: https://developer.android.com/training/cars/testing/dhu
