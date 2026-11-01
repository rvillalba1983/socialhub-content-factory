# Revisión de Raúl de la PR #1 (26 oct 2026)

Cambios pedidos sobre la PR #1, la base del backend:

1. **Persistencia:** Spring Data JPA, con Criteria para los filtros dinámicos cuando hagan falta.
2. **Auditoría:** usar una librería probada de uso general, en vez de clases propias.
3. **API first:** OpenAPI es la fuente de la verdad. A partir de ella se generan los modelos y las interfaces de los controladores.
4. **Diseño REST:** seguir las guías REST de Zalando.
5. **Código:** preferir records. Lombok está permitido para no escribir constructores.
6. **Arquitectura:** hexagonal y DDD.
7. **Seguridad:** simple, porque la app se ejecuta en ordenadores personales y no es visible desde fuera.
8. **Observabilidad:** especial atención a todas las operaciones externas a la aplicación, para poder ver y seguir cualquier problema.
9. **Librerías:** preguntar a Raúl cualquier librería que haga el código más legible o actual; él decide si se usa.
10. **Base de datos:** revisar desde ya los problemas de optimización.
11. **Simplicidad:** el sistema tiene que ser lo más simple posible dentro de la complejidad necesaria.

Los cambios se hacen sobre la PR #1. Al terminar, se avisa a Raúl para que revise.

Semana del 2 nov: Raúl no tiene acceso al ordenador. Se avanza solo con lo que no lo necesita.
