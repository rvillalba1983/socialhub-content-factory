# scripts/windows

## probar-postgres.bat

Comprueba que PostgreSQL funciona incluido en SocialHub en un Windows concreto, sin instalar nada en el sistema. Se usa con doble clic: el `.bat` lanza `probar-postgres.ps1`.

Pasos que hace:
1. Comprueba los componentes de Microsoft Visual C++ (`vcruntime140.dll`, `vcruntime140_1.dll`, `msvcp140.dll`) y que el puerto 55432 está libre.
2. Descarga PostgreSQL 18.6 portable (binarios de EDB, unos 370 MB) en `prueba-postgres\`, junto al script.
3. Lo descomprime con `tar.exe`, sin pgAdmin ni StackBuilder.
4. Crea la carpeta de datos y arranca el servidor, escuchando solo en `127.0.0.1`.
5. Crea una base de datos, escribe un dato, para el servidor, lo vuelve a arrancar y comprueba que el dato sigue ahí.
6. Deja el resultado en `informe-postgres.txt`, que se guarda en cada paso.

Si se repite, reutiliza lo ya descargado y descomprimido.

Resultado el 26 oct 2026 en Windows 10 (19045): todo OK. Ver `docs/decisiones.md`.
