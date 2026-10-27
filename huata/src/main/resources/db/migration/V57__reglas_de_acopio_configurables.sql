-- V57: reglas de ubicacion del acopio, configurables (pedido del cliente, 2026-10-25).
--
-- Para una demostracion el cliente necesitaba acopiar sin estar dentro de la zona ni junto al
-- proveedor, pero quiere conservar las reglas para activarlas despues. Se guardan como parametros
-- de negocio y se cambian desde "Precios y calidad" (ReglasAcopioService), sin tocar codigo.
--
-- Nacen ENCENDIDAS ('SI'): es la regla vigente. Si alguien borra la fila o escribe otra cosa, el
-- sistema tambien aplica la regla estricta.

INSERT INTO public.parametros_negocio (clave, valor, descripcion, activo) VALUES
    ('ACOPIO_EXIGIR_GEOCERCA', 'SI',
     'Si el acopiador debe estar dentro del limite de su zona para empezar el recorrido y registrar leche.',
     true),
    ('ACOPIO_EXIGIR_DISTANCIA', 'SI',
     'Si la app impide registrar leche lejos del proveedor (mas de RECOJO_DISTANCIA_MAXIMA_METROS).',
     true)
ON CONFLICT (clave) DO NOTHING;
