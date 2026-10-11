# Changelog

## Sin publicar
- Menas con PNG suelto: `pack/textures/ore/<id>.png` crea el modelo de cubo solo (sin .bbmodel). Caras distintas con `_side/_top/_bottom`, animación con `.mcmeta`.
- Selector de texturas en el menú de la mena (lista automática, con vista previa del bloque); ya no hay que escribir el id.
- Si la mena no tiene `model`, usa el PNG que se llame igual que ella.
- El pack se valida al construirlo: cualquier textura referenciada que falte se avisa como error (así se detecta antes de que salga el cuadro morado y negro).

## 1.0.0
- Menús GUI con paginación, búsqueda y entrada por chat o yunque.
- Crear, importar, editar y borrar dimensiones.
- Portales con hacha, relleno, costes, permisos y portal de vuelta automático.
- Menas custom (dureza, herramienta, drops, generación por vetas, respawn, modelo sobre el bloque).
- Mobs custom y jefes con fases; integración opcional con BetterModel 2.2.0.
- Generador de resource pack (.bbmodel → modelos Java) con CMD estables y servidor HTTP interno.
- Biblioteca de 1143 habilidades (13 disparadores, 18 formas, 50 efectos).
- Build con Gradle o Maven y workflows de GitHub (compilar, probar, publicar release).
