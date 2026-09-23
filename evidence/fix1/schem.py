import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect(sys.argv[1])
for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"):
    print(r[0])