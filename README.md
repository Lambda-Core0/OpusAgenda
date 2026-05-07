# OpusAgenda

OpusAgenda es una app Android de agenda y tareas con enfoque local, pensada para organizar pendientes en una interfaz estilo terminal retro. Guarda todo en el dispositivo y combina lista principal, recordatorios y widgets para tener la agenda siempre a mano

## Que hace

- Crea tareas y categorías para organizar listas jerárquicas con subtareas
- Permite buscar, filtrar entre vista completa y vista `Hoy`, fijar tareas y reordenarlas manualmente
- Asigna fecha limite, recordatorios programados y repetición automática por horas, días, semanas, meses o años
- Mantiene un resumen rápido de tareas abiertas, pendientes para hoy y total general

## Complementos incluidos

- `Widget de tareas`: muestra la lista en la pantalla de inicio, cambia entre `ALL` y `TODAY`(Botón de filtro encendido para 'TODAY', botón de filtro apagado para 'ALL'), permite marcar tareas como hechas y expandir o contraer categorías
- `Widget de accesos rápidos`: expone enlaces guardados para abrir servicios o paginas frecuentes desde el escritorio
- `Accesos rápidos editables`: incluye valores iniciales para Mail, Drive y Calendar, y permite agregar o modificar enlaces personalizados
- `Personalización visual`: tema inspirado en terminal con cambio de tipografía entre Inter, Geo, MedievalSharp, Octosquares y UnifrakturMaguntia
- `Recordatorios persistentes`: reprograma avisos tras reinicio del dispositivo o actualización de la app
- `Soporte multilenguaje`: recursos en español, alemán, italiano y ruso

## Base técnica

- Kotlin con arquitectura simple basada en `ViewModel`, `LiveData` y corrutinas
- Persistencia local con `Room` sobre SQLite
- UI clásica de Android con `ViewBinding`, `RecyclerView` y `Material Components`
- Requiere Android 7.0+ (`minSdk 24`)

## Estado del proyecto

Proyecto Android nativo sin backend externo. El foco actual esta en productividad personal, uso offline y acceso rápido desde widgets y notificaciones
