# PROMPT BIBLIA — SOCIALHUB
## Especificación maestra del producto, arquitectura, automatización e interfaz

Actúa como arquitecto de software senior, ingeniero full-stack, especialista en automatización mediante inteligencia artificial y diseñador de productos digitales.

Tu misión es diseñar y desarrollar **SocialHub**, una aplicación instalable, multiplataforma, modular y altamente automatizada para gestionar múltiples marcas, investigar oportunidades de contenido, producir publicaciones para redes sociales, programarlas, publicarlas, medir sus resultados y mejorar continuamente su estrategia.

Este documento constituye la fuente de verdad del proyecto. Debes respetar sus requisitos y decisiones. No debes sustituir las tecnologías elegidas ni simplificar los requisitos esenciales sin justificarlo.

No te limites a generar código de ejemplo. El objetivo es construir una aplicación funcional, mantenible, segura, portable y preparada para evolucionar.

---

# 1. VISIÓN DEL PRODUCTO

SocialHub es una plataforma de automatización de marketing y gestión de contenido social.

Su propósito es convertir la gestión de redes sociales en un proceso automatizado, observable y repetible, reduciendo al mínimo la intervención humana.

El usuario aporta inicialmente:

- Información sobre cada marca.
- Identidad visual y editorial.
- Fotografías, vídeos, documentos y otros materiales.
- Objetivos de cada promoción.
- Restricciones, preferencias y criterios de calidad.
- Credenciales y permisos necesarios para los servicios externos.

A partir de esa información, SocialHub debe ser capaz de investigar, proponer estrategias, generar contenido, preparar recursos audiovisuales, crear calendarios editoriales, programar publicaciones, publicarlas cuando sea técnicamente posible y analizar su rendimiento.

El usuario quiere pasar de una fase inicial de supervisión a otra de autonomía progresiva.

La aplicación no debe ser un simple cliente de Claude. Debe ser el sistema que coordina todas las operaciones, decide qué herramientas utilizar, administra las tareas, aplica las políticas de seguridad, controla los recursos disponibles y registra todos los resultados.

## 1.1. Principios fundamentales

1. **Automatización:** eliminar tareas manuales repetitivas.
2. **Autonomía progresiva:** empezar con supervisión humana y aumentar la autonomía cuando el sistema demuestre fiabilidad.
3. **Local First:** preferir herramientas locales gratuitas cuando puedan resolver una tarea con calidad suficiente.
4. **IA intercambiable:** permitir utilizar distintos proveedores, modelos y agentes.
5. **Ejecución híbrida:** dividir los trabajos en operaciones locales y operaciones de IA.
6. **Observabilidad:** poder saber qué está ocurriendo, por qué y con qué resultado.
7. **Control de recursos:** respetar cuotas, disponibilidad, límites y presupuestos.
8. **Multimarca:** gestionar marcas y promociones independientes mediante una plataforma común.
9. **Portabilidad:** poder instalar y ejecutar la aplicación en Windows y macOS.
10. **Fiabilidad:** utilizar estados explícitos, validaciones, reintentos controlados y registros.
11. **Trazabilidad:** registrar las fuentes, decisiones, herramientas, versiones y resultados.
12. **Simplicidad operativa:** una interfaz visual que permita comprender el sistema sin tener que navegar por numerosos menús administrativos.

---

# 2. CONCEPTO DIFERENCIAL DE LA INTERFAZ

## 2.1. Una fábrica digital navegable

La interfaz principal debe representar SocialHub como una cadena de producción de contenido, inspirada en la navegación por zoom de Miro y en la visualización de procesos industriales.

No se busca un dashboard convencional con una colección de gráficas y tablas.

Se busca una **fábrica digital visual, navegable, interactiva y en funcionamiento**.

Desde una vista global, el usuario debe poder observar el flujo completo de trabajo y conocer inmediatamente:

- Qué estaciones están funcionando.
- Qué procesos están ejecutándose.
- Qué tareas están pendientes.
- Qué elementos están bloqueados.
- Dónde existen errores.
- Qué promociones están activas.
- Qué publicaciones están preparándose o ya se han publicado.
- Qué recursos o agentes presentan limitaciones.

El usuario debe poder acercarse a una zona para descubrir sus detalles y acceder a acciones específicas.

## 2.2. Navegación mediante zoom semántico

El concepto central es el zoom semántico: al cambiar la escala, cambia también el nivel de información visible.

### Nivel 1: fábrica completa

La vista inicial muestra todo el sistema de producción.

Representar visualmente las estaciones conectadas de izquierda a derecha:

1. Investigación.
2. Selección y estrategia de contenido.
3. Generación de contenido.
4. Revisión y control de calidad.
5. Programación.
6. Publicación.
7. Análisis de resultados.
8. Retroalimentación y mejora de la estrategia.

La investigación puede ejecutarse de manera recurrente y el resultado del análisis debe alimentar futuras investigaciones y decisiones editoriales.

Cada estación muestra información resumida:

- Estado general.
- Número de tareas.
- Tareas en progreso.
- Tareas pendientes.
- Tareas bloqueadas.
- Errores recientes.
- Indicadores de actividad.

La cadena debe transmitir visualmente que los trabajos circulan por un proceso de producción.

Los elementos pueden representarse mediante estaciones, cintas transportadoras, nodos, conectores, tarjetas y pequeños indicadores de movimiento. El diseño debe ser moderno, limpio y comprensible, sin convertirse en una simulación industrial recargada.

### Nivel 2: detalle de una estación

Al hacer zoom sobre una estación, esta ocupa más espacio y muestra información adicional.

Por ejemplo, en Generación de contenido:

- Trabajos en ejecución.
- Porcentaje de progreso.
- Tiempo de ejecución.
- Tiempo estimado restante, si puede calcularse de forma fiable.
- Herramienta local o agente utilizado.
- Cuotas disponibles, cuando sean observables.
- Consumo y presupuesto.
- Cola de trabajos.
- Dependencias.
- Errores y reintentos.
- Resultado producido.

Deben aparecer acciones contextuales como:

- Ver detalles.
- Pausar.
- Reanudar.
- Reintentar.
- Cancelar.
- Cambiar herramienta.
- Reasignar agente.
- Consultar el resultado.
- Inspeccionar los registros.

Las acciones disponibles dependerán del estado real de cada trabajo.

### Nivel 3: detalle de una tarea

Al acercarse más o seleccionar un trabajo, el usuario puede inspeccionar su ejecución.

Mostrar:

- Objetivo original.
- Datos de entrada.
- Plan de ejecución.
- Pasos completados.
- Paso actual.
- Herramientas utilizadas.
- Resultados intermedios.
- Validaciones.
- Errores.
- Consumo.
- Historial de ejecución.
- Resultado final.
- Acciones permitidas.

### Nivel 4: centro de control de promoción

Cada promoción debe disponer de su propio centro de control, con una vista específica y reutilizable.

Una promoción es una unidad de trabajo independiente, asociada a una marca, objetivos, recursos, fechas, publicaciones y métricas.

Su centro de control debe permitir gestionar:

- Objetivos y estrategia.
- Calendario editorial.
- Publicaciones.
- Borradores.
- Fotografías, vídeos y documentos.
- Recursos de Google Drive.
- Investigación y fuentes.
- Flujos de automatización.
- Agentes y herramientas utilizados.
- Estados de ejecución.
- Presupuestos y cuotas.
- Incidencias.
- Métricas y resultados.
- Configuración específica de la promoción.

Debe poderse acceder al centro de control desde la vista global y regresar fácilmente al mapa principal.

## 2.3. Reglas de interacción

La interfaz debe proporcionar:

- Zoom con rueda del ratón.
- Desplazamiento por el lienzo.
- Navegación mediante ratón y gestos táctiles cuando sea viable.
- Selección de estaciones y elementos.
- Apertura de detalles mediante clic.
- Indicadores de selección.
- Ruta de navegación, por ejemplo: SocialHub / Litoques / Lanzamiento del álbum.
- Buscador global.
- Controles para volver a la vista general.
- Ajuste automático para encuadrar el conjunto.
- Acciones contextuales según la selección.

El zoom debe modificar el nivel de detalle, no limitarse a ampliar o reducir tarjetas.

Cuando sea necesario ejecutar acciones precisas, la interfaz puede abrir un panel lateral o una vista de detalle sin perder el contexto del mapa.

No implementar un editor gráfico generalista. Construir un mapa operativo especializado en automatización de contenido.

## 2.4. Estados visuales

Utilizar estados semánticos consistentes:

- OK: funcionamiento correcto.
- En progreso: trabajo ejecutándose.
- En espera: pendiente de una fecha, aprobación, recurso o disponibilidad.
- Bloqueado: necesita resolver una dependencia o incidencia.
- Error: ejecución fallida.
- Inactivo: estación o proceso sin trabajo activo.
- Cancelado: ejecución detenida intencionadamente.

Cada estado debe tener icono, etiqueta y color. No utilizar únicamente colores para comunicar el estado.

Distinguir siempre el estado de una estación del estado de sus tareas. Una estación puede estar operativa aunque contenga trabajos en espera.

**Regla crítica:** el estado real se obtiene de los datos persistidos y del motor de ejecución. Nunca se deduce de la posición, el color o la apariencia de un elemento en el lienzo.

---

# 3. MULTIMARCA Y MULTIPROMOCIÓN

SocialHub debe ser reutilizable para distintas marcas.

Marcas iniciales de referencia:

- Litoques: proyecto musical y promoción de un álbum.
- El Delito: marca o proyecto promocional.
- Otras marcas futuras que se incorporarán sin tener que modificar la arquitectura.

Estos nombres son ejemplos de configuración inicial, no entidades que deban estar codificadas de forma fija en el programa.

## 3.1. Modelo conceptual

La jerarquía principal será:

- SocialHub.
  - Marcas.
    - Promociones.
      - Objetivos.
      - Calendario.
      - Publicaciones.
      - Recursos.
      - Tareas.
      - Métricas.
      - Historial.

Una marca puede tener múltiples promociones activas, pausadas, planificadas o finalizadas.

Cada promoción debe disponer de configuración propia, pero reutilizar los servicios generales de la aplicación.

## 3.2. Aislamiento

Los datos de una marca no deben mezclarse accidentalmente con los de otra.

Cada recurso, publicación, investigación, configuración o resultado debe estar asociado a su contexto correspondiente.

El sistema debe permitir configurar por marca:

- Identidad visual.
- Tono editorial.
- Público objetivo.
- Idiomas.
- Plataformas.
- Frecuencia de publicación.
- Temas permitidos.
- Temas prohibidos.
- Objetivos.
- Recursos disponibles.
- Presupuesto.
- Reglas de aprobación.
- Políticas de autonomía.

Las promociones podrán heredar la configuración de la marca y sobrescribir los parámetros que necesiten.

---

# 4. PLATAFORMAS SOCIALES

Las plataformas iniciales prioritarias son:

- Instagram.
- TikTok.

Diseñar una arquitectura extensible que permita incorporar nuevas plataformas mediante adaptadores independientes.

La lógica general de campañas y publicaciones no debe depender directamente de la API concreta de una red social.

## 4.1. Adaptadores

Crear una abstracción común para operaciones como:

- Comprobar la disponibilidad de una cuenta.
- Validar los requisitos de publicación.
- Consultar las capacidades de la plataforma.
- Publicar contenido cuando esté permitido.
- Consultar el estado de una publicación.
- Obtener métricas disponibles.
- Detectar errores de autenticación.
- Gestionar limitaciones.
- Registrar identificadores externos.

Las capacidades reales variarán según la plataforma, los permisos, la versión de la API y el tipo de cuenta.

No prometer funcionalidades que la API no permita.

## 4.2. Publicación automática y alternativas

La publicación automática debe utilizar mecanismos oficiales y autorizados.

Antes de implementar cada integración, verificar:

- Requisitos de acceso.
- Permisos.
- Tipo de cuenta.
- Formatos admitidos.
- Límites de frecuencia.
- Requisitos de revisión o auditoría.
- Disponibilidad de métricas.
- Restricciones para publicar vídeos, imágenes o carruseles.

Si no existe acceso suficiente a la API, ofrecer una alternativa controlada:

- Generar el contenido.
- Preparar los archivos.
- Exportar los recursos.
- Mostrar instrucciones para publicar manualmente.
- Registrar que la publicación necesita intervención humana.

No simular que una publicación se ha realizado cuando únicamente se ha generado un archivo.

---

# 5. GOOGLE DRIVE COMO REPOSITORIO CENTRAL

Google Drive será el repositorio principal de documentación y archivos de cada marca.

SocialHub debe poder acceder mediante la integración oficial y los mecanismos de autenticación adecuados.

Cada marca debe disponer de una estructura de carpetas configurable. Por ejemplo:

- Marca.
  - Identidad y estrategia.
  - Material original.
  - Fotografías.
  - Vídeos.
  - Audio.
  - Documentación.
  - Investigación.
  - Promociones.
  - Calendario.
  - Borradores.
  - Recursos generados.
  - Contenido aprobado.
  - Contenido publicado.
  - Informes.

La estructura definitiva debe ser configurable, no una obligación rígida.

## 5.1. Operaciones

La integración debe permitir, según los permisos disponibles:

- Listar archivos.
- Buscar material.
- Leer documentos.
- Descargar recursos.
- Subir archivos generados.
- Actualizar metadatos compatibles.
- Asociar recursos a promociones.
- Mantener referencias a los originales.
- Registrar la ubicación de los entregables.

Guardar en PostgreSQL los metadatos, las relaciones, los identificadores y los estados operativos. Utilizar Drive para los archivos y documentos que corresponda.

No duplicar innecesariamente archivos pesados dentro de la base de datos.

Debe poderse recuperar el estado de un trabajo aunque el proceso de la aplicación se interrumpa.

---

# 6. MOTOR DE INVESTIGACIÓN CONTINUA

La investigación no debe limitarse a la fase inicial de una campaña.

SocialHub debe disponer de un motor de investigación recurrente que genere oportunidades de contenido y ayude a adaptar la estrategia.

## 6.1. Fuentes

Considerar, cuando exista acceso legítimo y técnicamente viable:

- Documentación de Google Drive.
- Material aportado por el usuario.
- Publicaciones anteriores.
- Resultados históricos.
- Fuentes públicas de información.
- Noticias y tendencias relevantes.
- Eventos.
- Calendarios temáticos.
- Fuentes oficiales de las plataformas.
- Métricas y datos obtenidos mediante APIs autorizadas.

Respetar las condiciones de uso, los derechos de autor, las restricciones de acceso y la privacidad.

## 6.2. Actividades

El motor debe poder:

1. Analizar el material disponible.
2. Detectar temas que todavía no se han tratado.
3. Identificar oportunidades de contenido.
4. Investigar tendencias relacionadas con cada marca.
5. Proponer enfoques y formatos.
6. Detectar contenido repetitivo.
7. Evaluar publicaciones anteriores.
8. Actualizar hipótesis editoriales.
9. Generar propuestas de publicaciones.
10. Recomendar cambios en el calendario.
11. Registrar las fuentes utilizadas.
12. Identificar información dudosa o insuficientemente contrastada.

No presentar como hechos confirmados afirmaciones que no estén respaldadas.

## 6.3. Programación

Permitir investigaciones:

- Diarias.
- Semanales.
- Quincenales.
- Mensuales.
- En fechas configuradas.
- Al incorporarse nuevo material.
- Al iniciarse una promoción.
- Ante determinados eventos o resultados.

Los intervalos deben ser configurables por marca y promoción.

La investigación debe tener su propia estación en el mapa de fábrica, con estado, actividad, tareas pendientes y errores.

---

# 7. ORQUESTADOR DE IA MULTIPROVEEDOR

La IA debe ser configurable y sustituible.

El usuario comienza utilizando Claude Pro y prevé incorporar OpenCode como siguiente opción de ejecución o agente.

No codificar el proyecto para que dependa exclusivamente de Claude.

## 7.1. Distinción entre proveedor, modelo y agente

La arquitectura debe distinguir:

- **Proveedor:** servicio o infraestructura que ofrece capacidades de IA.
- **Modelo:** modelo concreto utilizado.
- **Agente o runtime:** entorno que ejecuta instrucciones, herramientas y flujos.
- **Tarea:** trabajo que SocialHub necesita realizar.
- **Adaptador:** integración técnica entre SocialHub y un proveedor o runtime.

OpenCode debe tratarse como un agente/runtime que puede trabajar con distintos proveedores y modelos compatibles, no como un único modelo de IA.

## 7.2. Configuración

Permitir:

- Añadir proveedores.
- Desactivar proveedores.
- Configurar modelos.
- Asociar agentes a tareas.
- Definir capacidades.
- Configurar credenciales.
- Establecer prioridades.
- Definir límites de concurrencia.
- Configurar presupuestos.
- Establecer políticas de fallback.
- Seleccionar un agente preferido por tipo de tarea.
- Ejecutar tareas mediante varios agentes cuando tenga sentido.
- Registrar el historial de decisiones y ejecuciones.

Los adaptadores deben ser independientes de la lógica de negocio.

## 7.3. Claude Pro

Claude Pro será el punto de partida del proyecto.

Sin embargo, no se debe asumir que una suscripción de usuario incluye acceso ilimitado a la API, que autoriza cualquier automatización desatendida o que puede reutilizarse mediante cualquier runtime externo.

Antes de integrar cada mecanismo, verificar qué métodos de autenticación y ejecución están oficialmente soportados.

La aplicación debe separar la suscripción de usuario, el acceso a la API y la ejecución mediante herramientas externas.

No utilizar credenciales de formas que contradigan las condiciones del proveedor.

## 7.4. OpenCode

Preparar la arquitectura para incorporar OpenCode sin reescribir los módulos de campañas, publicaciones o investigación.

Su integración debe comprobar:

- Método de ejecución.
- Compatibilidad con el sistema operativo.
- Proveedores y modelos disponibles.
- Autenticación admitida.
- Capacidades de herramientas.
- Límites y cuotas observables.
- Formatos de entrada y salida.
- Gestión de procesos.
- Tratamiento de errores.

No asumir que la autenticación de una suscripción Claude Pro puede reutilizarse directamente en OpenCode. Si se requiere otro proveedor o método de acceso, debe configurarse expresamente.

## 7.5. Ejecución de tareas con varios agentes

El sistema podrá dividir un trabajo en subtareas y asignarlas a diferentes agentes.

Ejemplo:

- Un agente investiga tendencias.
- Otro propone conceptos editoriales.
- Un modelo genera texto.
- Una herramienta local prepara imágenes.
- Otro agente revisa la coherencia de la propuesta.
- El backend valida el resultado y registra la publicación.

La ejecución paralela solo se utilizará cuando aporte valor y no genere duplicidades ni costes innecesarios.

---

# 8. GESTIÓN DE LÍMITES, DISPONIBILIDAD Y PRESUPUESTOS

SocialHub debe administrar el consumo de proveedores de IA.

No debe lanzar tareas indiscriminadamente.

## 8.1. Datos que deben registrarse

Cuando estén disponibles:

- Proveedor.
- Modelo.
- Agente.
- Tipo de tarea.
- Inicio y finalización.
- Duración.
- Tokens de entrada y salida.
- Consumo económico.
- Cuota disponible.
- Límite aplicable.
- Número de solicitudes.
- Estado de ejecución.
- Errores.
- Reintentos.
- Fecha estimada de recuperación.
- Fuente de la información sobre el límite.

Distinguir entre valores confirmados, estimados y desconocidos.

No inventar cuotas ni asumir que un proveedor expone todos sus límites.

## 8.2. Motor de cuotas

Antes de ejecutar una tarea, comprobar:

1. Si el proveedor está habilitado.
2. Si las credenciales funcionan.
3. Si el método de autenticación está soportado.
4. Si existe capacidad disponible.
5. Si la tarea entra dentro del presupuesto.
6. Si se ha alcanzado algún límite.
7. Si existe una alternativa compatible.
8. Si la prioridad justifica esperar o utilizar otra opción.

Cuando no se pueda ejecutar una tarea:

- Dejarla en cola.
- Registrar la causa.
- Estimar cuándo podría reanudarse, si existen datos suficientes.
- Permitir que otros trabajos independientes continúen.
- Reintentar de forma controlada.
- Utilizar una alternativa autorizada cuando proceda.

No realizar reintentos ilimitados.

## 8.3. Control de gastos

No utilizar servicios de pago sin que estén expresamente autorizados en la configuración.

Permitir presupuestos globales, por proveedor, marca y promoción.

Configurar límites de consumo y políticas de parada.

Si el coste no puede determinarse con fiabilidad, mostrarlo como desconocido en lugar de inventar una cifra.

---

# 9. PRINCIPIO LOCAL FIRST Y EJECUCIÓN HÍBRIDA

Este principio es obligatorio en toda la arquitectura.

**Antes de ejecutar cualquier tarea mediante un proveedor de IA, SocialHub debe evaluar si existe una solución local, gratuita, compatible con el sistema operativo y suficientemente fiable. Debe preferirla cuando cumpla los requisitos de calidad. Las tareas que requieran razonamiento, investigación, creatividad o capacidades que no existan localmente podrán delegarse a uno o varios agentes de IA, respetando sus límites, disponibilidad, permisos y presupuestos. Todas las decisiones, ejecuciones, costes y resultados deben quedar registrados.**

No se debe enviar una tarea completa a la IA cuando pueda dividirse en operaciones locales y operaciones inteligentes.

## 9.1. Modos de ejecución

Implementar tres políticas configurables:

### Local First

Modo predeterminado.

Utiliza herramientas locales siempre que cumplan los requisitos de calidad. La IA se utiliza cuando aporta capacidades necesarias.

### Híbrido

Permite combinar herramientas locales y agentes de IA en una misma cadena de ejecución, alternando pasos.

### IA autorizada

Permite delegar tareas a proveedores externos cuando se haya autorizado su uso y presupuesto.

Este modo no debe anular los límites de seguridad, autenticación ni las condiciones del proveedor.

## 9.2. Ejemplo: creación de una publicación gráfica

Objetivo: preparar una imagen promocional para Instagram.

Secuencia posible:

1. Localizar la fotografía original en Google Drive.
2. Descargarla al espacio temporal de trabajo.
3. Analizar dimensiones, formato y orientación.
4. Recortar la imagen mediante ImageMagick.
5. Ajustar resolución y compresión.
6. Reservar las zonas de composición.
7. Enviar a un agente de IA la imagen preparada y el contexto de marca.
8. Generar un concepto creativo y el texto.
9. Si se necesita una imagen nueva, utilizar un generador compatible y autorizado.
10. Validar el resultado generado.
11. Componer localmente los textos y logotipos con tipografías reales cuando sea viable.
12. Añadir marcas de agua o elementos de identidad.
13. Exportar el formato final.
14. Validar resolución, peso, legibilidad y requisitos de la plataforma.
15. Guardar el resultado en Drive.
16. Asociar el archivo a la publicación.
17. Actualizar el estado del trabajo.

El flujo concreto dependerá de la tarea. No es obligatorio utilizar IA en todas las fases.

## 9.3. Herramientas locales prioritarias

Evaluar e integrar, según necesidad:

- **ImageMagick:** recorte, redimensionamiento, conversión, composición, marcas de agua, ajustes de imagen y optimización.
- **FFmpeg:** recorte de vídeo, conversión, mezcla de audio, subtítulos, fundidos, superposiciones, extracción de fotogramas y montaje.
- **Whisper.cpp:** transcripción local y subtítulos cuando sea viable.
- **SVG:** composición vectorial, textos, logotipos y plantillas reproducibles.
- **GIMP:** edición gráfica avanzada cuando sea necesaria.
- **Kdenlive o Shotcut:** edición audiovisual manual avanzada.
- Otras herramientas gratuitas que demuestren utilidad y compatibilidad.

Verificar licencias, dependencias, compatibilidad con Windows y macOS y posibilidades reales de automatización antes de convertir una herramienta en dependencia obligatoria.

## 9.4. Registro de capacidades locales

Implementar un módulo `local-tool-registry` que permita conocer:

- Herramientas instaladas.
- Versiones.
- Compatibilidad con el sistema operativo.
- Capacidades disponibles.
- Licencia.
- Requisitos de recursos.
- Estado de disponibilidad.
- Errores de instalación.
- Comandos o acciones admitidos.
- Resultados de comprobaciones.

El registro debe permitir evaluar si una operación puede resolverse localmente.

## 9.5. Ejecución segura

No ejecutar directamente comandos de shell inventados por un agente.

Las herramientas locales deben exponerse mediante operaciones estructuradas, parámetros validados y listas de acciones permitidas.

Validar rutas, formatos, tamaños y argumentos.

Evitar ejecución arbitraria, acceso no autorizado a archivos, sobrescrituras accidentales y exposición de credenciales.

Cada operación debe tener límites de tiempo, manejo de errores y mecanismos de cancelación cuando sea viable.

---

# 10. MOTOR DE PRODUCCIÓN DE CONTENIDO

El sistema debe transformar objetivos editoriales en publicaciones listas para programar.

## 10.1. Tipos de contenido

Diseñar una arquitectura que permita gestionar:

- Publicaciones de texto.
- Imágenes.
- Carruseles.
- Vídeos.
- Reels y formatos equivalentes.
- Subtítulos.
- Descripciones.
- Hashtags.
- Llamadas a la acción.
- Variantes para distintas plataformas.
- Material audiovisual complementario.

No asumir que todos los formatos son compatibles con todas las redes.

## 10.2. Plantillas e identidad

Cada marca debe poder definir:

- Colores.
- Tipografías.
- Logotipos.
- Estilo visual.
- Estilo fotográfico.
- Tono de comunicación.
- Vocabulario.
- Expresiones preferidas.
- Expresiones prohibidas.
- Público objetivo.
- Restricciones temáticas.

Las plantillas deben facilitar una producción consistente, sin impedir que la IA proponga variaciones creativas.

## 10.3. Memoria editorial

El sistema debe almacenar suficiente contexto para evitar que cada generación empiece desde cero.

Considerar:

- Publicaciones anteriores.
- Temas tratados.
- Conceptos utilizados.
- Recursos disponibles.
- Resultados obtenidos.
- Decisiones editoriales.
- Preferencias y correcciones del usuario.
- Contenido rechazado y motivos.
- Campañas activas.
- Objetivos de cada marca.

Utilizar esta memoria para reducir repeticiones y mejorar la coherencia.

## 10.4. Control de calidad

Antes de aceptar una publicación, comprobar:

- Asociación correcta con la marca y promoción.
- Cumplimiento de identidad visual.
- Coherencia del mensaje.
- Ortografía.
- Requisitos del formato.
- Resolución y dimensiones.
- Ausencia de duplicidades evidentes.
- Existencia de fuentes cuando se presentan afirmaciones factuales.
- Derechos de uso conocidos.
- Restricciones editoriales.
- Requisitos específicos de la plataforma.

Las comprobaciones deterministas deben implementarse mediante código local cuando sea posible.

La evaluación semántica podrá utilizar IA si resulta necesaria y está autorizada.

---

# 11. CALENDARIO EDITORIAL Y PUBLICACIONES

Cada marca y promoción debe tener un calendario propio.

## 11.1. Datos de publicación

Cada publicación debe almacenar:

- Marca.
- Promoción.
- Plataforma.
- Tipo de contenido.
- Texto.
- Recursos multimedia.
- Fecha prevista.
- Zona horaria.
- Estado.
- Identificador externo, cuando exista.
- Fecha real de publicación.
- Resultado de la publicación.
- Historial de modificaciones.
- Referencias a tareas de producción.
- Métricas disponibles.

## 11.2. Estados

Como mínimo:

- Idea.
- Propuesta.
- En producción.
- Borrador.
- Pendiente de revisión.
- Aprobada.
- Programada.
- Publicando.
- Publicada.
- Error de publicación.
- Cancelada.

Las transiciones deben estar definidas y validadas en el backend.

No permitir que una publicación pase a Publicada simplemente porque una tarea de generación haya terminado.

## 11.3. Reglas operativas

- Evitar duplicados.
- Respetar la frecuencia configurada.
- Evitar publicaciones fuera de los periodos permitidos.
- Validar que todos los recursos estén disponibles.
- Comprobar que la cuenta y los permisos sean válidos.
- Gestionar fallos temporales.
- Registrar los intentos.
- Evitar publicaciones dobles por reintentos.
- Mantener una política clara de cancelación y reprogramación.

Las tareas programadas deben ser persistentes y recuperables después de reiniciar la aplicación.

---

# 12. PUBLICACIÓN, MÉTRICAS Y MEJORA CONTINUA

Una vez publicada una pieza, SocialHub debe continuar su ciclo de vida.

Cuando las plataformas permitan obtener datos, registrar métricas como:

- Alcance.
- Impresiones.
- Interacciones.
- Reproducciones.
- Comentarios.
- Compartidos.
- Guardados.
- Clics.
- Crecimiento de audiencia.
- Otras métricas disponibles.

No asumir que todas las plataformas exponen todos los indicadores.

## 12.1. Análisis

El sistema debe comparar resultados considerando el contexto:

- Marca.
- Promoción.
- Plataforma.
- Formato.
- Tema.
- Fecha y hora.
- Tipo de mensaje.
- Objetivo de la publicación.

Evitar conclusiones basadas en muestras insuficientes.

## 12.2. Retroalimentación

Utilizar los resultados para:

- Detectar formatos prometedores.
- Identificar temas saturados.
- Proponer nuevas líneas editoriales.
- Ajustar la planificación.
- Revisar hipótesis.
- Recomendar experimentos.
- Generar informes periódicos.

Las modificaciones de estrategia deben quedar registradas.

No permitir que una variación aislada en métricas provoque cambios drásticos e irreversibles.

La estación de resultados debe alimentar de nuevo la estación de investigación, cerrando el ciclo de producción.

---

# 13. AUTONOMÍA Y SUPERVISIÓN HUMANA

El sistema debe permitir evolucionar de la supervisión inicial a la autonomía.

## 13.1. Modo supervisado

Durante la fase inicial:

- La IA propone.
- SocialHub prepara los recursos.
- El usuario revisa las primeras publicaciones.
- Se registran correcciones y preferencias.
- Se verifican las integraciones.
- Se evalúa la calidad real.

## 13.2. Modo autónomo

Cuando se haya configurado y validado:

- Investigación recurrente.
- Propuesta de contenido.
- Generación de recursos.
- Validación automática.
- Programación.
- Publicación autorizada.
- Obtención de métricas.
- Análisis.
- Mejora de la estrategia.

No exigir aprobación manual para cada operación rutinaria si la política de la promoción permite autonomía.

## 13.3. Políticas configurables

Permitir configurar por marca y promoción:

- Nivel de autonomía.
- Revisión humana obligatoria.
- Tipos de contenido permitidos.
- Límites de frecuencia.
- Presupuesto de IA.
- Proveedores autorizados.
- Horarios.
- Acciones prohibidas.
- Criterios de calidad.
- Reglas de escalado de incidencias.

## 13.4. Paradas de seguridad

Pausar los trabajos afectados ante situaciones como:

- Credenciales caducadas.
- Errores repetidos.
- Derechos de uso dudosos.
- Afirmaciones factuales sin fuentes suficientes.
- Presupuesto agotado.
- Cambios de permisos.
- Fallos importantes de validación.
- Duplicidades potencialmente dañinas.
- Cambios en las condiciones de publicación.

Una incidencia de una promoción no debe detener automáticamente las demás promociones si son independientes.

---

# 14. ARQUITECTURA TÉCNICA

## 14.1. Tecnologías elegidas

La base tecnológica acordada es:

- Java: utilizar la versión estable más reciente que sea compatible y adecuada al proyecto, verificando su estado de soporte.
- Spring Boot 4.
- PostgreSQL.
- Maven.
- Spring MVC.
- Spring Data.
- Flyway.
- JUnit 5.
- Mockito.

La versión concreta de Java y de cada dependencia debe verificarse al iniciar el proyecto. No fijar versiones inexistentes ni incompatibles.

El objetivo del usuario es utilizar Java de última generación. Si se plantea elegir entre una versión de funcionalidades recientes y una versión LTS, explicar claramente las diferencias y tomar una decisión técnica documentada antes de fijar el entorno.

## 14.2. Organización arquitectónica

Empezar con un **monolito modular**, con límites de dominio claros y arquitectura hexagonal.

No introducir microservicios innecesarios.

La aplicación debe poder evolucionar sin acoplar la lógica de negocio a proveedores externos o herramientas concretas.

Separar:

- Dominio.
- Casos de uso.
- Aplicación.
- Infraestructura.
- Adaptadores externos.
- API.
- Interfaz de usuario.

La lógica de dominio no debe depender directamente de SDK de proveedores, APIs de redes sociales ni comandos del sistema operativo.

## 14.3. Módulos propuestos

Como mínimo, evaluar los siguientes módulos:

1. `brand-management`
2. `promotion-management`
3. `content-management`
4. `research-engine`
5. `research-scheduler`
6. `editorial-strategy`
7. `content-memory`
8. `media-pipeline`
9. `local-tool-registry`
10. `ai-orchestrator`
11. `ai-provider-adapters`
12. `ai-usage-management`
13. `quality-gates`
14. `autonomy-policy`
15. `publishing-engine`
16. `social-platform-adapters`
17. `calendar-management`
18. `analytics-engine`
19. `drive-adapter`
20. `task-execution`
21. `audit-log`
22. `control-center-api`

Estos módulos representan responsabilidades lógicas. No es necesario que cada uno se convierta en un módulo Maven independiente desde el primer día.

## 14.4. Ejecución de tareas

Las tareas deben modelarse como trabajos persistentes.

Cada trabajo debe tener:

- Identificador.
- Tipo.
- Contexto de marca y promoción.
- Entradas.
- Estado.
- Prioridad.
- Dependencias.
- Intentos.
- Fechas.
- Resultados.
- Errores.
- Herramientas utilizadas.
- Consumo.
- Historial.

Diseñar una máquina de estados explícita y transiciones válidas.

Gestionar concurrencia, bloqueos, reintentos, idempotencia y recuperación tras reinicios.

Evitar que dos procesos ejecuten simultáneamente la misma tarea cuando no esté permitido.

Las tareas independientes deben poder continuar aunque una tarea concreta esté bloqueada.

## 14.5. Interfaz frontend

La experiencia principal será una SPA de centro de control visual.

Evaluar una solución de frontend que permita:

- Lienzo navegable.
- Zoom semántico.
- Desplazamiento.
- Estaciones conectadas.
- Elementos seleccionables.
- Paneles contextuales.
- Actualización de estados.
- Calendario.
- Detalle de promociones.

Puede evaluarse React Flow u otra biblioteca adecuada para la visualización de procesos, o una solución de canvas si ofrece mejor experiencia para el zoom libre.

La elección debe justificarse mediante un prototipo funcional, no solo por familiaridad con una biblioteca.

El frontend no será la fuente de verdad de los estados.

## 14.6. API

Diseñar endpoints claros y versionables para:

- Marcas.
- Promociones.
- Publicaciones.
- Calendarios.
- Tareas.
- Estaciones.
- Agentes.
- Proveedores.
- Cuotas.
- Recursos.
- Métricas.
- Configuración.
- Auditoría.

El frontend debe consultar información real del backend, no depender de datos ficticios incrustados en los componentes.

Los datos de demostración deben estar aislados y claramente identificados.

---

# 15. MODELO DE DATOS INICIAL

Diseñar el modelo de PostgreSQL a partir de los dominios anteriores.

Evaluar, como mínimo, las siguientes entidades:

- `brand`
- `brand_configuration`
- `promotion`
- `promotion_goal`
- `promotion_policy`
- `content_item`
- `content_asset`
- `content_source`
- `editorial_plan`
- `publication`
- `publication_platform`
- `publication_attempt`
- `publication_metric`
- `research_run`
- `research_finding`
- `research_source`
- `task`
- `task_dependency`
- `task_execution`
- `task_event`
- `local_tool`
- `local_tool_capability`
- `ai_provider`
- `ai_model`
- `ai_agent`
- `ai_credential_ref`
- `ai_usage_window`
- `ai_usage_event`
- `ai_task`
- `ai_routing_policy`
- `ai_budget`
- `ai_execution_log`
- `scheduled_job`
- `drive_resource`
- `audit_event`
- `application_configuration`

No crear tablas innecesarias sin justificar su responsabilidad.

Utilizar Flyway para versionar el esquema.

Definir índices, restricciones, claves foráneas y reglas de borrado.

Mantener integridad referencial y consistencia de estados.

Evitar almacenar secretos en texto plano.

Los datos operativos deben conservar suficiente historial para diagnosticar errores y reconstruir las decisiones relevantes.

---

# 16. PORTABILIDAD E INSTALACIÓN

SocialHub debe poder instalarse desde cero y ejecutarse en Windows y macOS.

La instalación debe contemplar:

- Comprobación de requisitos.
- Configuración de Java o runtime empaquetado.
- Instalación o configuración de PostgreSQL.
- Creación de la base de datos.
- Ejecución de migraciones.
- Configuración inicial.
- Autenticación con Google Drive.
- Configuración de proveedores de IA.
- Instalación o detección de herramientas locales.
- Verificación de conectividad.
- Copias de seguridad.
- Actualización de la aplicación.

## 16.1. PostgreSQL

PostgreSQL es una decisión explícita del proyecto.

No sustituirlo silenciosamente por SQLite.

Como PostgreSQL requiere un proceso de servidor, diseñar una estrategia clara para su instalación y ejecución local:

- Instalación administrada por el instalador o scripts.
- Configuración local documentada.
- O una alternativa empaquetada técnicamente viable, si se demuestra que funciona en los sistemas objetivo.

Evaluar el uso de Docker como opción, pero no asumir que está instalado.

## 16.2. Datos y rutas

- Configurar un directorio de datos.
- Evitar rutas absolutas específicas de un único ordenador.
- Separar configuración y datos.
- Facilitar copias de seguridad.
- Permitir restauración.
- No incluir secretos en el repositorio.
- No depender de rutas de usuario concretas.

Los archivos originales y entregables permanecerán principalmente en Google Drive, mientras que la base de datos mantendrá el estado operativo.

## 16.3. Varios ordenadores

La aplicación debe poder reinstalarse en otro ordenador.

No asumir que las credenciales OAuth o los tokens de un dispositivo se pueden copiar de forma segura al siguiente.

Preparar un procedimiento de reautorización y recuperación.

Si varias instalaciones pueden acceder a los mismos datos y ejecutar publicaciones, diseñar mecanismos de exclusión y coordinación para evitar ejecuciones duplicadas.

No afirmar que el sistema es multiusuario o distribuido hasta que se haya implementado y probado esa capacidad.

---

# 17. SEGURIDAD Y SECRETOS

Implementar controles desde el principio:

- Gestión segura de credenciales.
- Separación entre configuración y secretos.
- Protección de tokens.
- Autenticación OAuth donde corresponda.
- Validación de entradas.
- Restricciones de acceso a archivos.
- Ejecución controlada de herramientas locales.
- Registro de acciones sensibles.
- Tratamiento seguro de errores.
- No exposición de secretos en logs.
- No ejecución directa de instrucciones arbitrarias recibidas de un agente.

Las instrucciones, documentos y contenido recuperados de Drive o de fuentes externas deben tratarse como datos no confiables. No deben poder modificar las políticas de seguridad de la aplicación.

Separar el contenido que procesa la IA de las instrucciones internas que controlan el sistema.

Toda acción de publicación debe respetar la política de permisos y autonomía.

---

# 18. CONTRATO ENTRE IA Y BACKEND

Los agentes no deben controlar el estado de la aplicación mediante texto libre.

Definir contratos estructurados y versionados, preferentemente JSON, para comunicar:

- Objetivo.
- Contexto.
- Entradas.
- Plan propuesto.
- Herramientas solicitadas.
- Parámetros.
- Resultado.
- Fuentes.
- Advertencias.
- Estado.
- Errores.

El backend debe validar cada resultado antes de ejecutarlo o persistirlo.

La IA puede proponer operaciones, pero el motor de ejecución decide si están permitidas.

No ejecutar comandos arbitrarios recibidos en una respuesta.

Las operaciones deben estar limitadas por capacidades, políticas y permisos.

Si una respuesta no cumple el contrato, registrar el problema y solicitar una corrección o marcar la tarea como fallida de forma controlada.

---

# 19. OBSERVABILIDAD Y AUDITORÍA

El sistema debe permitir responder a preguntas como:

- ¿Qué está haciendo ahora?
- ¿Por qué está bloqueada una tarea?
- ¿Qué agente ha generado un recurso?
- ¿Qué herramienta local se ha utilizado?
- ¿Cuánto ha costado?
- ¿Qué fuente respalda una propuesta?
- ¿Por qué se ha elegido una alternativa?
- ¿Qué publicaciones se han realizado?
- ¿Qué fallos han ocurrido?
- ¿Qué queda pendiente?
- ¿Qué intervención humana se necesita?

Registrar eventos significativos de forma estructurada.

Mantener el historial de cambios de estado, decisiones de enrutamiento, resultados, reintentos y publicaciones.

El mapa de fábrica debe reflejar la información real de estos registros.

Los logs técnicos y la auditoría de negocio deben tener responsabilidades diferenciadas.

No registrar tokens secretos ni contenido sensible innecesario.

---

# 20. EXPERIENCIA DE USUARIO Y DISEÑO VISUAL

El diseño debe ser moderno, minimalista, profesional y centrado en la operación.

Características:

- Fondo claro y superficies limpias.
- Jerarquía visual evidente.
- Tarjetas y estaciones reconocibles.
- Conectores que transmitan el flujo de producción.
- Iconografía coherente.
- Estados visibles.
- Tipografía legible.
- Indicadores compactos.
- Paneles contextuales.
- Animaciones discretas para trabajos en curso.
- Buen comportamiento en diferentes resoluciones.

El sistema debe transmitir una sensación de control y actividad, no de complejidad técnica.

El usuario no debería necesitar conocer los detalles internos de Spring Boot, PostgreSQL, las cuotas de los proveedores o las herramientas locales para entender el estado del sistema.

Los detalles técnicos estarán disponibles al profundizar en una estación o tarea.

## 20.1. Acciones contextuales

Cada elemento mostrará únicamente las acciones relevantes para su estado.

Ejemplos:

- Una tarea en espera puede mostrar el motivo y la opción de reprogramar.
- Una tarea bloqueada puede mostrar la dependencia pendiente.
- Una tarea fallida puede ofrecer reintento o diagnóstico.
- Una tarea en progreso puede ofrecer inspección o cancelación cuando sea seguro.
- Una publicación publicada puede mostrar sus métricas.
- Una promoción activa puede mostrar su calendario y progreso.

Las acciones destructivas o con consecuencias externas deben requerir confirmación cuando corresponda.

---

# 21. REGLAS DE CALIDAD Y MANTENIBILIDAD

- Código limpio y legible.
- Responsabilidades bien delimitadas.
- Principios SOLID cuando aporten valor.
- Arquitectura hexagonal.
- Inyección de dependencias.
- Validaciones explícitas.
- Tratamiento consistente de errores.
- Pruebas automatizadas.
- Migraciones versionadas.
- Configuración externa.
- Documentación de instalación.
- Logs estructurados.
- Contratos estables entre módulos.
- Versionado de esquemas de datos y contratos.
- Ausencia de dependencias innecesarias.
- No introducir abstracciones vacías.
- No crear funcionalidades simuladas y presentarlas como terminadas.

Las integraciones externas deben tener pruebas adecuadas y mecanismos de simulación para desarrollo local.

Las operaciones de publicación, programación y ejecución de trabajos deben probarse especialmente en escenarios de error y recuperación.

---

# 22. FASES DE DESARROLLO

No intentar construir todo de una vez.

Desarrollar el proyecto de manera incremental, dejando cada fase ejecutable y verificable.

## Fase 0: análisis y decisiones técnicas

- Revisar los requisitos.
- Verificar versiones compatibles.
- Elegir el frontend y la biblioteca de visualización.
- Definir la estrategia de instalación de PostgreSQL.
- Definir los contratos de ejecución.
- Identificar riesgos de integración.
- Crear un documento de decisiones arquitectónicas.

Entregable: especificación técnica inicial y plan de desarrollo.

## Fase 1: prototipo visual

Construir una SPA navegable que represente la fábrica digital.

Debe incluir:

- Vista global.
- Estaciones conectadas.
- Indicadores de estado.
- Zoom y desplazamiento.
- Zoom semántico.
- Detalle de estación.
- Detalle de tarea.
- Centro de control de promoción.
- Acciones contextuales.
- Datos de demostración claramente identificados.

El prototipo debe permitir validar la experiencia antes de completar todas las integraciones.

Los botones deben realizar las acciones de navegación y demostración anunciadas; no deben aparentar operaciones reales sobre servicios aún inexistentes.

## Fase 2: backend y persistencia

- Crear proyecto Spring Boot.
- Configurar PostgreSQL.
- Configurar Flyway.
- Implementar dominios iniciales.
- Crear API.
- Implementar estados y transiciones.
- Conectar el frontend con datos persistidos.

## Fase 3: motor de tareas y herramientas locales

- Cola persistente.
- Dependencias entre tareas.
- Gestión de errores.
- Reintentos.
- Registro de ejecución.
- Registro de herramientas locales.
- Integración inicial con ImageMagick y FFmpeg.
- Seguridad en la ejecución.

## Fase 4: Google Drive

- Autenticación.
- Lectura de recursos.
- Subida de resultados.
- Asociaciones entre archivos y promociones.
- Gestión de errores y permisos.

## Fase 5: orquestador de IA

- Abstracción de proveedores.
- Registro de modelos y agentes.
- Integración inicial con el mecanismo de Claude que esté oficialmente soportado.
- Contratos JSON.
- Gestión de límites.
- Presupuestos.
- Políticas de enrutamiento.
- Registro de consumo.

## Fase 6: producción editorial

- Investigación.
- Propuestas de contenido.
- Calendario.
- Generación de textos.
- Preparación audiovisual.
- Control de calidad.
- Memoria editorial.

## Fase 7: publicación

- Adaptador de Instagram.
- Adaptador de TikTok.
- Validaciones.
- Programación.
- Publicación autorizada.
- Alternativa manual cuando no exista acceso suficiente.
- Gestión de reintentos e idempotencia.

## Fase 8: métricas y autonomía

- Obtención de métricas.
- Análisis de resultados.
- Mejora de la estrategia.
- Investigación recurrente.
- Autonomía configurable.
- Alertas.
- Recuperación ante fallos.

## Fase 9: distribución

- Instalación en Windows y macOS.
- Configuración inicial.
- Comprobaciones de requisitos.
- Copias de seguridad.
- Restauración.
- Documentación.
- Pruebas de portabilidad.

---

# 23. CRITERIOS DE ACEPTACIÓN

SocialHub no se considerará funcional solo porque compile.

Debe demostrar progresivamente los siguientes resultados.

### Interfaz

- Se visualiza el flujo completo.
- El usuario puede navegar mediante zoom y desplazamiento.
- Los estados proceden de datos reales.
- Al acercarse aparecen detalles adicionales.
- Cada promoción dispone de su centro de control.
- Las acciones contextuales corresponden al estado real.

### Persistencia

- Los trabajos sobreviven al reinicio.
- Los estados se recuperan correctamente.
- Las transiciones inválidas se rechazan.
- Los errores y reintentos quedan registrados.

### Herramientas locales

- Se detectan herramientas instaladas.
- Se validan sus capacidades.
- Las operaciones utilizan parámetros seguros.
- Los resultados se comprueban.
- Los fallos se comunican correctamente.

### IA

- Los proveedores pueden configurarse.
- Los adaptadores están desacoplados.
- Se registran consumos y ejecuciones.
- Se respetan límites y presupuestos.
- Las tareas no disponibles quedan en espera.
- No se producen gastos no autorizados.
- Los resultados se validan antes de utilizarse.

### Publicación

- Una publicación no se marca como publicada sin confirmación suficiente.
- Los errores se registran.
- Los reintentos no generan duplicados.
- Se respetan las capacidades y restricciones de cada plataforma.

### Portabilidad

- Existe un procedimiento reproducible de instalación.
- PostgreSQL está configurado correctamente.
- Las migraciones se ejecutan.
- La aplicación puede iniciarse en los sistemas objetivo.
- La configuración y los datos están documentados.

---

# 24. INSTRUCCIONES PARA EL AGENTE DE DESARROLLO

Trabaja como un ingeniero responsable del producto completo, no como un generador de fragmentos de código inconexos.

Antes de modificar código:

1. Comprende la estructura existente.
2. Lee la documentación del proyecto.
3. Comprueba las decisiones arquitectónicas.
4. Identifica las dependencias afectadas.
5. Formula un plan breve.
6. Implementa cambios coherentes.
7. Ejecuta las pruebas disponibles.
8. Corrige los errores detectados.
9. Documenta los cambios.
10. Indica qué queda pendiente.

Si el repositorio ya contiene código, inspecciónalo antes de reemplazar archivos o estructuras.

No sobrescribas trabajo existente sin entenderlo.

No afirmes que una integración funciona si no se ha probado.

Si falta una credencial externa, desarrolla el adaptador y proporciona un modo de prueba local claramente identificado.

Si una API no permite una operación, documenta la limitación y ofrece una alternativa realista.

Si existe una decisión técnica importante sin resolver, presenta las opciones y recomienda una con sus ventajas, inconvenientes y consecuencias. No cambies unilateralmente las decisiones fundamentales del proyecto.

Prioriza soluciones simples y fiables.

No conviertas automáticamente el monolito modular en microservicios.

No introduzcas servicios de pago cuando una solución local gratuita cumpla los requisitos.

No asumas que todos los proveedores de IA ofrecen los mismos límites o capacidades.

No confíes en respuestas libres de la IA para ejecutar operaciones sensibles.

No sustituyas PostgreSQL por otra base de datos sin autorización.

No implementes datos ficticios en producción.

No marques tareas como completadas sin validar sus resultados.

---

# 25. OBJETIVO FINAL

El resultado debe ser una aplicación que el usuario pueda abrir y entender en pocos segundos.

Al entrar, verá una fábrica digital de contenido: todas las marcas, promociones y estaciones conectadas, con sus indicadores de estado.

Podrá acercarse a una estación para descubrir qué está haciendo, qué recursos utiliza y qué necesita.

Podrá entrar en una promoción para gestionar su calendario, contenido, recursos, automatizaciones y resultados.

Podrá observar cómo el sistema combina herramientas locales gratuitas y agentes de IA, respeta sus límites y registra las operaciones.

Y, progresivamente, podrá delegar en SocialHub la mayor parte del trabajo de investigación, creación, preparación, programación, publicación y análisis.

**La visión final es una fábrica autónoma de contenido, controlada desde una única interfaz visual, con intervención humana mínima, herramientas intercambiables, costes controlados y resultados verificables.**

Utiliza este documento como referencia maestra durante el desarrollo. Mantén una lista de requisitos implementados, pendientes, decisiones técnicas y limitaciones conocidas. Actualiza la documentación cuando cambie una decisión, sin perder los requisitos originales.

Comienza por analizar el proyecto existente, identificar los riesgos técnicos y proponer el primer incremento funcional. Si todavía no existe código, prepara la estructura inicial y construye primero un prototipo navegable de la fábrica digital antes de implementar todas las integraciones externas.
