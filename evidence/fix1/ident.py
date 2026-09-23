import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
import sys
a = sys.argv[1]; b = sys.argv[2]
pre = sqlite3.connect('evidence/fix1/' + a)
post = sqlite3.connect('evidence/fix1/' + b)
tables = [r[0] for r in pre.execute(
    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' ORDER BY name")
]
all_ok = True
for t in tables:
    ncols = len(list(pre.execute('PRAGMA table_info(' + t + ')')))
    order = ','.join(str(i) for i in range(1, ncols + 1))
    x = list(pre.execute('SELECT * FROM ' + t + ' ORDER BY ' + order))
    y = list(post.execute('SELECT * FROM ' + t + ' ORDER BY ' + order))
    ok = (x == y)
    all_ok &= ok
    print(f'{t}: pre={len(x)} post={len(y)} identical={ok}')
print('ALL_IDENTICAL:', all_ok)