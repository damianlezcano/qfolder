# Plan de Trabajo — qfolder (Pendientes)

Plan actualizado el 2 de junio de 2026. Contiene **solo las tareas pendientes o parcialmente implementadas**.
El plan original tenía 65 tareas; 60 fueron completadas. Este documento detalla las **8 tareas restantes**.

> **Nota para el LLM ejecutor:** Antes de cada cambio, leer el archivo completo (o la sección relevante) para confirmar que las líneas coinciden con lo descrito. Las líneas de referencia son del código al 2 de junio de 2026. Después de los cambios, ejecutar `cd p2p-client && mvn test` para validar. Ejecutar `./build.sh` al final.

---

## Índice

- [PENDIENTE-1: DirectBootstrap.join() — WsClient no cerrado tras join exitoso](#pendiente-1-directbootstrapjoin--wsclient-no-cerrado-tras-join-exitoso)
- [PENDIENTE-2: UpdateChecker — JSON parsing con regex](#pendiente-2-updatechecker--json-parsing-con-regex)
- [PENDIENTE-3: System.out.println de debug en Controller.java](#pendiente-3-systemoutprintln-de-debug-en-controllerjava)
- [PENDIENTE-4: Deprecated new Locale(String) en I18n.java](#pendiente-4-deprecated-new-localestring-en-i18njava)
- [PENDIENTE-5: Dead code — serializeNotesStateInBackground](#pendiente-5-dead-code--serializenotesstateInbackground)
- [PENDIENTE-6: Duplicados en messages_en.properties](#pendiente-6-duplicados-en-messages_enproperties)
- [PENDIENTE-7: Test caso25 no renombrado](#pendiente-7-test-caso25-no-renombrado)
- [PENDIENTE-8: AppConfigTest no creado](#pendiente-8-appconfigtest-no-creado)
- [PENDIENTE-9: SpotBugs no habilitado en CI](#pendiente-9-spotbugs-no-habilitado-en-ci)
- [PENDIENTE-10: maven-compiler-plugin no actualizado](#pendiente-10-maven-compiler-plugin-no-actualizado)

---

## PENDIENTE-1: DirectBootstrap.join() — WsClient no cerrado tras join exitoso

**Severidad:** Alta (resource leak)
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/adapters/network/DirectBootstrap.java`

**Estado actual:** Los reintentos fallidos se cierran correctamente con `try/finally`, pero cuando el join es exitoso (`success = true`), el `WsClient bootstrap` nunca se cierra. La conexión queda abierta indefinidamente después del bootstrap, consumiendo un thread executor y un socket TCP.

**Código actual (líneas 106–111):**

```java
			} finally {
				if (!success && bootstrap != null) {
					try { bootstrap.close(); } catch (Exception ignored) {}
				}
			}
			if (success) return true;
```

**Problema:** Solo cierra cuando `!success`. El caso `success == true` deja el socket abierto.

**Corrección:** Cerrar el bootstrap siempre en el `finally`, independientemente de `success`. La conexión P2P permanente la maneja `P2PMeshService` / `P2PNetworkAdapter` después del bootstrap, no el socket de bootstrap.

Cambiar las líneas 106–111 en `DirectBootstrap.java`:

```java
			} finally {
				if (bootstrap != null) {
					try { bootstrap.close(); } catch (Exception ignored) {}
				}
			}
			if (success) return true;
```

**Nota importante:** Verificar que cerrar el bootstrap no interrumpa callbacks asincrónicos pendientes (sync responses que llegan después del `return true`). Si es necesario, agregar un pequeño delay antes de cerrar:

```java
			} finally {
				if (bootstrap != null) {
					if (success) {
						try { Thread.sleep(500); } catch (InterruptedException ignored) {}
					}
					try { bootstrap.close(); } catch (Exception ignored) {}
				}
			}
```

**Test de regresión:** Ejecutar `CoreWebSocketIntegrationTest` completo — los tests `directBootstrapJoin*` validan el flujo de bootstrap.

---

## PENDIENTE-2: UpdateChecker — JSON parsing con regex

**Severidad:** Media (fragilidad, no bug funcional actual)
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/UpdateChecker.java`

**Estado actual:** `TAG_PATTERN` y `URL_PATTERN` (líneas 30–31) usan regex para parsear la respuesta JSON de la GitHub API. Esto es frágil si el formato JSON cambia (por ejemplo, si `tag_name` aparece en metadata anidada o si hay quotes escapadas).

**Código actual (líneas 30–31):**

```java
	private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern URL_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");
```

**Código actual de uso (líneas 62–68):**

```java
				String body = response.body();
				Matcher tagMatcher = TAG_PATTERN.matcher(body);
				if (!tagMatcher.find()) return;
				String latestTag = tagMatcher.group(1).replace("v", "").trim();
				if (!isNewer(VERSION, latestTag)) return;

				List<String> urls = collectAssetUrls(body);
```

**Código actual de `collectAssetUrls` (líneas 88–93):**

```java
	private static List<String> collectAssetUrls(String json) {
		List<String> urls = new ArrayList<>();
		Matcher m = URL_PATTERN.matcher(json);
		while (m.find()) urls.add(m.group(1));
		return urls;
	}
```

**Corrección:** Reemplazar con `jakarta.json.JsonReader`, que ya está disponible como dependencia del proyecto (`jakarta.json-api` 1.1.6 y `org.glassfish:jakarta.json` 1.1.6 en `pom.xml`).

1. **Eliminar** las líneas 30–31 (las constantes `TAG_PATTERN` y `URL_PATTERN`).

2. **Eliminar** los imports de `java.util.regex.Matcher` y `java.util.regex.Pattern` (líneas 17–18). Verificar que no se usen en otro lugar del archivo.

3. **Reemplazar** las líneas 62–68 y el método `collectAssetUrls` (líneas 88–93) con:

```java
				String body = response.body();
				String latestTag;
				List<String> urls = new ArrayList<>();
				try (jakarta.json.JsonReader reader = jakarta.json.Json.createReader(new java.io.StringReader(body))) {
					jakarta.json.JsonObject release = reader.readObject();
					latestTag = release.getString("tag_name", "").replace("v", "").trim();
					if (latestTag.isEmpty() || !isNewer(VERSION, latestTag)) return;
					jakarta.json.JsonArray assets = release.getJsonArray("assets");
					if (assets != null) {
						for (jakarta.json.JsonValue asset : assets) {
							jakarta.json.JsonObject obj = asset.asJsonObject();
							String dlUrl = obj.getString("browser_download_url", null);
							if (dlUrl != null) urls.add(dlUrl);
						}
					}
				}
```

4. **Eliminar** el método `collectAssetUrls` completo (ya no se necesita).

5. Actualizar la variable `urls` que ya se usa en las líneas siguientes (69–70). Verificar que `hasJar` y `hasPackage` sigan funcionando igual.

**Test:** No hay tests para `UpdateChecker`. La validación es manual: compilar y verificar que no hay errores. Idealmente crear un test unitario mínimo (pero esto no es bloqueante).

---

## PENDIENTE-3: System.out.println de debug en Controller.java

**Severidad:** Baja (ruido en logs, impacto menor en rendimiento)
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`

**Estado actual:** Hay **35 instancias** de `System.out.println` en los métodos de refresh de idioma/tooltips. Fueron agregadas como debug durante la implementación de i18n en vivo. Ya no son necesarias.

**Líneas a eliminar (todas son `System.out.println` de debug):**

```
3175, 3185, 3189, 3191, 3214, 3217, 3230, 3238, 3245, 3249,
3253, 3257, 3260, 3264, 3266, 3272, 3275, 3278, 3281, 3284,
3287, 3290, 3293, 3296, 3299, 3302, 3305, 3308, 3311, 3314,
3317, 3320, 3323, 3331, 3339
```

**Métodos afectados:**
- `refreshLanguageTexts()` (líneas 3174+)
- `refreshAllFileTableColumns()` (líneas 3188+)
- `searchAndRefreshFileTables()` (líneas 3210+)
- `refreshAllTooltips()` (líneas 3229+)
- `updateTooltipsInContainer()` (líneas 3263+)

**Corrección:** Eliminar cada línea `System.out.println(...)` de debug en esos métodos. NO eliminar ninguna otra línea de lógica. Las líneas de debug se identifican porque todas empiezan con un prefijo `[Controller.` entre corchetes.

**Patrón de búsqueda para encontrar todas:**

```
System.out.println("[Controller.
```

**Alternativa (si se quiere conservar algún diagnóstico):** Reemplazar con `log.debug(...)` en lugar de eliminar. Pero dado que son logs de ciclo de refresh de UI que se ejecutan frecuentemente, la recomendación es eliminarlos.

**Importante:** NO eliminar los `System.out.println` que están en `Logger.java` (líneas 31, 38) — esos son el mecanismo intencional de logging de la app.

---

## PENDIENTE-4: Deprecated `new Locale(String)` en I18n.java

**Severidad:** Baja (deprecation warning en Java 21)
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/util/I18n.java`

**Estado actual:** `loadBundle()` en línea 26 ya usa `Locale.forLanguageTag()` (correcto), pero `setLocale()` en línea 17 sigue usando el constructor deprecated:

```java
	public static void setLocale(Locale locale) {
		activeLocale = new Locale(locale.getLanguage());
		bundle = loadBundle(activeLocale);
	}
```

**Corrección:** Reemplazar la línea 17:

```java
	public static void setLocale(Locale locale) {
		activeLocale = Locale.forLanguageTag(locale.getLanguage());
		bundle = loadBundle(activeLocale);
	}
```

---

## PENDIENTE-5: Dead code — serializeNotesStateInBackground

**Severidad:** Baja (dead code, no afecta runtime)
**Archivo:** `p2p-client/src/main/java/org/q3s/p2p/client/view/Controller.java`

**Estado actual:** El método `serializeNotesStateInBackground(StyledDocument doc, int docLength)` (líneas 3728–3761) ya no se llama desde ningún lugar. Fue reemplazado por la serialización directa en EDT dentro de `broadcastNotes()` (tarea 1.4 completada).

**Corrección:** Eliminar el método completo `serializeNotesStateInBackground` (líneas 3728–3761). Son 34 líneas de código muerto.

**Verificación previa:** Buscar `serializeNotesStateInBackground` en todo el proyecto para confirmar que no hay ninguna referencia:

```bash
grep -rn "serializeNotesStateInBackground" p2p-client/src/
```

Solo debe aparecer la declaración del método. Si aparece alguna llamada, NO eliminar.

---

## PENDIENTE-6: Duplicados en messages_en.properties

**Severidad:** Baja (ResourceBundle usa la última entrada, las primeras son dead code)
**Archivo:** `p2p-client/src/main/resources/i18n/messages_en.properties`

**Estado actual:** El archivo tiene keys duplicadas que fueron limpiadas en `messages.properties` pero no en este bundle:

```
Línea 72: tab.files=Files     ← primera aparición
Línea 99: tab.files=Files     ← duplicada

Línea 103: transfer.requesting=Requesting  ← primera aparición
Línea 108: transfer.requesting=Requesting  ← duplicada

Línea 104: transfer.retrying=Retrying      ← primera aparición
Línea 109: transfer.retrying=Retrying      ← duplicada
```

**Corrección:** Eliminar las líneas duplicadas **99, 108 y 109** del archivo `messages_en.properties`. Conservar las primeras apariciones (72, 103, 104).

Alternativamente, buscar el bloque duplicado y eliminarlo completo. El patrón es que hay un bloque de líneas 99–109 que repite keys ya definidas arriba.

---

## PENDIENTE-7: Test caso25 no renombrado

**Severidad:** Muy baja (nombre misleading, lógica ya corregida)
**Archivo:** `p2p-client/src/test/java/org/q3s/p2p/core/CoreQfolderTest.java`

**Estado actual:** La lógica del test fue corregida para usar `receiveRemoteEvent()` (valida correctamente), pero el nombre del método sigue siendo `caso25eventoDeNoAprobadoNoModificaEstado` (línea 357).

**Corrección:** Renombrar el método de test:

```java
// Antes (línea 357):
@Test void caso25eventoDeNoAprobadoNoModificaEstado() {

// Después:
@Test void caso25eventoDeNoAprobadoEsRechazadoPorValidacion() {
```

---

## PENDIENTE-8: AppConfigTest no creado

**Severidad:** Media (gap de cobertura de tests)
**Archivo a crear:** `p2p-client/src/test/java/org/q3s/p2p/client/AppConfigTest.java`

**Contexto:** `AppConfig` es la clase que carga propiedades desde archivos, system properties y variables de entorno. No tiene tests. La clase está en `p2p-client/src/main/java/org/q3s/p2p/client/AppConfig.java`.

**Tests prioritarios a implementar:**

```java
package org.q3s.p2p.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

	@Test
	void getReturnsDefaultWhenPropertyNotSet() {
		// AppConfig.get con key inexistente debe retornar el default
		String result = AppConfig.get("nonexistent.key.test", "myDefault");
		assertEquals("myDefault", result);
	}

	@Test
	void getIntReturnsDefaultForInvalidValue() {
		// Si la property no es un entero válido, debe retornar default
		// Nota: setear una system property con valor no-numérico antes si es posible,
		// o simplemente verificar con key inexistente
		int result = AppConfig.getInt("nonexistent.int.key.test", 42);
		assertEquals(42, result);
	}

	@Test
	void getBooleanReturnsFalseForEmptyString() {
		// String vacío no debería ser true
		// Verificar con key inexistente retorna default
		boolean result = AppConfig.getBoolean("nonexistent.bool.key.test", true);
		assertTrue(result); // default es true, key no existe, retorna default
	}

	@Test
	void getReturnsSystemPropertyOverDefault() {
		// System property tiene precedencia sobre default
		String key = "qfolder.test.sysprop." + System.nanoTime();
		System.setProperty(key, "fromSystemProp");
		try {
			String result = AppConfig.get(key, "defaultValue");
			// AppConfig.get busca primero en props cargadas, luego system props (depende de implementación)
			// Verificar la precedencia real de la implementación
			assertNotNull(result);
		} finally {
			System.clearProperty(key);
		}
	}
}
```

**Nota:** `AppConfig` usa `static` init con `Properties` cargadas una sola vez. Los tests pueden necesitar reflexión para re-cargar, o simplemente verificar el comportamiento de los getters con keys que no existen en los properties cargados. Revisar la implementación de `AppConfig.java` antes de escribir los tests para ajustar las assertions a la precedencia real (file > system property > default, o al revés).

---

## PENDIENTE-9: SpotBugs no habilitado en CI

**Severidad:** Media (quality gate ausente)
**Archivo:** `.github/workflows/maven-publish.yml`

**Estado actual (línea 29):**

```yaml
    - name: Build and test with Maven
      run: mvn -B verify --file p2p-client/pom.xml
```

SpotBugs solo corre bajo el perfil `static-analysis` definido en `pom.xml` (líneas 119–145), pero el CI no activa ese perfil.

**Corrección:** Agregar `-Pstatic-analysis` en la línea 29:

```yaml
    - name: Build and test with Maven
      run: mvn -B -Pstatic-analysis verify --file p2p-client/pom.xml
```

**Riesgo:** SpotBugs puede reportar nuevos findings que fallen el build (`<failOnError>true</failOnError>` en pom.xml línea 131). Antes de hacer este cambio, ejecutar localmente:

```bash
cd p2p-client && mvn -Pstatic-analysis verify
```

Si hay findings, corregirlos primero o cambiar `<failOnError>false</failOnError>` temporalmente.

---

## PENDIENTE-10: maven-compiler-plugin no actualizado

**Severidad:** Baja (funciona, pero usa versión vieja)
**Archivo:** `p2p-client/pom.xml`

**Estado actual (línea 16):**

```xml
		<compiler-plugin.version>3.8.1</compiler-plugin.version>
```

La versión 3.8.1 es de 2019. Para Java 21, es recomendable usar 3.13+ que soporta el flag `<release>`.

**Corrección:** Cambiar la línea 16:

```xml
		<compiler-plugin.version>3.13.0</compiler-plugin.version>
```

**Opcional (recomendado):** En la configuración del plugin (líneas 73–75), reemplazar `source`/`target` con `release`:

Las propiedades actuales (líneas 18–19) son:

```xml
		<maven.compiler.source>21</maven.compiler.source>
		<maven.compiler.target>21</maven.compiler.target>
```

Reemplazar por:

```xml
		<maven.compiler.release>21</maven.compiler.release>
```

Y eliminar las líneas de `maven.compiler.source` y `maven.compiler.target`.

**Nota:** `<release>` es más estricto que `<source>/<target>` — no solo compila para Java 21, sino que también verifica que no se usen APIs de versiones posteriores. Si hay errores de compilación, dejar `source`/`target` y solo actualizar la versión del plugin.

---

## Resumen

| ID | Tarea | Severidad | Estimación |
|---|---|---|---|
| PENDIENTE-1 | DirectBootstrap.join() WsClient leak en success | Alta | 15 min |
| PENDIENTE-2 | UpdateChecker regex → JsonReader | Media | 30 min |
| PENDIENTE-3 | Eliminar 35 System.out.println de Controller | Baja | 15 min |
| PENDIENTE-4 | Locale deprecated en I18n.setLocale | Baja | 5 min |
| PENDIENTE-5 | Dead code serializeNotesStateInBackground | Baja | 5 min |
| PENDIENTE-6 | Duplicados en messages_en.properties | Baja | 5 min |
| PENDIENTE-7 | Renombrar test caso25 | Muy baja | 5 min |
| PENDIENTE-8 | Crear AppConfigTest | Media | 30 min |
| PENDIENTE-9 | SpotBugs en CI | Media | 10 min |
| PENDIENTE-10 | Actualizar maven-compiler-plugin | Baja | 10 min |

**Total estimado:** ~2 horas de trabajo.

---

## Notas para el LLM Ejecutor

1. **Orden sugerido:** PENDIENTE-4, PENDIENTE-5, PENDIENTE-6, PENDIENTE-7 (triviales) → PENDIENTE-3 (mecánico) → PENDIENTE-1, PENDIENTE-2 (requieren cuidado) → PENDIENTE-8 (test nuevo) → PENDIENTE-9, PENDIENTE-10 (build/CI).
2. **Validación:** Después de cada cambio, ejecutar `cd p2p-client && mvn test`. Si algún test falla, corregir antes de avanzar.
3. **Build final:** Al terminar todo, ejecutar `./build.sh` para verificar que el fat JAR se genera correctamente.
4. **No refactorizar `Controller`:** Solo eliminar las líneas de `System.out.println` indicadas. No hacer refactors adicionales.
5. **Antes de PENDIENTE-9:** Ejecutar `cd p2p-client && mvn -Pstatic-analysis verify` localmente para verificar si SpotBugs pasa. Si no pasa, NO activar en CI hasta corregir findings.
6. **Commits:** Un commit por tarea o grupo de tareas relacionadas.

---

## Fase 11: Mejoras Arquitectónicas (sin cambios, referencia)

> Estas mejoras de mayor alcance no estaban en scope de esta ronda. Se conservan como referencia para futuros sprints.

- **11.1** Pipeline unificado de eventos (`EventPipeline`)
- **11.2** Separar proyecciones de estado (Membership/Content/MeshProjector)
- **11.3** Hacer `PEER_STATUS_UPDATED` efímero
- **11.4** Integrar `SnapshotService` en startup
- **11.5** CRDT para notas colaborativas
- **11.6** Optimizar `EventValidator` — eliminar O(n²) en bulk sync
- **11.7** Auto-reconnect en mesh tras fallo silencioso
- **11.8** Consolidar modelo dual de `Event`
