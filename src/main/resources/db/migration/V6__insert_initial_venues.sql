-- Sedes compartidas de demostracion: aforo ficticio y direccion pendiente.
-- Conservar los codigos y las modificaciones de las sedes ya cargadas manualmente.
INSERT INTO venues (code, name, city, address, capacity, active) VALUES
    ('DEMO-SANTA-MARTA', 'Santa Marta', 'Santa Marta', 'Sede de demostracion; direccion pendiente', 1000, TRUE),
    ('DEMO-BARRANQUILLA', 'Barranquilla', 'Barranquilla', 'Sede de demostracion; direccion pendiente', 1000, TRUE),
    ('DEMO-BOGOTA', 'Bogota', 'Bogota', 'Sede de demostracion; direccion pendiente', 1000, TRUE),
    ('DEMO-MIAMI', 'Miami', 'Miami', 'Sede de demostracion; direccion pendiente', 1000, TRUE),
    ('DEMO-CIUDAD-DE-MEXICO', 'Ciudad de Mexico', 'Ciudad de Mexico', 'Sede de demostracion; direccion pendiente', 1000, TRUE),
    ('DEMO-MEDELLIN', 'Medellin', 'Medellin', 'Sede de demostracion; direccion pendiente', 1000, TRUE),
    ('DEMO-BUENOS-AIRES', 'Buenos Aires', 'Buenos Aires', 'Sede de demostracion; direccion pendiente', 1000, TRUE)
ON CONFLICT (code) DO NOTHING;
