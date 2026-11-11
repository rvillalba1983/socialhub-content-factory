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

## siempre-abierto/

Mantiene abiertos en el PC de Raúl la app de Claude de escritorio, OpenCode y Chrome. Cada minuto mira si están abiertos y, si falta alguno, lo abre desde el menú Inicio. Mientras está en marcha, Windows no se duerme (la pantalla sí puede apagarse). Así Claude puede llegar al PC y las tareas programadas («Publicar con Chrome») encuentran Chrome abierto.

- **Instalar:** doble clic en `instalar.bat`. Copia el script a `%LOCALAPPDATA%\SiempreAbierto`, lo pone en la carpeta «Inicio» del usuario (arranca solo al iniciar sesión, sin ventana) y lo arranca ya. No necesita permisos de administrador.
- **Quitar:** doble clic en `desinstalar.bat`. Lo para, lo quita del inicio de sesión y Windows vuelve a dormirse como siempre. No cierra ningún programa.
- **Registro:** `%LOCALAPPDATA%\SiempreAbierto\registro.log`. Apunta lo que abre y lo que no encuentra.
- **Si no encuentra un programa,** se cambia su nombre del menú Inicio o del proceso al principio de `mantener-abiertos.ps1`. Los de OpenCode (`OpenCode`, `opencode`, `opencode-desktop`) están sin comprobar en el PC de Raúl.
- **Límites:** solo funciona con la sesión iniciada; si el PC se reinicia, hay que entrar en Windows. Para que no se duerma, el PC no puede estar apagado ni con la tapa cerrada (en un portátil, según su configuración).

La prueba en un Windows real está en `.github/workflows/siempre-abierto.yml`. Se lanza sola en cada PR que cambia estos archivos.
