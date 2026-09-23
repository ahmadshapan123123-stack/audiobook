import sqlite3, sys
sys.stdout.reconfigure(encoding='utf-8')
con = sqlite3.connect('evidence/fix1/A0b')
for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'"):
    print(r[0])