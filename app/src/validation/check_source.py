from pathlib import Path
import re, sqlite3
from lxml import etree
root=Path(__file__).resolve().parents[1]/'main'
xmls=list(root.rglob('*.xml'))
for p in xmls: etree.parse(str(p))
print(f'{len(xmls)} XML files parsed')
ids=set()
for p in xmls: ids.update(re.findall(r'@\+id/(\w+)',p.read_text()))
kt='\n'.join(p.read_text() for p in root.rglob('*.kt'))
kt=re.sub(r'^\s*//.*$', '', kt, flags=re.M)
missing=set(re.findall(r'(?<!android\.)R\.id\.(\w+)',kt))-ids
# Some Android public IDs are referenced through android.R.id.
assert not missing, f'Missing resource IDs: {sorted(missing)}'
print('Kotlin R.id references exist')
assert not re.search(r'UsdCents|amountUsd|totalUsd|exchangeRateKhrPerUsd|utilityConvertedUsd|formatUsd',kt)
print('No legacy USD field or formatter references remain')
# Execute every DAO SQL query against a schema derived from entity columns.
db=sqlite3.connect(':memory:')
for p in (root/'java/com/example/roomkit/Date/Local/Entity').glob('*.kt'):
 s=p.read_text(); table=re.search(r'tableName\s*=\s*"(\w+)"',s)
 if not table: continue
 fields=re.findall(r'val\s+(\w+)\s*:\s*(Long|Int|Double|String|Boolean)(\?)?',s)
 columns=[]
 for name,typ,nullable in fields:
  sqltyp={'Long':'INTEGER','Int':'INTEGER','Double':'REAL','String':'TEXT','Boolean':'INTEGER'}[typ]
  columns.append(f'"{name}" {sqltyp}')
 db.execute('CREATE TABLE '+table[1]+' ('+', '.join(columns)+')')
count=0
for p in root.rglob('*Dao.kt'):
 for m in re.finditer(r'@Query\(\s*(?:"""([\s\S]*?)"""|"([^"\n]*)")\s*\)',re.sub(r'^\s*//.*$', '', p.read_text(), flags=re.M)):
  query=m[1] if m[1] is not None else m[2]
  params={name:1 for name in re.findall(r':(\w+)',query)}
  db.execute('EXPLAIN '+query,params)
  count+=1
print(f'{count} DAO queries validated against entity column names')
app=(root/'java/com/example/roomkit/AppDatabase.kt').read_text()
trigger=re.search(r'CREATE TRIGGER[\s\S]*?END',app)[0]
db.execute(trigger)
db.execute('INSERT INTO buildings(buildingId,name) VALUES(1,"Property")')
try:
 db.execute('INSERT INTO buildings(buildingId,name) VALUES(2,"Second")')
 raise AssertionError('Second property was accepted')
except sqlite3.IntegrityError: pass
db.execute('UPDATE buildings SET name="Updated" WHERE buildingId=1')
assert db.execute('SELECT name FROM buildings').fetchone()[0]=='Updated'
print('Single-property trigger blocks second insert and allows editing')
