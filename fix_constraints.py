import re

file_path = "src/main/resources/db/migration/V121.3__consolidado_activos_vehiculos_gastos.sql"

with open(file_path, "r") as f:
    content = f.read()

# We want to find blocks like:
# ALTER TABLE ONLY schema.table
#     ADD CONSTRAINT constraint_name something...;
# and replace with DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'constraint_name') THEN ALTER TABLE ... END IF; END $$;

pattern = re.compile(
    r"ALTER\s+TABLE\s+(?:ONLY\s+)?([a-zA-Z0-9_.]+)\s*?\n\s*ADD\s+CONSTRAINT\s+([a-zA-Z0-9_]+)\s+([^;]+);",
    re.MULTILINE | re.IGNORECASE
)

def repl(m):
    table = m.group(1)
    constraint = m.group(2)
    definition = m.group(3)
    
    # We'll use a DO block
    res = f"""DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '{constraint}') THEN
        ALTER TABLE {table} ADD CONSTRAINT {constraint} {definition};
    END IF;
END$$;"""
    return res

new_content = pattern.sub(repl, content)

with open(file_path, "w") as f:
    f.write(new_content)

print("Constraints fixed")
