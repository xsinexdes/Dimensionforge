# DimensionForge

Plugin para **Paper 1.21.1** (Java 21) para crear/importar dimensiones, portales con hacha, menas custom,
mobs y jefes (integración opcional con **BetterModel**), resource pack automático y una biblioteca de
**1143 habilidades** con fases de jefe. Todo se gestiona con menús (`/dimension menu`) o con comandos.

## Subir a GitHub y compilar (sin instalar nada)
1. Descomprime el zip. En GitHub: **New repository** (vacío, sin README).
2. Sube **todo el contenido** de la carpeta (arrastra los archivos en «uploading an existing file»; asegúrate de que se sube `.github/`, que es una carpeta oculta). Con git:
   `git init && git add . && git commit -m "DimensionForge" && git branch -M main && git remote add origin <URL> && git push -u origin main`
3. Pestaña **Actions** → **Build DimensionForge** (se lanza solo con cada push; o «Run workflow»).
4. Cuando termine en verde: ejecución → **Artifacts → DimensionForge** → descarga el `.jar`.
5. Si sale rojo, abre el paso «Compilar y probar» y copia el error.
6. Para publicar una versión: `git tag v1.0.0 && git push origin v1.0.0` → el workflow **Publicar release** adjunta el jar a una Release.

Local: `./gradlew build` (jar en `build/libs/`) o `mvn clean package` (jar en `target/`). Se incluyen `build.gradle.kts` y `pom.xml`.

Archivos del repositorio: `pom.xml`, `build.gradle.kts`, `settings.gradle.kts`, `gradlew`/`gradle/wrapper`, `.github/` (workflows, plantillas de issues y PR, dependabot), `.gitignore`, `.gitattributes`, `LICENSE` (MIT, cámbiala si quieres), `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, `examples/`.

## Instalar
- Copia el jar en `plugins/`. Opcionales: BetterModel 2.2.0 (modelos animados), WorldGuard (respeto de regiones), Vault (costes en dinero).
- Arranca una vez: se crean `config.yml`, `messages.yml`, `skills/library.yml`, etc.
- Ejemplos listos en `examples/` (cópialos a `plugins/DimensionForge/` con el servidor parado):
  `dimensions.yml`, `portals.yml`, `ores.yml`, `mobs.yml`, `cmd_registry.yml`, `skills/library.yml`,
  `pack/models/ore/mithril_ore.bbmodel` (→ `plugins/DimensionForge/pack/models/ore/`) y
  `bettermodel/guardian_minero.bbmodel` (→ `plugins/BetterModel/models/`).
  Resultado esperado del pack: `examples/pack/RESULT_pack.txt`.

## Pruebas exactas (ejemplo «minas»)
1. `/dimension list` → aparece `minas` (si copiaste `dimensions.yml`) o créala: `/dimension create minas NORMAL AMPLIFIED`.
2. `/dimension tp minas` → mensaje de entrada «Minas Profundas». Vuelve con `/dimension tp world`.
3. Portal: `/dimension wand`, clic izquierdo y derecho en dos esquinas, `/dimension portal menu` → crear → destino `minas`.
   Entra caminando: te lleva a `minas`. (El portal de ejemplo de `portals.yml` está en 10,64,10 de `world`: ajusta la región.)
4. Mena: en `minas`, `/dimension ore regen` (o `ore scan mithril`) y explora Y -60..20. Con pico de madera/piedra: aviso «Necesitas…»;
   con pico de hierro+: se rompe tras ~6 s base, suelta Mithril, reaparece a los 90 s.
5. Jefe: `/dimension mob spawn guardian_minero` (o menú Mobs → clic). Barra de jefe visible. Baja su vida:
   al 66 % «Fase 2 – Derrumbe»; al 33 % «Fase 3 – Dinamita» (+10 % de curación).
6. Habilidades: `/dimension skill count` → 1143; `/dimension skill test guardian_minero sig_quake`.
7. Pack: `/dimension pack build`, `pack status`; pon `pack.host.public-address` (IP/dominio) y `/dimension pack send`.
8. BetterModel: con el plugin instalado y `guardian_minero.bbmodel`, `/dimension mob model guardian_minero guardian_minero`.

## Honestidad: hecho / simplificado / no posible
**Hecho**: GUI (paginación, búsqueda, entrada por chat o yunque), crear/importar/editar/borrar dimensiones, hacha de portales con
rellenos y costes, menas (tiempo de minado propio, herramientas, drops, generación por vetas, respawn, modo modelo), mobs custom con
stats/drops/fases/barra de jefe/reglas de aparición, generador de pack con CMD estables, servidor HTTP interno, biblioteca de 1143 habilidades
(13 disparadores, 18 formas, 50 efectos, 10 elementos, 12 modificadores) con test JUnit.

**Sin verificar**: no he podido compilar aquí (sin acceso a los repositorios de Paper). Solo se comprobó el generador de habilidades,
el convertidor `.bbmodel` y la coherencia de claves de mensajes. Es posible algún error de compilación por nombres de la API; GitHub lo dirá.

**Simplificado**
- BetterModel **2.2.0** (la última línea para 1.21.1; la 3.x exige 1.21.4+/Java 25). Convivencia de packs: solo modo `SEPARATE`, ruta sin verificar; no hay fusión.
- `pack.format-mode: items` (carpeta `items/`) solo vale desde 1.21.4; en 1.21.1 se usan los `overrides` de `models/item`.
- Conversión `.bbmodel`→Java: solo cubos, rotación en un eje, sin mallas ni animaciones (las animaciones las hace BetterModel).
- «Lava temporal» usa fuego/brasas, no lava real. `ON_CRIT` = recibir un golpe crítico. `DRAIN_XP` sin verificar en todas las versiones de Paper.
- Atributos de mob como alcance de ataque, trepar o volar están limitados a lo que da la API.
- Las menas generadas se registran al cargar el mundo; las etiquetas de los menús están en español fijo; «Silenciar» bloquea chat y comandos.

**No posible**: hacer que los clientes vean modelos custom sin que acepten el resource pack; renombrar dimensiones vanilla del servidor;
fusionar automáticamente con el pack de BetterModel.
