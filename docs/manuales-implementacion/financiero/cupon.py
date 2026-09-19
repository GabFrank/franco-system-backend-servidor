#!/usr/bin/env python3
"""Genera un cupon FRCP1 valido para el monto que pidas.

El monto se escribe con punto decimal y SIN separador de miles:
  ./cupon.py 137500          -> 137.500 Gs.
  ./cupon.py 94.55 BRL       -> 94,55 R$
  ./cupon.py 137500 PYG -4320  -> mismo monto, fechado 3 dias atras (cupon "viejo")
"""
import sys, re, random, string
from datetime import datetime, timedelta

DEC = {'PYG': 0, 'BRL': 2, 'USD': 2}
SIM = {'PYG': 'Gs.', 'BRL': 'R$', 'USD': 'US$'}
PAT = re.compile(r"^FRCP1\*(?P<auth>[A-Z0-9]{0,20})\*(?P<bol>[A-Z0-9]{0,20})\*"
                 r"(?P<cur>PYG|BRL|USD)\*(?P<amt>[0-9]{1,15})\*"
                 r"(?P<ref>[A-Z0-9]{0,40})\*(?P<ts>[0-9]{12})$")

def rnd(n):
    return ''.join(random.choice(string.ascii_uppercase + string.digits) for _ in range(n))

monto = float(sys.argv[1]) if len(sys.argv) > 1 else 50000.0
cur   = (sys.argv[2] if len(sys.argv) > 2 else 'PYG').upper()
mins  = int(sys.argv[3]) if len(sys.argv) > 3 else 0

amt = int(round(monto * (10 ** DEC[cur])))
ts  = (datetime.now() + timedelta(minutes=mins)).strftime('%Y%m%d%H%M')
cupon = f"FRCP1*{rnd(6)}*{rnd(6)}*{cur}*{amt}*E{ts}{rnd(10)}*{ts}"

vista = f"{monto:,.{DEC[cur]}f}".replace(",", "·").replace(".", ",").replace("·", ".")
print(cupon)
print(f"  -> {vista} {SIM[cur]}   amt={amt}   ts={ts}   "
      f"{'patron OK' if PAT.match(cupon) else 'NO MATCHEA'}")
