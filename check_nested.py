import re

with open("src/main/resources/db/migration/V121.3__consolidado_activos_vehiculos_gastos.sql", "r") as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    if "DO $$" in line:
        depth += 1
        if depth > 1:
            print(f"Nested DO $$ at line {i+1}")
    if "END$$;" in line or "END $$;" in line:
        depth -= 1

print(f"Final depth: {depth}")
