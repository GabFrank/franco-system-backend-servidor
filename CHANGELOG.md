# Changelog

Todos los cambios notables de este proyecto serán documentados en este archivo.

El formato está basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/),
y este proyecto adhiere a [Semantic Versioning](https://semver.org/lang/es/).

## [3.0.8] - 2025-11-26

### 🚀 Nuevas Funcionalidades

- **Sistema SIFEN - Facturación Electrónica**: Implementación completa del sistema de facturación electrónica con SIFEN
- **Sistema de Notificaciones Push**: Sistema completo con RabbitMQ y Firebase Cloud Messaging
- **Sistema de Observaciones**: Observaciones para ventas y cajas con categorías y motivos
- **Replicación Lógica**: Sistema de replicación lógica entre servidor central y filiales
- **Gestión de Imágenes**: Sistema centralizado de gestión de imágenes con `ImagenMaster`
- **Notas de Recepción Agrupadas**: Sistema para agrupar y gestionar recepciones de mercaderías

### 🔧 Mejoras

- Mejoras en reimpresión de tickets de factura legal
- Soporte para facturas con moneda extranjera
- Filtros mejorados en búsqueda de productos y ventas
- Mejoras en control de inventario y productos vencidos
- Paginación mejorada en múltiples módulos
- Mejoras en sincronización entre servidor central y filiales

### 🐛 Correcciones

- Fix en búsqueda de productos
- Corrección en control de inventario
- Fix error unidad de medida en documentos electrónicos
- Corrección de valores por defecto en pedido item
- Fix en relaciones de producto-proveedor

### 📚 Documentación

- Documentación completa de implementación SIFEN
- Manual de implementación de facturación electrónica
- Documentación de mejoras en notificaciones FCM
- Guías de uso de scripts de backup y restore

### 🔄 Integraciones

- Integración completa con SIFEN para facturación electrónica
- Integración con Firebase Cloud Messaging para notificaciones push
- Integración con RabbitMQ para sistema de mensajería

**Ver [changelog detallado](CHANGELOG/v3.0.8.md) para más información.**

---

## Versiones Anteriores

Para ver el historial completo de cambios, consulta los archivos individuales en el directorio [CHANGELOG/](CHANGELOG/).

